-- ================================================================
-- BStore — Configuração Avançada de Segurança no Supabase
-- ================================================================
-- Executar manualmente no SQL Editor do Supabase (uma única vez).
-- Requer permissões de superuser (o editor do Supabase tem por defeito).
--
-- Este script cria um role dedicado "bstore_app" com permissões
-- mínimas. Ao ligar o backend Java com este role (em vez do postgres
-- superuser), as políticas RLS são aplicadas efectivamente.
-- ================================================================

-- 1. Criar o role da aplicação (sem permissões de superuser)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'bstore_app') THEN
        -- ALTERAR esta password antes de executar!
        CREATE ROLE bstore_app WITH LOGIN PASSWORD 'ALTERAR_ESTA_PASSWORD_AQUI';
    END IF;
END
$$;

-- 2. Permissões no schema public
GRANT USAGE ON SCHEMA public TO bstore_app;

-- 3. Permissões nas tabelas existentes
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO bstore_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bstore_app;

-- 4. Permissão na função auxiliar de RLS
GRANT EXECUTE ON FUNCTION bstore_current_user_id() TO bstore_app;

-- 5. Permissões automáticas para tabelas futuras (criadas por migrações Flyway)
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO bstore_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO bstore_app;

-- ================================================================
-- APÓS EXECUTAR ESTE SCRIPT:
--
-- Actualizar as variáveis de ambiente no Render.com / .env:
--
--   DATABASE_URL=postgresql://bstore_app:[NOVA_PASSWORD]@db.[REF].supabase.co:5432/postgres
--   DB_USER=bstore_app
--   DB_PASSWORD=[NOVA_PASSWORD]
--
-- IMPORTANTE: Manter a URL com o postgres superuser para Flyway
-- (migrações de schema). Configuração avançada opcional:
--
--   MIGRATION_DATABASE_URL=postgresql://postgres:[PASS]@db.[REF].supabase.co:5432/postgres
--
-- ================================================================

-- VERIFICAÇÃO: Testar se o role foi criado correctamente
SELECT rolname, rolcanlogin, rolsuper
FROM pg_roles
WHERE rolname = 'bstore_app';
