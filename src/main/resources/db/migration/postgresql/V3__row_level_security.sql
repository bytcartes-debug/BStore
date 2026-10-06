-- =============================================================
-- V3: Row Level Security + melhorias de constraints
-- Supabase / PostgreSQL
-- =============================================================

-- 1. Garantir NOT NULL nos usuario_id de todas as tabelas de negócio
--    (V2 já actualizou NULLs para o primeiro utilizador)
ALTER TABLE categorias ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE produtos    ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE vendas      ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE devedores   ALTER COLUMN usuario_id SET NOT NULL;

-- 2. Foreign keys explícitas (anteriormente omitidas)
ALTER TABLE categorias
    ADD CONSTRAINT fk_categorias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
ALTER TABLE produtos
    ADD CONSTRAINT fk_produtos_usuario   FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
ALTER TABLE vendas
    ADD CONSTRAINT fk_vendas_usuario     FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
ALTER TABLE devedores
    ADD CONSTRAINT fk_devedores_usuario  FOREIGN KEY (usuario_id) REFERENCES usuarios(id);

-- 3. Índices de performance por utilizador
CREATE INDEX IF NOT EXISTS idx_categorias_usuario        ON categorias(usuario_id);
CREATE INDEX IF NOT EXISTS idx_produtos_usuario          ON produtos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_vendas_usuario            ON vendas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_vendas_usuario_data       ON vendas(usuario_id, data_venda DESC);
CREATE INDEX IF NOT EXISTS idx_devedores_usuario         ON devedores(usuario_id);
CREATE INDEX IF NOT EXISTS idx_sessoes_expira_activa     ON sessoes(expira_em) WHERE revogada_em IS NULL;

-- 4. Função auxiliar: lê o ID do utilizador actual injectado pela API Java
--    Retorna NULL se não houver contexto definido (ex: login, health-check).
CREATE OR REPLACE FUNCTION bstore_current_user_id()
RETURNS BIGINT AS $$
    SELECT NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

-- 5. Activar Row Level Security em todas as tabelas
ALTER TABLE usuarios  ENABLE ROW LEVEL SECURITY;
ALTER TABLE sessoes   ENABLE ROW LEVEL SECURITY;
ALTER TABLE categorias ENABLE ROW LEVEL SECURITY;
ALTER TABLE produtos   ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendas     ENABLE ROW LEVEL SECURITY;
ALTER TABLE devedores  ENABLE ROW LEVEL SECURITY;

-- 6. Políticas RLS
-- ---------------------------------------------------------------

-- USUARIOS: acesso irrestrito (a autorização é gerida ao nível da API)
-- O seeding inicial e gestão de utilizadores correm sem contexto de utilizador.
CREATE POLICY usuarios_policy ON usuarios
    FOR ALL USING (true) WITH CHECK (true);

-- SESSOES: SELECT livre (o token aleatório de 32 bytes é o mecanismo de auth)
--           mutações apenas pelo proprietário da sessão
CREATE POLICY sessoes_select ON sessoes
    FOR SELECT USING (true);

CREATE POLICY sessoes_insert ON sessoes
    FOR INSERT WITH CHECK (true);

CREATE POLICY sessoes_update ON sessoes
    FOR UPDATE USING (usuario_id = bstore_current_user_id());

CREATE POLICY sessoes_delete ON sessoes
    FOR DELETE USING (usuario_id = bstore_current_user_id());

-- CATEGORIAS: apenas o proprietário vê e modifica os seus dados
CREATE POLICY categorias_policy ON categorias
    FOR ALL
    USING      (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- PRODUTOS: apenas o proprietário
CREATE POLICY produtos_policy ON produtos
    FOR ALL
    USING      (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- VENDAS: apenas o proprietário
CREATE POLICY vendas_policy ON vendas
    FOR ALL
    USING      (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());

-- DEVEDORES: apenas o proprietário
CREATE POLICY devedores_policy ON devedores
    FOR ALL
    USING      (usuario_id = bstore_current_user_id())
    WITH CHECK (usuario_id = bstore_current_user_id());
