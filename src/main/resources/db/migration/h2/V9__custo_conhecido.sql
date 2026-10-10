-- =============================================================
-- V9: Custo conhecido para isolar vendas migradas sem custo do lucro
-- H2 Database
-- =============================================================

ALTER TABLE venda_itens ADD COLUMN custo_conhecido BOOLEAN DEFAULT TRUE NOT NULL;

-- Linhas migradas na V6 que ficaram com custo_unitario = 0 por falta de custo original
UPDATE venda_itens
SET custo_conhecido = FALSE
WHERE custo_unitario = 0;
