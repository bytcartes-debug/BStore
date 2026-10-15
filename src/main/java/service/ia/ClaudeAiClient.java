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

public class ClaudeAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiClient.class);
    public static final String DEFAULT_MODEL = "claude-sonnet-5";
    public static final String DEFAULT_API_KEY = "sk-myapi_HhWHzOPfyU_gmfuo2uHxvVzXPOR8crAxAcEwjFlDy-E4H_5_B3A3oQ";
    public static final String DEFAULT_ENDPOINT = "https://claude-n-codex.com:8443/v1/messages";

    private static final List<String> FALLBACK_MODELS = List.of(
            "claude-sonnet-5",
            "claude-sonnet-4-6",
            "sonnet-5",
            "sonnet"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClaudeAiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    private boolean isChaveValidaClaude(String k) {
        if (k == null || k.isBlank()) return false;
        String clean = k.trim();
        if (clean.startsWith("AQ.") || clean.startsWith("AIza")) return false;
        return clean.startsWith("sk-") || clean.startsWith("claude-") || clean.length() > 25;
    }

    private String resolverApiKey(String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            String k = apiKey.trim();
            if (isChaveValidaClaude(k)) {
                return k;
            }
            log.warn("Chave informada não é compatível com Claude (detectada chave do Gemini ou inválida). Usando chave padrão.");
        }
        String envKey = EnvUtil.get("CLAUDE_API_KEY");
        if (envKey == null || envKey.trim().isEmpty()) {
            envKey = EnvUtil.get("ANTHROPIC_API_KEY");
        }
        if (envKey != null && !envKey.trim().isEmpty()) {
            String k = envKey.trim();
            if (isChaveValidaClaude(k)) {
                return k;
            }
        }
        return DEFAULT_API_KEY;
    }

    private String resolverEndpoint() {
        String base = EnvUtil.get("CLAUDE_BASE_URL", DEFAULT_ENDPOINT);
        if (base == null || base.trim().isEmpty()) {
            return DEFAULT_ENDPOINT;
        }
        base = base.trim();
        if (base.endsWith("/v1/messages")) {
            return base;
        }
        if (base.endsWith("/v1/")) {
            return base + "messages";
        }
        if (base.endsWith("/v1")) {
            return base + "/messages";
        }
        if (base.endsWith("/")) {
            return base + "v1/messages";
        }
        return base + "/v1/messages";
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
        String preferido = EnvUtil.get("CLAUDE_MODEL", DEFAULT_MODEL);
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(preferido);
        set.addAll(FALLBACK_MODELS);
        return new ArrayList<>(set);
    }

    private AiResposta chamarApi(AiRequisicao req, byte[] imagemBytes, String mimeType, String apiKey, String modelo) {
        String key = resolverApiKey(apiKey);
        if (key == null || key.isBlank()) {
            return AiResposta.falha("Chave de API do Claude não configurada.");
        }

        String endpoint = resolverEndpoint();
        List<String> modelosParaTentar = resolverListaModelos(modelo);
        String ultimoErro = null;

        for (int i = 0; i < modelosParaTentar.size(); i++) {
            String modelName = modelosParaTentar.get(i);

            try {
                ObjectNode root = objectMapper.createObjectNode();
                root.put("model", modelName);
                int safeMaxTokens = Math.max(req.getMaxTokens(), 1024);
                root.put("max_tokens", safeMaxTokens);

                if (req.getSystemInstruction() != null && !req.getSystemInstruction().trim().isEmpty()) {
                    root.put("system", req.getSystemInstruction().trim());
                }

                ArrayNode messages = root.putArray("messages");
                ObjectNode userMsg = messages.addObject();
                userMsg.put("role", "user");

                if (imagemBytes != null && imagemBytes.length > 0) {
                    ArrayNode contentArray = userMsg.putArray("content");

                    String safeMime = "image/jpeg";
                    if (mimeType != null) {
                        String m = mimeType.trim().toLowerCase();
                        if (m.contains("png")) safeMime = "image/png";
                        else if (m.contains("webp")) safeMime = "image/webp";
                        else if (m.contains("gif")) safeMime = "image/gif";
                        else if (m.contains("jpeg") || m.contains("jpg")) safeMime = "image/jpeg";
                    }
                    String base64Data = Base64.getEncoder().encodeToString(imagemBytes);

                    ObjectNode imgBlock = contentArray.addObject();
                    imgBlock.put("type", "image");
                    ObjectNode source = imgBlock.putObject("source");
                    source.put("type", "base64");
                    source.put("media_type", safeMime);
                    source.put("data", base64Data);

                    ObjectNode textBlock = contentArray.addObject();
                    textBlock.put("type", "text");
                    textBlock.put("text", req.getPrompt());
                } else {
                    userMsg.put("content", req.getPrompt());
                }

                String jsonPayload = objectMapper.writeValueAsString(root);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode resNode = objectMapper.readTree(response.body());
                    int promptTokens = 0;
                    int candidatesTokens = 0;

                    if (resNode.has("usage")) {
                        JsonNode usage = resNode.get("usage");
                        if (usage.has("input_tokens")) promptTokens = usage.get("input_tokens").asInt();
                        if (usage.has("output_tokens")) candidatesTokens = usage.get("output_tokens").asInt();
                    }

                    if (resNode.has("content")) {
                        JsonNode contentNode = resNode.get("content");
                        if (contentNode.isArray()) {
                            StringBuilder sb = new StringBuilder();
                            for (JsonNode block : contentNode) {
                                if (block.has("type") && "text".equalsIgnoreCase(block.get("type").asText()) && block.has("text")) {
                                    sb.append(block.get("text").asText());
                                }
                            }
                            if (sb.length() > 0) {
                                return AiResposta.sucesso(sb.toString().trim(), promptTokens, candidatesTokens);
                            }
                        } else if (contentNode.isTextual()) {
                            return AiResposta.sucesso(contentNode.asText().trim(), promptTokens, candidatesTokens);
                        }
                    }

                    if (resNode.has("choices") && resNode.get("choices").isArray() && resNode.get("choices").size() > 0) {
                        JsonNode firstChoice = resNode.get("choices").get(0);
                        if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                            return AiResposta.sucesso(firstChoice.get("message").get("content").asText().trim(), promptTokens, candidatesTokens);
                        }
                    }

                    ultimoErro = "O Claude respondeu mas não devolveu bloco de texto válido.";
                } else {
                    int status = response.statusCode();
                    String msg = "HTTP " + status;
                    try {
                        JsonNode errNode = objectMapper.readTree(response.body());
                        if (errNode.has("error")) {
                            JsonNode err = errNode.get("error");
                            if (err.has("message")) {
                                msg = err.get("message").asText();
                            } else if (err.isTextual()) {
                                msg = err.asText();
                            }
                        } else if (errNode.has("message")) {
                            msg = errNode.get("message").asText();
                        }
                    } catch (Exception ignored) {}

                    ultimoErro = msg;
                    log.warn("Claude [{}] falhou (HTTP {}): {}. Verificando fallback...", modelName, status, msg);

                    if ((status == 503 || status == 529 || status == 429 || status == 404 || status == 400 || status == 401) && i + 1 < modelosParaTentar.size()) {
                        continue;
                    }
                }
            } catch (Exception e) {
                log.warn("Exceção ao chamar Claude com modelo {}: {}. Verificando fallback...", modelName, e.getMessage());
                ultimoErro = "Erro de conexão com o Claude: " + e.getMessage();
                if (i + 1 < modelosParaTentar.size()) {
                    continue;
                }
            }
        }

        return AiResposta.falha(ultimoErro != null ? ultimoErro : "Falha na comunicação com o Claude.");
    }
}
