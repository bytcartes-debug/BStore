-- V10: Controlar ultimo_numero_venda de forma segura para concorrencia
ALTER TABLE definicoes_loja ADD COLUMN ultimo_numero_venda BIGINT DEFAULT 0 NOT NULL;

UPDATE definicoes_loja d
SET ultimo_numero_venda = COALESCE((
    SELECT MAX(v.numero)
    FROM vendas v
    WHERE v.usuario_id = d.usuario_id
), 0);
