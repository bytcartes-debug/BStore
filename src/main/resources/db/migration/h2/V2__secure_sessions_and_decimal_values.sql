ALTER TABLE usuarios ALTER COLUMN password_hash VARCHAR(100);

ALTER TABLE produtos ALTER COLUMN preco DECIMAL(19, 2);
ALTER TABLE produtos ALTER COLUMN quantidade_stock DECIMAL(19, 3);
ALTER TABLE produtos ALTER COLUMN stock_minimo DECIMAL(19, 3);
ALTER TABLE vendas ALTER COLUMN quantidade DECIMAL(19, 3);
ALTER TABLE vendas ALTER COLUMN preco_unitario DECIMAL(19, 2);
ALTER TABLE vendas ALTER COLUMN total DECIMAL(19, 2);
ALTER TABLE devedores ALTER COLUMN divida DECIMAL(19, 2);

UPDATE categorias SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;
UPDATE produtos SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;
UPDATE vendas SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;
UPDATE devedores SET usuario_id = (SELECT MIN(id) FROM usuarios) WHERE usuario_id IS NULL;

CREATE TABLE sessoes (
    id VARCHAR(64) PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    criada_em TIMESTAMP NOT NULL,
    expira_em TIMESTAMP NOT NULL,
    revogada_em TIMESTAMP,
    CONSTRAINT fk_sessao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE INDEX idx_sessoes_usuario ON sessoes(usuario_id);
CREATE INDEX idx_sessoes_expira_em ON sessoes(expira_em);
CREATE UNIQUE INDEX uk_produto_codigo_barras_usuario ON produtos(codigo_barras, usuario_id);
