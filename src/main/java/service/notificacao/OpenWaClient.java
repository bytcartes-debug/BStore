package service.notificacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.EnvUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class OpenWaClient implements NotificacaoWhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(OpenWaClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean ativo;
    private final String baseUrl;
    private final String apiKey;
    private final String sessionId;
    private final String telefoneDono;
    private final int timeoutSeconds;
    private final int maxEnviosPorDia;

    private final HttpClient httpClient;
    private final ExecutorService executor;

    // Controle de ritmo e deduplicação
    private long ultimoEnvioMillis = 0L;
    private final Object envioLock = new Object();
    private LocalDate dataContadorDiario = LocalDate.now();
    private final AtomicInteger contadorDiario = new AtomicInteger(0);
    private final Map<String, Long> historicoMensagensRecentes = new ConcurrentHashMap<>();

    // Cache de status da sessão (60 segundos)
    private volatile StatusSessao statusSessaoCache = null;
    private volatile long statusSessaoCacheTimestamp = 0L;
    private static final long CACHE_STATUS_TTL_MILLIS = 60_000L;

    public OpenWaClient() {
        this(null, null, null, null, null, null, null, null);
    }

    public OpenWaClient(Boolean ativoParam, String baseUrlParam, String apiKeyParam,
                        String sessionIdParam, String telefoneDonoParam,
                        Integer timeoutSecondsParam, Integer maxEnviosPorDiaParam,
                        HttpClient customHttpClient) {

        // 1. Resolução de ativação e retrocompatibilidade
        boolean ativoDetectado = false;
        String envAtivo = EnvUtil.get("WHATSAPP_NOTIFICACOES_ACTIVAS");
        if (envAtivo == null || envAtivo.isBlank()) {
            String legacyAtivo = EnvUtil.get("ZAPI_NOTIFICACOES_ACTIVAS");
            if (legacyAtivo != null && !legacyAtivo.isBlank()) {
                log.warn("[OpenWA] ZAPI_NOTIFICACOES_ACTIVAS está obsoleto. Utilize WHATSAPP_NOTIFICACOES_ACTIVAS no .env.");
                envAtivo = legacyAtivo;
            }
        }
        if (ativoParam != null) {
            ativoDetectado = ativoParam;
        } else if (envAtivo != null && !envAtivo.isBlank()) {
            ativoDetectado = Boolean.parseBoolean(envAtivo.trim());
        }

        // 2. Telefone do dono
        String tel = telefoneDonoParam;
        if (tel == null || tel.isBlank()) {
            tel = EnvUtil.get("WHATSAPP_DONO_TELEFONE");
            if (tel == null || tel.isBlank()) {
                String legacyTel = EnvUtil.get("ZAPI_DONO_TELEFONE");
                if (legacyTel != null && !legacyTel.isBlank()) {
                    log.warn("[OpenWA] ZAPI_DONO_TELEFONE está obsoleto. Utilize WHATSAPP_DONO_TELEFONE no .env.");
                    tel = legacyTel;
                }
            }
        }
        this.telefoneDono = (tel != null) ? tel.trim() : "";

        // 3. Base URL
        String url = (baseUrlParam != null && !baseUrlParam.isBlank())
                ? baseUrlParam
                : EnvUtil.get("OPENWA_BASE_URL", "http://localhost:2785");
        url = sanitizarBaseUrl(url);

        // 4. Credenciais
        this.apiKey = (apiKeyParam != null && !apiKeyParam.isBlank())
                ? apiKeyParam.trim()
                : EnvUtil.get("OPENWA_API_KEY", "").trim();

        this.sessionId = (sessionIdParam != null && !sessionIdParam.isBlank())
                ? sessionIdParam.trim()
                : EnvUtil.get("OPENWA_SESSION_ID", "").trim();

        // 5. Timeouts e limites
        int tSec = (timeoutSecondsParam != null && timeoutSecondsParam > 0)
                ? timeoutSecondsParam
                : parseInteiro(EnvUtil.get("OPENWA_TIMEOUT_SECONDS"), 5);
        this.timeoutSeconds = Math.max(1, Math.min(60, tSec));

        int maxDia = (maxEnviosPorDiaParam != null && maxEnviosPorDiaParam > 0)
                ? maxEnviosPorDiaParam
                : parseInteiro(EnvUtil.get("WHATSAPP_MAX_POR_DIA"), 100);
        this.maxEnviosPorDia = Math.max(1, maxDia);

        // 6. Validação e modo de ativação tolerante
        String erroValidacao = validarConfiguracao(url, this.apiKey, this.sessionId, this.telefoneDono);
        if (ativoDetectado && erroValidacao != null) {
            log.warn("[OpenWA] Notificações desativadas automaticamente: {}", erroValidacao);
            this.ativo = false;
        } else {
            this.ativo = ativoDetectado;
        }
        this.baseUrl = url;

        // 7. HTTP Client e Executor assíncrono com fila limitada
        this.httpClient = (customHttpClient != null) ? customHttpClient : HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();

        // Fila limitada de 100 tarefas com descarte das mais antigas para nunca travar a aplicação
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(100);
        this.executor = new ThreadPoolExecutor(
                1, 2,
                60L, TimeUnit.SECONDS,
                queue,
                r -> {
                    Thread t = new Thread(r, "openwa-whatsapp-notifier");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    private static String sanitizarBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "http://127.0.0.1:2785";
        }
        String limpo = url.trim();
        while (limpo.endsWith("/")) {
            limpo = limpo.substring(0, limpo.length() - 1);
        }
        if (limpo.startsWith("http://localhost:")) {
            limpo = "http://127.0.0.1:" + limpo.substring("http://localhost:".length());
        }
        return limpo;
    }

    private static int parseInteiro(String str, int padrao) {
        if (str == null || str.isBlank()) return padrao;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private String validarConfiguracao(String url, String key, String sess, String tel) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return "OPENWA_BASE_URL deve começar com http:// ou https://";
        }

        String env = EnvUtil.get("APP_ENV", EnvUtil.get("ENV", "development"));
        boolean isProducao = "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env);
        boolean isLocal = url.contains("localhost") || url.contains("127.0.0.1");
        if (isProducao && !isLocal && !url.startsWith("https://")) {
            return "Em ambiente de produção exige-se HTTPS para OPENWA_BASE_URL (exceto localhost)";
        }

        if (key == null || key.isBlank()) {
            return "OPENWA_API_KEY não definida";
        }
        if (sess == null || sess.isBlank()) {
            return "OPENWA_SESSION_ID não definida";
        }
        String telNormalizado = normalizarTelefone(tel);
        if (telNormalizado == null) {
            return "Telefone do dono ausente ou com menos de 8 dígitos";
        }
        return null;
    }

    public static String normalizarTelefone(String telefone) {
        if (telefone == null) return null;
        String digitos = telefone.replaceAll("[^0-9]", "");
        if (digitos.length() < 8) {
            return null;
        }
        return digitos;
    }

    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) return "***";
        String digitos = telefone.replaceAll("[^0-9]", "");
        if (digitos.length() <= 3) return "***" + digitos;
        return "***" + digitos.substring(digitos.length() - 3);
    }

    @Override
    public boolean isAtivo() {
        return ativo;
    }

    @Override
    public String getTelefoneDono() {
        return telefoneDono;
    }

    @Override
    public boolean enviarTexto(String telefone, String mensagem) {
        if (!ativo) {
            return false;
        }
        ResultadoEnvio res = enviarTextoDetalhado(telefone, mensagem);
        return res.isSucesso();
    }

    @Override
    public ResultadoEnvio enviarTextoDetalhado(String telefone, String mensagem) {
        if (baseUrl == null || apiKey == null || apiKey.isBlank() || sessionId == null || sessionId.isBlank()) {
            log.warn("[OpenWA] Tentativa de envio com configuração incompleta.");
            return new ResultadoEnvio(false, "Configuração do OpenWA incompleta.", StatusSessao.NAO_CONFIGURADO);
        }

        String telLimpo = normalizarTelefone(telefone);
        if (telLimpo == null) {
            log.warn("[OpenWA] Número de telefone inválido ou menor que 8 dígitos.");
            return new ResultadoEnvio(false, "Número de telefone inválido.", StatusSessao.NAO_CONFIGURADO);
        }

        if (mensagem == null || mensagem.isBlank()) {
            log.warn("[OpenWA] Mensagem vazia não enviada.");
            return new ResultadoEnvio(false, "Mensagem vazia.", StatusSessao.NAO_CONFIGURADO);
        }

        // 1. Verificação de limite diário
        verificarViradaDoDia();
        if (contadorDiario.get() >= maxEnviosPorDia) {
            log.warn("[OpenWA] Limite diário de {} mensagens atingido. Envio suprimido.", maxEnviosPorDia);
            return new ResultadoEnvio(false, "Limite diário de mensagens do WhatsApp atingido.", StatusSessao.CONECTADO);
        }

        // 2. Deduplicação em menos de 60 segundos
        String chaveDeduplicacao = telLimpo + ":" + mensagem.hashCode();
        long agora = System.currentTimeMillis();
        Long ultimoTempoMsg = historicoMensagensRecentes.get(chaveDeduplicacao);
        if (ultimoTempoMsg != null && (agora - ultimoTempoMsg) < 60_000L) {
            log.info("[OpenWA] Mensagem idêntica para destino {} enviada há menos de 60s. Descartada por deduplicação.", mascararTelefone(telLimpo));
            return new ResultadoEnvio(true, "Mensagem agrupada por deduplicação recente.", StatusSessao.CONECTADO);
        }

        // 3. Limite de ritmo: no máximo 1 mensagem por segundo
        synchronized (envioLock) {
            long tempoDesdeUltimo = System.currentTimeMillis() - ultimoEnvioMillis;
            if (tempoDesdeUltimo < 1000L) {
                try {
                    Thread.sleep(1000L - tempoDesdeUltimo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ResultadoEnvio(false, "Envio interrompido.", StatusSessao.SERVIDOR_INACESSIVEL);
                }
            }
            ultimoEnvioMillis = System.currentTimeMillis();
        }

        // 4. Execução do pedido HTTP (com retry único para 5xx/falha de rede)
        String endpoint = baseUrl + "/api/sessions/" + sessionId + "/messages/send-text";
        String chatId = telLimpo + "@c.us";
        String corpoJson;
        try {
            corpoJson = MAPPER.writeValueAsString(Map.of(
                    "chatId", chatId,
                    "text", mensagem
            ));
        } catch (Exception e) {
            log.error("[OpenWA] Erro ao serializar payload de mensagem: {}", e.getMessage());
            return new ResultadoEnvio(false, "Erro ao preparar mensagem.", StatusSessao.NAO_CONFIGURADO);
        }

        ResultadoEnvio res = executarPedidoEnvio(endpoint, corpoJson, telLimpo, 0);
        if (res.isSucesso()) {
            contadorDiario.incrementAndGet();
            historicoMensagensRecentes.put(chaveDeduplicacao, System.currentTimeMillis());
            limparHistoricoAntigo();
        }
        return res;
    }

    private ResultadoEnvio executarPedidoEnvio(String endpoint, String corpoJson, String telLimpo, int tentativa) {
        String telMasc = mascararTelefone(telLimpo);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(this.timeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-API-Key", this.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();

            if (code >= 200 && code < 300) {
                log.info("[OpenWA] Notificação enviada com sucesso para {} (Status {})", telMasc, code);
                return new ResultadoEnvio(true, "Mensagem enviada com sucesso para o WhatsApp!", StatusSessao.CONECTADO);
            }

            if (code == 401 || code == 403) {
                log.error("[OpenWA] Falha de autenticação/permissão (HTTP {}). Chave de API inválida ou sem permissão para sessão/chat.", code);
                return new ResultadoEnvio(false, "Chave de API do OpenWA inválida ou sem permissão.", StatusSessao.ERRO_AUTENTICACAO);
            }

            if (code == 404) {
                log.error("[OpenWA] Sessão do WhatsApp não encontrada no servidor (HTTP 404).");
                return new ResultadoEnvio(false, "Sessão do WhatsApp desligada ou inexistente. Volte a ler o QR no OpenWA.", StatusSessao.DESLIGADO);
            }

            // 5xx ou outros códigos
            if (code >= 500 && tentativa == 0) {
                log.warn("[OpenWA] Erro no servidor (HTTP {}). Tentando nova tentativa em 2 segundos...", code);
                pausar(2000L);
                return executarPedidoEnvio(endpoint, corpoJson, telLimpo, 1);
            }

            log.warn("[OpenWA] Falha no envio para {}: HTTP {}", telMasc, code);
            return new ResultadoEnvio(false, "Falha no envio via OpenWA (HTTP " + code + ").", StatusSessao.SERVIDOR_INACESSIVEL);

        } catch (HttpTimeoutException e) {
            if (tentativa == 0) {
                log.warn("[OpenWA] Timeout ao comunicar com OpenWA. Tentando novamente em 2 segundos...");
                pausar(2000L);
                return executarPedidoEnvio(endpoint, corpoJson, telLimpo, 1);
            }
            log.error("[OpenWA] Timeout excedido na comunicação com OpenWA.");
            return new ResultadoEnvio(false, "Servidor OpenWA não respondeu a tempo (timeout).", StatusSessao.SERVIDOR_INACESSIVEL);

        } catch (IOException e) {
            if (tentativa == 0) {
                log.warn("[OpenWA] Falha de rede ao conectar com OpenWA. Tentando novamente em 2 segundos...");
                pausar(2000L);
                return executarPedidoEnvio(endpoint, corpoJson, telLimpo, 1);
            }
            log.error("[OpenWA] Falha de rede ao comunicar com OpenWA: {}", e.getMessage());
            return new ResultadoEnvio(false, "Servidor OpenWA inacessível.", StatusSessao.SERVIDOR_INACESSIVEL);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoEnvio(false, "Envio interrompido.", StatusSessao.SERVIDOR_INACESSIVEL);
        } catch (Exception e) {
            log.error("[OpenWA] Erro inesperado ao comunicar com OpenWA: {}", e.getMessage());
            return new ResultadoEnvio(false, "Erro ao conectar com OpenWA: " + e.getMessage(), StatusSessao.SERVIDOR_INACESSIVEL);
        }
    }

    @Override
    public StatusSessao verificarStatusSessao() {
        if (baseUrl == null || apiKey == null || apiKey.isBlank() || sessionId == null || sessionId.isBlank()) {
            return StatusSessao.NAO_CONFIGURADO;
        }

        long agora = System.currentTimeMillis();
        if (statusSessaoCache != null && (agora - statusSessaoCacheTimestamp) < CACHE_STATUS_TTL_MILLIS) {
            return statusSessaoCache;
        }

        StatusSessao novoStatus = consultarStatusSessaoRemoto();
        this.statusSessaoCache = novoStatus;
        this.statusSessaoCacheTimestamp = agora;
        return novoStatus;
    }

    private StatusSessao consultarStatusSessaoRemoto() {
        try {
            String endpoint = baseUrl + "/api/sessions/" + sessionId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.min(timeoutSeconds, 4)))
                    .header("X-API-Key", this.apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();

            if (code == 401 || code == 403) {
                log.warn("[OpenWA] Verificação de status falhou com código de autenticação: {}", code);
                return StatusSessao.ERRO_AUTENTICACAO;
            }
            if (code == 404) {
                log.warn("[OpenWA] Sessão não encontrada no OpenWA.");
                return StatusSessao.DESLIGADO;
            }
            if (code >= 200 && code < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    JsonNode root = MAPPER.readTree(body);
                    String status = root.path("status").asText("");
                    if ("ready".equalsIgnoreCase(status) || "authenticated".equalsIgnoreCase(status) || "connected".equalsIgnoreCase(status)) {
                        return StatusSessao.CONECTADO;
                    }
                }
                return StatusSessao.DESLIGADO;
            }
            return StatusSessao.SERVIDOR_INACESSIVEL;
        } catch (Exception e) {
            log.warn("[OpenWA] Não foi possível verificar o estado da sessão: {}", e.getMessage());
            return StatusSessao.SERVIDOR_INACESSIVEL;
        }
    }

    private void verificarViradaDoDia() {
        LocalDate hoje = LocalDate.now();
        if (!hoje.equals(dataContadorDiario)) {
            dataContadorDiario = hoje;
            contadorDiario.set(0);
        }
    }

    private void limparHistoricoAntigo() {
        long limite = System.currentTimeMillis() - 120_000L;
        historicoMensagensRecentes.entrySet().removeIf(entry -> entry.getValue() < limite);
    }

    private void pausar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
