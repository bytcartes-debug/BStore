ALTER TABLE usuarios ALTER COLUMN password_hash TYPE VARCHAR(100);

ALTER TABLE produtos ALTER COLUMN preco TYPE NUMERIC(19, 2) USING ROUND(preco::numeric, 2);
ALTER TABLE produtos ALTER COLUMN quantidade_stock TYPE NUMERIC(19, 3) USING ROUND(quantidade_stock::numeric, 3);
ALTER TABLE produtos ALTER COLUMN stock_minimo TYPE NUMERIC(19, 3) USING ROUND(stock_minimo::numeric, 3);
ALTER TABLE vendas ALTER COLUMN quantidade TYPE NUMERIC(19, 3) USING ROUND(quantidade::numeric, 3);
ALTER TABLE vendas ALTER COLUMN preco_unitario TYPE NUMERIC(19, 2) USING ROUND(preco_unitario::numeric, 2);
ALTER TABLE vendas ALTER COLUMN total TYPE NUMERIC(19, 2) USING ROUND(total::numeric, 2);
ALTER TABLE devedores ALTER COLUMN divida TYPE NUMERIC(19, 2) USING ROUND(divida::numeric, 2);

UPDATE categorias SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;
UPDATE produtos SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;
UPDATE vendas SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;
UPDATE devedores SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;

CREATE TABLE sessoes (
    id VARCHAR(64) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    criada_em TIMESTAMP NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    revogada_em TIMESTAMP
);

CREATE INDEX idx_sessoes_usuario ON sessoes(usuario_id);
CREATE INDEX idx_sessoes_expira_em ON sessoes(expira_em);
CREATE UNIQUE INDEX uk_produto_codigo_barras_usuario ON produtos(codigo_barras, usuario_id);
