-- =============================================================
-- V8: Devoluções parciais, controlo de sessões de caixa e reposição
-- Supabase / PostgreSQL
-- =============================================================

-- 1. Reposição: stock_maximo em produtos
ALTER TABLE produtos ADD COLUMN stock_maximo NUMERIC(19, 3);

-- 2. Definições da loja (controlo de caixa desligado por defeito)
CREATE TABLE definicoes_loja (
    usuario_id BIGINT PRIMARY KEY,
    controlo_caixa BOOLEAN DEFAULT FALSE NOT NULL,
    nome_loja VARCHAR(100),
    atualizado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_definicoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

ALTER TABLE definicoes_loja ENABLE ROW LEVEL SECURITY;
CREATE POLICY definicoes_loja_policy ON definicoes_loja
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

INSERT INTO definicoes_loja (usuario_id, controlo_caixa, nome_loja)
SELECT u.id, false, u.nome FROM usuarios u;

-- 3. Sessões de caixa
CREATE TABLE sessoes_caixa (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    aberta_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fechada_em TIMESTAMP,
    valor_inicial NUMERIC(19, 2) DEFAULT 0 NOT NULL,
    valor_contado NUMERIC(19, 2),
    valor_esperado NUMERIC(19, 2),
    diferenca NUMERIC(19, 2),
    nota_abertura VARCHAR(255),
    nota_fecho VARCHAR(255),
    aberta_por BIGINT,
    fechada_por BIGINT,
    estado VARCHAR(15) DEFAULT 'ABERTA' NOT NULL,
    CONSTRAINT chk_sessao_estado CHECK (estado IN ('ABERTA', 'FECHADA')),
    CONSTRAINT fk_sessoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

ALTER TABLE sessoes_caixa ENABLE ROW LEVEL SECURITY;
CREATE POLICY sessoes_caixa_policy ON sessoes_caixa
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

CREATE INDEX idx_sessoes_caixa_usuario ON sessoes_caixa(usuario_id, aberta_em DESC);
CREATE UNIQUE INDEX uk_sessao_aberta_usuario ON sessoes_caixa(usuario_id) WHERE estado = 'ABERTA';

-- 4. Associação de vendas com sessão de caixa
ALTER TABLE vendas ADD COLUMN sessao_id BIGINT;
ALTER TABLE vendas ADD CONSTRAINT fk_vendas_sessao FOREIGN KEY (sessao_id) REFERENCES sessoes_caixa(id) ON DELETE SET NULL;
CREATE INDEX idx_vendas_sessao ON vendas(sessao_id);
ALTER TABLE vendas ALTER COLUMN estado TYPE VARCHAR(30);
ALTER TABLE vendas DROP CONSTRAINT IF EXISTS chk_venda_estado;
ALTER TABLE vendas ADD CONSTRAINT chk_venda_estado CHECK (estado IN ('CONCLUIDA', 'ANULADA', 'DEVOLVIDA', 'PARCIALMENTE_DEVOLVIDA'));

-- 5. Devoluções parciais por linha
CREATE TABLE devolucoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    venda_id BIGINT NOT NULL,
    uuid_cliente VARCHAR(100),
    criada_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total NUMERIC(19, 2) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    criado_por BIGINT,
    CONSTRAINT fk_devolucoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_devolucoes_venda FOREIGN KEY (venda_id) REFERENCES vendas(id) ON DELETE CASCADE
);

ALTER TABLE devolucoes ENABLE ROW LEVEL SECURITY;
CREATE POLICY devolucoes_policy ON devolucoes
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

CREATE INDEX idx_devolucoes_usuario ON devolucoes(usuario_id, criada_em DESC);
CREATE INDEX idx_devolucoes_venda ON devolucoes(venda_id);
CREATE UNIQUE INDEX uk_devolucoes_uuid ON devolucoes(usuario_id, uuid_cliente) WHERE uuid_cliente IS NOT NULL;

CREATE TABLE devolucao_itens (
    id BIGSERIAL PRIMARY KEY,
    devolucao_id BIGINT NOT NULL,
    item_venda_id BIGINT NOT NULL,
    quantidade NUMERIC(19, 3) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_devolucao_itens_devolucao FOREIGN KEY (devolucao_id) REFERENCES devolucoes(id) ON DELETE CASCADE,
    CONSTRAINT fk_devolucao_itens_item FOREIGN KEY (item_venda_id) REFERENCES venda_itens(id) ON DELETE CASCADE
);

ALTER TABLE devolucao_itens ENABLE ROW LEVEL SECURITY;
CREATE POLICY devolucao_itens_policy ON devolucao_itens
    FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM devolucoes d
            WHERE d.id = devolucao_itens.devolucao_id
              AND d.usuario_id = bstore_current_user_id()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM devolucoes d
            WHERE d.id = devolucao_itens.devolucao_id
              AND d.usuario_id = bstore_current_user_id()
        )
    );

CREATE INDEX idx_devolucao_itens_devolucao ON devolucao_itens(devolucao_id);
CREATE INDEX idx_devolucao_itens_item ON devolucao_itens(item_venda_id);
