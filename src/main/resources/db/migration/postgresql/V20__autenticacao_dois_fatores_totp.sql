-- Migração V20: Autenticação de Dois Fatores (2FA / TOTP - RFC 6238)
-- Permite maior segurança para contas administrativas com tokens temporários

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS totp_ativo BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN usuarios.totp_secret IS 'Chave secreta Base32 para geração de tokens TOTP RFC 6238';
COMMENT ON COLUMN usuarios.totp_ativo IS 'Indica se a autenticação de dois fatores está ativa para o utilizador';
