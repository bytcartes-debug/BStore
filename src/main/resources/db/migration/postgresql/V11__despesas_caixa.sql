-- =============================================================
-- V11: Despesas e saídas de caixa (Sangrias de sessão)
-- Supabase / PostgreSQL
-- =============================================================

CREATE TABLE despesas_caixa (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    sessao_id BIGINT,
    valor NUMERIC(19, 2) NOT NULL,
    categoria VARCHAR(50) DEFAULT 'OUTRO' NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    criada_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    criado_por BIGINT,
    CONSTRAINT fk_despesas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_despesas_sessao FOREIGN KEY (sessao_id) REFERENCES sessoes_caixa(id) ON DELETE SET NULL
);

ALTER TABLE despesas_caixa ENABLE ROW LEVEL SECURITY;
CREATE POLICY despesas_caixa_policy ON despesas_caixa
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

CREATE INDEX idx_despesas_caixa_usuario_sessao ON despesas_caixa(usuario_id, sessao_id);
CREATE INDEX idx_despesas_caixa_data ON despesas_caixa(usuario_id, criada_em DESC);
