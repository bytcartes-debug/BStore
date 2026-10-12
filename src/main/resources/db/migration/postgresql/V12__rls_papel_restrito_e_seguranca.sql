-- =============================================================
-- V12: Row Level Security Real, Papel bstore_app e Isolamento
-- Supabase / PostgreSQL
-- =============================================================

-- 1. Criação do papel restrito bstore_app (sem password na migração)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bstore_app') THEN
        CREATE ROLE bstore_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
    END IF;
END
$$;

-- 2. Conceder permissões mínimas no schema public
GRANT USAGE ON SCHEMA public TO bstore_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO bstore_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO bstore_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO bstore_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO bstore_app;

-- 3. Activar FORCE ROW LEVEL SECURITY em todas as 17 tabelas de negócio
ALTER TABLE usuarios FORCE ROW LEVEL SECURITY;
ALTER TABLE sessoes FORCE ROW LEVEL SECURITY;
ALTER TABLE categorias FORCE ROW LEVEL SECURITY;
ALTER TABLE produtos FORCE ROW LEVEL SECURITY;
ALTER TABLE devedores FORCE ROW LEVEL SECURITY;
ALTER TABLE pedidos_venda FORCE ROW LEVEL SECURITY;
ALTER TABLE movimentos_stock FORCE ROW LEVEL SECURITY;
ALTER TABLE venda_itens FORCE ROW LEVEL SECURITY;
ALTER TABLE vendas FORCE ROW LEVEL SECURITY;
ALTER TABLE pagamentos_venda FORCE ROW LEVEL SECURITY;
ALTER TABLE pagamentos_divida FORCE ROW LEVEL SECURITY;
ALTER TABLE metodos_pagamento FORCE ROW LEVEL SECURITY;
ALTER TABLE definicoes_loja FORCE ROW LEVEL SECURITY;
ALTER TABLE sessoes_caixa FORCE ROW LEVEL SECURITY;
ALTER TABLE devolucoes FORCE ROW LEVEL SECURITY;
ALTER TABLE devolucao_itens FORCE ROW LEVEL SECURITY;
ALTER TABLE despesas_caixa FORCE ROW LEVEL SECURITY;

-- 4. Função auxiliar de contexto: devolve estritamente NULL se não configurado
CREATE OR REPLACE FUNCTION bstore_current_user_id()
RETURNS BIGINT AS $$
    SELECT NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;
$$ LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public;

-- 4.1 Função auxiliar de verificação de superuser (sem recursão de RLS)
CREATE OR REPLACE FUNCTION bstore_is_superuser()
RETURNS BOOLEAN AS $$
    SELECT EXISTS (
        SELECT 1 FROM usuarios
        WHERE id = NULLIF(current_setting('app.current_user_id', true), '')::BIGINT
          AND role = 'superuser'
    );
$$ LANGUAGE SQL STABLE SECURITY DEFINER SET search_path = public;

-- 5. Blindar a tabela usuarios (fechar o acesso global irrestrito da V3)
DROP POLICY IF EXISTS usuarios_policy ON usuarios;
DROP POLICY IF EXISTS usuarios_tenant_policy ON usuarios;
CREATE POLICY usuarios_tenant_policy ON usuarios
    FOR ALL
    USING (id = bstore_current_user_id() OR bstore_is_superuser())
    WITH CHECK (id = bstore_current_user_id() OR bstore_is_superuser());

-- 6. Blindar a tabela sessoes (fechar os selects irrestritos da V3)
DROP POLICY IF EXISTS sessoes_select ON sessoes;
DROP POLICY IF EXISTS sessoes_insert ON sessoes;
DROP POLICY IF EXISTS sessoes_update ON sessoes;
DROP POLICY IF EXISTS sessoes_delete ON sessoes;
DROP POLICY IF EXISTS sessoes_tenant_policy ON sessoes;
CREATE POLICY sessoes_tenant_policy ON sessoes
    FOR ALL
    USING (usuario_id = bstore_current_user_id() OR bstore_is_superuser())
    WITH CHECK (usuario_id = bstore_current_user_id() OR bstore_is_superuser());

-- 7. Funções SECURITY DEFINER mínimas para fluxos não autenticados (login e validação de token)

-- 7.1 Busca de utilizador no momento do login (sem expor a tabela completa)
CREATE OR REPLACE FUNCTION bstore_buscar_usuario_login(p_email TEXT)
RETURNS TABLE (
    id BIGINT,
    nome VARCHAR(100),
    email VARCHAR(150),
    password_hash VARCHAR(100),
    role VARCHAR(20),
    data_expiracao DATE,
    dias_acesso INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT u.id, u.nome, u.email, u.password_hash, u.role, u.data_expiracao, u.dias_acesso
    FROM usuarios u
    WHERE LOWER(u.email) = LOWER(TRIM(p_email))
    LIMIT 1;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION bstore_buscar_usuario_login(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bstore_buscar_usuario_login(TEXT) TO bstore_app;

-- 7.2 Validação de sessão ativa por token de cookie
CREATE OR REPLACE FUNCTION bstore_buscar_sessao_activa(p_token VARCHAR(64))
RETURNS TABLE (
    sessao_id VARCHAR(64),
    criada_em TIMESTAMP WITH TIME ZONE,
    expira_em TIMESTAMP WITH TIME ZONE,
    revogada_em TIMESTAMP WITH TIME ZONE,
    usuario_id BIGINT,
    usuario_nome VARCHAR(100),
    usuario_email VARCHAR(150),
    usuario_role VARCHAR(20),
    usuario_expiracao DATE,
    usuario_dias INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT s.id, s.criada_em, s.expira_em, s.revogada_em,
           u.id, u.nome, u.email, u.role, u.data_expiracao, u.dias_acesso
    FROM sessoes s
    JOIN usuarios u ON u.id = s.usuario_id
    WHERE s.id = p_token
      AND s.revogada_em IS NULL
      AND s.expira_em > CURRENT_TIMESTAMP
    LIMIT 1;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION bstore_buscar_sessao_activa(VARCHAR(64)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bstore_buscar_sessao_activa(VARCHAR(64)) TO bstore_app;

-- 7.3 Emissão de sessão no login com autenticação bem-sucedida
CREATE OR REPLACE FUNCTION bstore_criar_sessao(
    p_token VARCHAR(64),
    p_usuario_id BIGINT,
    p_criada_em TIMESTAMP WITH TIME ZONE,
    p_expira_em TIMESTAMP WITH TIME ZONE
) RETURNS VOID AS $$
BEGIN
    INSERT INTO sessoes (id, usuario_id, criada_em, expira_em)
    VALUES (p_token, p_usuario_id, p_criada_em, p_expira_em);
END;
$$ LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION bstore_criar_sessao(VARCHAR(64), BIGINT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bstore_criar_sessao(VARCHAR(64), BIGINT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE) TO bstore_app;

-- 7.4 Revogação de sessão no logout
CREATE OR REPLACE FUNCTION bstore_revogar_sessao(p_token VARCHAR(64))
RETURNS VOID AS $$
BEGIN
    UPDATE sessoes
    SET revogada_em = CURRENT_TIMESTAMP
    WHERE id = p_token;
END;
$$ LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION bstore_revogar_sessao(VARCHAR(64)) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bstore_revogar_sessao(VARCHAR(64)) TO bstore_app;

-- 7.5 Registo seguro de novo utilizador e inicialização da sua loja
CREATE OR REPLACE FUNCTION bstore_criar_usuario(
    p_nome VARCHAR(100),
    p_email VARCHAR(150),
    p_password_hash VARCHAR(100),
    p_role VARCHAR(20),
    p_data_expiracao DATE,
    p_dias_acesso INTEGER
) RETURNS BIGINT AS $$
DECLARE
    v_id BIGINT;
BEGIN
    INSERT INTO usuarios (nome, email, password_hash, role, data_expiracao, dias_acesso)
    VALUES (p_nome, LOWER(TRIM(p_email)), p_password_hash, p_role, p_data_expiracao, p_dias_acesso)
    RETURNING id INTO v_id;

    INSERT INTO definicoes_loja (usuario_id, modo_escuro, nome_loja)
    VALUES (v_id, false, p_nome);

    RETURN v_id;
END;
$$ LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION bstore_criar_usuario(VARCHAR(100), VARCHAR(150), VARCHAR(100), VARCHAR(20), DATE, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bstore_criar_usuario(VARCHAR(100), VARCHAR(150), VARCHAR(100), VARCHAR(20), DATE, INTEGER) TO bstore_app;

