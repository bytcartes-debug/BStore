-- =============================================================
-- V14: Ativação por defeito da IA e chave central do servidor
-- =============================================================

ALTER TABLE definicoes_loja ALTER COLUMN ia_activa SET DEFAULT TRUE;
UPDATE definicoes_loja SET ia_activa = TRUE WHERE ia_activa = FALSE;
