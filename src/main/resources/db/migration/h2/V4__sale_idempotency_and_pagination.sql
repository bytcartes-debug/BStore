CREATE TABLE pedidos_venda (
    usuario_id BIGINT NOT NULL,
    chave VARCHAR(80) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    resposta TEXT NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT pk_pedidos_venda PRIMARY KEY (usuario_id, chave),
    CONSTRAINT fk_pedidos_venda_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE INDEX idx_produtos_usuario_nome_id ON produtos(usuario_id, nome, id);
CREATE INDEX idx_produtos_usuario_categoria ON produtos(usuario_id, categoria_id);
CREATE INDEX idx_vendas_usuario_data_id ON vendas(usuario_id, data_venda DESC, id DESC);
