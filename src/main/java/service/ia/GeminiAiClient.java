package service.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.EnvUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class GeminiAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);
    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiAiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private String resolverApiKey(String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey.trim();
        }
        String envKey = EnvUtil.get("GEMINI_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        return null;
    }

    @Override
    public AiResposta gerarTexto(AiRequisicao req, String apiKey, String modelo) {
        return chamarApi(req, null, null, apiKey, modelo);
    }

    @Override
    public AiResposta analisarImagem(AiRequisicao req, byte[] imagemBytes, String mimeType, String apiKey, String modelo) {
        return chamarApi(req, imagemBytes, mimeType, apiKey, modelo);
    }

    private AiResposta chamarApi(AiRequisicao req, byte[] imagemBytes, String mimeType, String apiKey, String modelo) {
        String key = resolverApiKey(apiKey);
        if (key == null) {
            return AiResposta.falha("Chave de API do Gemini não configurada na loja nem no servidor.");
        }

        String fallbackModel = EnvUtil.get("GEMINI_MODEL", DEFAULT_MODEL);
        String modelName = (modelo != null && !modelo.trim().isEmpty()) ? modelo.trim() : fallbackModel;
        String endpoint = BASE_URL + modelName + ":generateContent?key=" + key;

        try {
            ObjectNode root = objectMapper.createObjectNode();

            // Instrução de sistema se presente
            if (req.getSystemInstruction() != null && !req.getSystemInstruction().trim().isEmpty()) {
                ObjectNode sysInstruction = root.putObject("system_instruction");
                ArrayNode sysParts = sysInstruction.putArray("parts");
                sysParts.addObject().put("text", req.getSystemInstruction().trim());
            }

            // Conteúdo principal
            ArrayNode contents = root.putArray("contents");
            ObjectNode contentObj = contents.addObject();
            ArrayNode parts = contentObj.putArray("parts");

            // Texto do prompt
            parts.addObject().put("text", req.getPrompt());

            // Imagem se houver
            if (imagemBytes != null && imagemBytes.length > 0) {
                String base64Data = Base64.getEncoder().encodeToString(imagemBytes);
                String safeMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "image/jpeg";
                ObjectNode inlineData = parts.addObject().putObject("inline_data");
                inlineData.put("mime_type", safeMime);
                inlineData.put("data", base64Data);
            }

            // Configuração de geração
            ObjectNode genConfig = root.putObject("generationConfig");
            genConfig.put("temperature", req.getTemperatura());
            genConfig.put("maxOutputTokens", req.getMaxTokens());

            String jsonPayload = objectMapper.writeValueAsString(root);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(18))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Falha na chamada ao Gemini [HTTP {}]: {}", response.statusCode(), response.body());
                try {
                    JsonNode errNode = objectMapper.readTree(response.body());
                    if (errNode.has("error") && errNode.get("error").has("message")) {
                        return AiResposta.falha(errNode.get("error").get("message").asText());
                    }
                } catch (Exception ignored) {}
                return AiResposta.falha("Erro na comunicação com a IA (HTTP " + response.statusCode() + ").");
            }

            JsonNode resNode = objectMapper.readTree(response.body());
            int promptTokens = 0;
            int candidatesTokens = 0;

            if (resNode.has("usageMetadata")) {
                JsonNode usage = resNode.get("usageMetadata");
                if (usage.has("promptTokenCount")) promptTokens = usage.get("promptTokenCount").asInt();
                if (usage.has("candidatesTokenCount")) candidatesTokens = usage.get("candidatesTokenCount").asInt();
            }

            if (resNode.has("candidates") && resNode.get("candidates").isArray() && resNode.get("candidates").size() > 0) {
                JsonNode firstCandidate = resNode.get("candidates").get(0);
                if (firstCandidate.has("content") && firstCandidate.get("content").has("parts")) {
                    ArrayNode resParts = (ArrayNode) firstCandidate.get("content").get("parts");
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode p : resParts) {
                        if (p.has("text")) {
                            sb.append(p.get("text").asText());
                        }
                    }
                    return AiResposta.sucesso(sb.toString().trim(), promptTokens, candidatesTokens);
                }
            }

            return AiResposta.falha("A IA não gerou uma resposta válida para esta solicitação.");

        } catch (Exception e) {
            log.error("Exceção ao contactar Gemini API", e);
            return AiResposta.falha("Erro interno ao processar pedido de IA: " + e.getMessage());
        }
    }
}
