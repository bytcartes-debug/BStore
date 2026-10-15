-- =============================================================
-- V15: Definir Claude como provedor de IA padrão no sistema
-- =============================================================

ALTER TABLE definicoes_loja ALTER COLUMN ia_provedor SET DEFAULT 'claude';
UPDATE definicoes_loja SET ia_provedor = 'claude';
