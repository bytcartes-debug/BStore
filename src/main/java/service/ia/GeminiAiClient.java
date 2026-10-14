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
import java.util.*;

public class GeminiAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiClient.class);
    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final List<String> FALLBACK_MODELS = List.of(
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-3-flash-preview",
            "gemini-3.6-flash"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiAiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
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

    private List<String> resolverListaModelos(String modeloDesejado) {
        if (modeloDesejado != null && !modeloDesejado.trim().isEmpty()) {
            return List.of(modeloDesejado.trim());
        }
        String preferido = EnvUtil.get("GEMINI_MODEL", DEFAULT_MODEL);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(preferido);
        set.addAll(FALLBACK_MODELS);
        return new ArrayList<>(set);
    }

    private AiResposta chamarApi(AiRequisicao req, byte[] imagemBytes, String mimeType, String apiKey, String modelo) {
        String key = resolverApiKey(apiKey);
        if (key == null) {
            return AiResposta.falha("Chave de API do Gemini não configurada na loja nem no servidor.");
        }

        List<String> modelosParaTentar = resolverListaModelos(modelo);
        String ultimoErro = null;

        for (int i = 0; i < modelosParaTentar.size(); i++) {
            String modelName = modelosParaTentar.get(i);
            String endpoint = BASE_URL + modelName + ":generateContent?key=" + key;

            try {
                ObjectNode root = objectMapper.createObjectNode();

                if (req.getSystemInstruction() != null && !req.getSystemInstruction().trim().isEmpty()) {
                    ObjectNode sysInstruction = root.putObject("system_instruction");
                    ArrayNode sysParts = sysInstruction.putArray("parts");
                    sysParts.addObject().put("text", req.getSystemInstruction().trim());
                }

                ArrayNode contents = root.putArray("contents");
                ObjectNode contentObj = contents.addObject();
                ArrayNode parts = contentObj.putArray("parts");

                parts.addObject().put("text", req.getPrompt());

                if (imagemBytes != null && imagemBytes.length > 0) {
                    String base64Data = Base64.getEncoder().encodeToString(imagemBytes);
                    String safeMime = (mimeType != null && !mimeType.trim().isEmpty()) ? mimeType.trim() : "image/jpeg";
                    ObjectNode inlineData = parts.addObject().putObject("inline_data");
                    inlineData.put("mime_type", safeMime);
                    inlineData.put("data", base64Data);
                }

                ObjectNode genConfig = root.putObject("generationConfig");
                genConfig.put("temperature", req.getTemperatura());
                int safeMaxTokens = Math.max(req.getMaxTokens(), 1024);
                genConfig.put("maxOutputTokens", safeMaxTokens);

                String jsonPayload = objectMapper.writeValueAsString(root);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(22))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
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

                    ultimoErro = "A IA respondeu mas não gerou texto válido.";
                } else {
                    int status = response.statusCode();
                    String msg = "HTTP " + status;
                    try {
                        JsonNode errNode = objectMapper.readTree(response.body());
                        if (errNode.has("error") && errNode.get("error").has("message")) {
                            msg = errNode.get("error").get("message").asText();
                        }
                    } catch (Exception ignored) {}

                    ultimoErro = msg;
                    log.warn("Gemini [{}] falhou (HTTP {}): {}. Verificando fallback...", modelName, status, msg);

                    // Se for 503 (sobrecarga temporária), 429 (limite) ou 404 (modelo descontinuado), tenta o próximo modelo
                    if ((status == 503 || status == 429 || status == 404) && i + 1 < modelosParaTentar.size()) {
                        continue;
                    }
                }
            } catch (Exception e) {
                log.warn("Exceção ao chamar Gemini com modelo {}: {}. Verificando fallback...", modelName, e.getMessage());
                ultimoErro = "Erro de conexão com o serviço de IA: " + e.getMessage();
                if (i + 1 < modelosParaTentar.size()) {
                    continue;
                }
            }
        }

        return AiResposta.falha(ultimoErro != null ? ultimoErro : "Falha na comunicação com a IA.");
    }
}
