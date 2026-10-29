package service.notificacao;

public interface NotificacaoWhatsAppClient {

    enum StatusSessao {
        CONECTADO("Sessão ativa e conectada ao WhatsApp"),
        DESLIGADO("Sessão do WhatsApp desligada. Volte a ler o QR no OpenWA"),
        ERRO_AUTENTICACAO("Chave de API inválida ou sem permissão"),
        SERVIDOR_INACESSIVEL("Servidor OpenWA inacessível"),
        NAO_CONFIGURADO("WhatsApp não configurado");

        private final String descricao;

        StatusSessao(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }

        public boolean isConectado() {
            return this == CONECTADO;
        }
    }

    class ResultadoEnvio {
        private final boolean sucesso;
        private final String mensagem;
        private final StatusSessao status;

        public ResultadoEnvio(boolean sucesso, String mensagem, StatusSessao status) {
            this.sucesso = sucesso;
            this.mensagem = mensagem;
            this.status = status;
        }

        public boolean isSucesso() {
            return sucesso;
        }

        public String getMensagem() {
            return mensagem;
        }

        public StatusSessao getStatus() {
            return status;
        }
    }

    boolean enviarTexto(String telefone, String mensagem);

    ResultadoEnvio enviarTextoDetalhado(String telefone, String mensagem);

    StatusSessao verificarStatusSessao();

    boolean isAtivo();

    String getTelefoneDono();
}
