-- =============================================================
-- V6: Venda como documento, pagamentos divididos e devedores
-- Supabase / PostgreSQL
-- =============================================================

-- 1. Renomear tabela vendas para venda_itens e adicionar novas colunas
ALTER TABLE vendas RENAME TO venda_itens;
ALTER TABLE venda_itens ADD COLUMN custo_unitario NUMERIC(19, 2) DEFAULT 0 NOT NULL;
ALTER TABLE venda_itens ADD COLUMN venda_id BIGINT;

UPDATE venda_itens vi
SET custo_unitario = COALESCE((SELECT p.custo FROM produtos p WHERE p.id = vi.produto_id), 0);

-- Ajustar política RLS da tabela renomeada
DROP POLICY IF EXISTS vendas_policy ON venda_itens;
CREATE POLICY venda_itens_policy ON venda_itens
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- 2. Criar a nova tabela vendas (cabeçalho da venda)
CREATE TABLE vendas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    numero BIGINT NOT NULL,
    criada_em TIMESTAMP NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    total_custo NUMERIC(19, 2) DEFAULT 0 NOT NULL,
    estado VARCHAR(15) NOT NULL,
    cliente_id BIGINT,
    observacao VARCHAR(255),
    anulada_em TIMESTAMP,
    anulada_por BIGINT,
    motivo_anulacao VARCHAR(255),
    criado_por BIGINT,
    origem_item_id BIGINT,
    CONSTRAINT chk_venda_estado CHECK (estado IN ('CONCLUIDA', 'ANULADA')),
    CONSTRAINT uk_venda_usuario_numero UNIQUE (usuario_id, numero),
    CONSTRAINT fk_vendas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

ALTER TABLE vendas ENABLE ROW LEVEL SECURITY;
CREATE POLICY vendas_policy ON vendas
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- 3. Criar a tabela pagamentos_venda
CREATE TABLE pagamentos_venda (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    venda_id BIGINT NOT NULL,
    metodo VARCHAR(15) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    troco NUMERIC(19, 2) DEFAULT 0 NOT NULL,
    CONSTRAINT chk_pagamento_venda_metodo CHECK (metodo IN ('DINHEIRO', 'MPESA', 'EMOLA', 'MKESH', 'CARTAO', 'FIADO')),
    CONSTRAINT fk_pagamentos_venda_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_pagamentos_venda_venda FOREIGN KEY (venda_id) REFERENCES vendas(id) ON DELETE CASCADE
);

ALTER TABLE pagamentos_venda ENABLE ROW LEVEL SECURITY;
CREATE POLICY pagamentos_venda_policy ON pagamentos_venda
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- 4. Backfill: cada linha antiga de venda_itens vira um cabeçalho e um pagamento DINHEIRO
INSERT INTO vendas (usuario_id, numero, criada_em, total, total_custo, estado, observacao, criado_por, origem_item_id)
SELECT 
    vi.usuario_id,
    ROW_NUMBER() OVER (PARTITION BY vi.usuario_id ORDER BY vi.data_venda ASC, vi.id ASC) AS numero,
    CAST(CONCAT(CAST(vi.data_venda AS VARCHAR), ' 12:00:00') AS TIMESTAMP) AS criada_em,
    vi.total,
    ROUND((vi.quantidade * vi.custo_unitario)::numeric, 2) AS total_custo,
    'CONCLUIDA' AS estado,
    vi.observacao,
    vi.usuario_id AS criado_por,
    vi.id AS origem_item_id
FROM venda_itens vi;

UPDATE venda_itens vi
SET venda_id = (SELECT v.id FROM vendas v WHERE v.origem_item_id = vi.id);

INSERT INTO pagamentos_venda (usuario_id, venda_id, metodo, valor, troco)
SELECT v.usuario_id, v.id, 'DINHEIRO', v.total, 0
FROM vendas v
WHERE v.origem_item_id IS NOT NULL;

ALTER TABLE vendas DROP COLUMN origem_item_id;

ALTER TABLE venda_itens ALTER COLUMN venda_id SET NOT NULL;
ALTER TABLE venda_itens ADD CONSTRAINT fk_venda_itens_venda FOREIGN KEY (venda_id) REFERENCES vendas(id) ON DELETE CASCADE;

-- 5. Atualizar devedores e criar pagamentos_divida
ALTER TABLE devedores ADD COLUMN venda_id BIGINT;
ALTER TABLE devedores ADD CONSTRAINT fk_devedores_venda FOREIGN KEY (venda_id) REFERENCES vendas(id) ON DELETE SET NULL;

CREATE TABLE pagamentos_divida (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    devedor_id BIGINT NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    metodo VARCHAR(15) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    observacao VARCHAR(255),
    CONSTRAINT chk_pagamento_divida_metodo CHECK (metodo IN ('DINHEIRO', 'MPESA', 'EMOLA', 'MKESH', 'CARTAO')),
    CONSTRAINT fk_pagamentos_divida_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_pagamentos_divida_devedor FOREIGN KEY (devedor_id) REFERENCES devedores(id) ON DELETE CASCADE
);

ALTER TABLE pagamentos_divida ENABLE ROW LEVEL SECURITY;
CREATE POLICY pagamentos_divida_policy ON pagamentos_divida
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- 6. Índices
CREATE INDEX idx_vendas_usuario_criada_em ON vendas(usuario_id, criada_em DESC);
CREATE INDEX idx_venda_itens_venda ON venda_itens(venda_id);
CREATE INDEX idx_venda_itens_produto ON venda_itens(produto_id);
CREATE INDEX idx_pagamentos_venda_venda ON pagamentos_venda(venda_id);
CREATE INDEX idx_pagamentos_venda_usuario ON pagamentos_venda(usuario_id);
CREATE INDEX idx_pagamentos_divida_devedor ON pagamentos_divida(devedor_id);
CREATE INDEX idx_pagamentos_divida_usuario ON pagamentos_divida(usuario_id);
