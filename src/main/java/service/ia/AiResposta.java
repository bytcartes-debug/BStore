package service.ia;

public class AiResposta {

    private final String texto;
    private final int tokensEntrada;
    private final int tokensSaida;
    private final boolean sucesso;
    private final String erro;

    public AiResposta(String texto, int tokensEntrada, int tokensSaida) {
        this.texto = texto;
        this.tokensEntrada = tokensEntrada;
        this.tokensSaida = tokensSaida;
        this.sucesso = true;
        this.erro = null;
    }

    public AiResposta(String erro) {
        this.texto = null;
        this.tokensEntrada = 0;
        this.tokensSaida = 0;
        this.sucesso = false;
        this.erro = erro;
    }

    public static AiResposta sucesso(String texto, int tokensEntrada, int tokensSaida) {
        return new AiResposta(texto, tokensEntrada, tokensSaida);
    }

    public static AiResposta falha(String erro) {
        return new AiResposta(erro);
    }

    public String getTexto() { return texto; }
    public int getTokensEntrada() { return tokensEntrada; }
    public int getTokensSaida() { return tokensSaida; }
    public boolean isSucesso() { return sucesso; }
    public String getErro() { return erro; }
}
