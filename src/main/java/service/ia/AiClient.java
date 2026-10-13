package service.ia;

public interface AiClient {

    AiResposta gerarTexto(AiRequisicao req, String apiKey, String modelo);

    AiResposta analisarImagem(AiRequisicao req, byte[] imagemBytes, String mimeType, String apiKey, String modelo);
}
