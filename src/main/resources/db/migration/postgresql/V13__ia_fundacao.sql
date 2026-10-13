-- =============================================================
-- V13: Fundação Segura para IA (Gemini / Anthropic / OpenAI)
-- Auditoria de consumo, limites e isolamento por loja (RLS)
-- =============================================================

-- 1. Colunas de configuração de IA em definicoes_loja
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS ia_activa BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS ia_provedor VARCHAR(32) DEFAULT 'gemini' NOT NULL;
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS ia_api_key VARCHAR(255);
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS ia_limite_diario INTEGER DEFAULT 50 NOT NULL;
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS ia_limite_mensal INTEGER DEFAULT 500 NOT NULL;

-- 2. Tabela de auditoria de consumo e rate limiting de IA
CREATE TABLE IF NOT EXISTS ia_uso (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    operacao VARCHAR(64) NOT NULL,
    tokens_entrada INTEGER DEFAULT 0 NOT NULL,
    tokens_saida INTEGER DEFAULT 0 NOT NULL,
    custo_estimado NUMERIC(10, 4) DEFAULT 0 NOT NULL,
    sucesso BOOLEAN DEFAULT TRUE NOT NULL,
    mensagem_erro TEXT,
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_ia_uso_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

-- 3. Activar FORCE ROW LEVEL SECURITY na nova tabela
ALTER TABLE ia_uso ENABLE ROW LEVEL SECURITY;
ALTER TABLE ia_uso FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ia_uso_policy ON ia_uso;
CREATE POLICY ia_uso_policy ON ia_uso
    FOR ALL
    USING (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- 4. Índices para contagem rápida de limites diários e mensais
CREATE INDEX IF NOT EXISTS idx_ia_uso_usuario_criado_em ON ia_uso(usuario_id, criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_ia_uso_operacao ON ia_uso(usuario_id, operacao);

-- 5. Permissões de acesso para o papel restrito bstore_app
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bstore_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON ia_uso TO bstore_app;
        GRANT USAGE, SELECT ON SEQUENCE ia_uso_id_seq TO bstore_app;
    END IF;
END
$$;
