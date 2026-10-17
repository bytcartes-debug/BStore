-- =============================================================
-- V18: Notificações Automáticas via WhatsApp (Z-API) para o Dono
-- Envio de relatórios em tempo real:
-- 1. Venda registada
-- 2. Adição de novo produto
-- 3. Baixo estoque
-- 4. Abertura de caixa
-- 5. Fecho de caixa
-- =============================================================

ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS whatsapp_notificacoes_activas BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS whatsapp_telefone_dono VARCHAR(50);
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS zapi_instance_id VARCHAR(100);
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS zapi_token VARCHAR(100);
ALTER TABLE definicoes_loja ADD COLUMN IF NOT EXISTS zapi_client_token VARCHAR(100);
