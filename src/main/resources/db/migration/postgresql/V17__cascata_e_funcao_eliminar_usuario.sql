-- =============================================================
-- V17: Cascata e função segura para super-administradores eliminarem utilizadores
-- =============================================================

-- 1. Assegurar ON DELETE CASCADE nas tabelas que ficaram sem cascata na V3
ALTER TABLE categorias DROP CONSTRAINT IF EXISTS fk_categorias_usuario;
ALTER TABLE categorias ADD CONSTRAINT fk_categorias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

ALTER TABLE produtos DROP CONSTRAINT IF EXISTS fk_produtos_usuario;
ALTER TABLE produtos ADD CONSTRAINT fk_produtos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

ALTER TABLE devedores DROP CONSTRAINT IF EXISTS fk_devedores_usuario;
ALTER TABLE devedores ADD CONSTRAINT fk_devedores_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 2. Função SECURITY DEFINER para exclusão atómica e limpa de utilizador e todos os seus dados
CREATE OR REPLACE FUNCTION bstore_eliminar_usuario(p_id BIGINT)
RETURNS VOID AS $$
BEGIN
    -- 2.1 Desvincular caixas abertos ou fechados por este utilizador
    UPDATE sessoes_caixa SET aberta_por = NULL WHERE aberta_por = p_id;
    UPDATE sessoes_caixa SET fechada_por = NULL WHERE fechada_por = p_id;

    -- 2.2 Limpar dados dependentes
    DELETE FROM ia_uso WHERE usuario_id = p_id;
    DELETE FROM despesas_caixa WHERE usuario_id = p_id;
    DELETE FROM devolucao_itens WHERE devolucao_id IN (SELECT id FROM devolucoes WHERE usuario_id = p_id);
    DELETE FROM devolucoes WHERE usuario_id = p_id;
    DELETE FROM pagamentos_divida WHERE usuario_id = p_id;
    DELETE FROM devedores WHERE usuario_id = p_id;
    DELETE FROM pagamentos_venda WHERE usuario_id = p_id;
    DELETE FROM venda_itens WHERE venda_id IN (SELECT id FROM vendas WHERE usuario_id = p_id);
    DELETE FROM vendas WHERE usuario_id = p_id;
    DELETE FROM sessoes_caixa WHERE usuario_id = p_id;
    DELETE FROM pedidos_venda WHERE usuario_id = p_id;
    DELETE FROM movimentos_stock WHERE usuario_id = p_id;
    DELETE FROM produtos WHERE usuario_id = p_id;
    DELETE FROM categorias WHERE usuario_id = p_id;
    DELETE FROM metodos_pagamento WHERE usuario_id = p_id;
    DELETE FROM definicoes_loja WHERE usuario_id = p_id;
    DELETE FROM sessoes WHERE usuario_id = p_id;

    -- 2.3 Excluir o utilizador
    DELETE FROM usuarios WHERE id = p_id;
END;
$$ LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = public;

REVOKE ALL ON FUNCTION bstore_eliminar_usuario(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bstore_eliminar_usuario(BIGINT) TO bstore_app;
