-- =============================================================
-- V19: Auditoria, Rastreabilidade e Logs de Ações Críticas
-- Não-repúdio, rastreamento de terminais e conformidade operacional
-- =============================================================

CREATE TABLE IF NOT EXISTS registro_auditoria (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    usuario_nome VARCHAR(100),
    tipo_entidade VARCHAR(50) NOT NULL,
    acao VARCHAR(50) NOT NULL,
    entidade_id BIGINT,
    detalhes TEXT,
    ip_origem VARCHAR(45),
    criado_em TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auditoria_usuario ON registro_auditoria(usuario_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_data ON registro_auditoria(criado_em DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_tipo_acao ON registro_auditoria(tipo_entidade, acao);

-- 2. Activar Row Level Security
ALTER TABLE registro_auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE registro_auditoria FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS auditoria_tenant_policy ON registro_auditoria;
CREATE POLICY auditoria_tenant_policy ON registro_auditoria
    FOR ALL
    USING (usuario_id = bstore_current_user_id() OR bstore_is_superuser())
    WITH CHECK (usuario_id = bstore_current_user_id() OR bstore_is_superuser());

-- 3. Permissões para o papel bstore_app
GRANT SELECT, INSERT ON registro_auditoria TO bstore_app;
GRANT USAGE, SELECT ON SEQUENCE registro_auditoria_id_seq TO bstore_app;
