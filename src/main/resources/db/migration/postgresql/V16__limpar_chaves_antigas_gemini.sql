-- =============================================================
-- V16: Limpar chaves antigas do Gemini na tabela definicoes_loja
-- Permite que todas as lojas usem a chave padrão global do Claude
-- =============================================================

UPDATE definicoes_loja 
SET ia_api_key = NULL 
WHERE ia_api_key LIKE 'AQ.%' 
   OR ia_api_key LIKE 'AIza%'
   OR (ia_api_key IS NOT NULL AND ia_api_key NOT LIKE 'sk-%' AND ia_api_key NOT LIKE 'claude-%');
