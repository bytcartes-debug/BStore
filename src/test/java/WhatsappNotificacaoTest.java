import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.Produto;
import model.Venda;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.notificacao.NotificacaoWhatsAppClient;
import service.notificacao.OpenWaClient;
import service.notificacao.WhatsappNotificacaoService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class WhatsappNotificacaoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer mockServer;
    private int mockPort;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPort = mockServer.getAddress().getPort();
        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void testEnvioComFormatoECabecalhosCorretos() {
        List<String> receivedMethods = Collections.synchronizedList(new ArrayList<>());
        List<String> receivedPaths = Collections.synchronizedList(new ArrayList<>());
        List<String> receivedKeys = Collections.synchronizedList(new ArrayList<>());
        List<String> receivedBodies = Collections.synchronizedList(new ArrayList<>());

        mockServer.createContext("/api/sessions/test-session/messages/send-text", exchange -> {
            receivedMethods.add(exchange.getRequestMethod());
            receivedPaths.add(exchange.getRequestURI().getPath());
            receivedKeys.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBodies.add(body);

            String response = "{\"messageId\":\"msg-123\",\"timestamp\":123456789}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        OpenWaClient client = new OpenWaClient(
                true, baseUrl, "minha-chave-secreta", "test-session", "258841234567", 5, 100, null
        );

        boolean enviado = client.enviarTexto("+258 84 123-4567", "Mensagem de teste unitário");
        assertTrue(enviado, "O envio deve retornar sucesso");

        assertEquals(1, receivedMethods.size());
        assertEquals("POST", receivedMethods.get(0));
        assertEquals("/api/sessions/test-session/messages/send-text", receivedPaths.get(0));
        assertEquals("minha-chave-secreta", receivedKeys.get(0));

        assertDoesNotThrow(() -> {
            JsonNode json = MAPPER.readTree(receivedBodies.get(0));
            assertEquals("258841234567@c.us", json.path("chatId").asText());
            assertEquals("Mensagem de teste unitário", json.path("text").asText());
        });
    }

    @Test
    void testNaoTentaNovamenteEm401E403() {
        AtomicInteger requisicoes401 = new AtomicInteger(0);
        AtomicInteger requisicoes403 = new AtomicInteger(0);

        mockServer.createContext("/api/sessions/auth-401/messages/send-text", exchange -> {
            requisicoes401.incrementAndGet();
            byte[] resp = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });

        mockServer.createContext("/api/sessions/auth-403/messages/send-text", exchange -> {
            requisicoes403.incrementAndGet();
            byte[] resp = "{\"error\":\"Forbidden\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        OpenWaClient client401 = new OpenWaClient(true, baseUrl, "chave-errada", "auth-401", "258841234567", 5, 100, null);
        var res401 = client401.enviarTextoDetalhado("258841234567", "Teste 401");
        assertFalse(res401.isSucesso());
        assertEquals(NotificacaoWhatsAppClient.StatusSessao.ERRO_AUTENTICACAO, res401.getStatus());
        assertEquals(1, requisicoes401.get(), "Erro 401 não deve fazer retry");

        OpenWaClient client403 = new OpenWaClient(true, baseUrl, "chave-sem-permissao", "auth-403", "258841234567", 5, 100, null);
        var res403 = client403.enviarTextoDetalhado("258841234567", "Teste 403");
        assertFalse(res403.isSucesso());
        assertEquals(NotificacaoWhatsAppClient.StatusSessao.ERRO_AUTENTICACAO, res403.getStatus());
        assertEquals(1, requisicoes403.get(), "Erro 403 não deve fazer retry");
    }

    @Test
    void testRetryEm503() {
        AtomicInteger requisicoes = new AtomicInteger(0);

        mockServer.createContext("/api/sessions/retry-sess/messages/send-text", exchange -> {
            int tentativa = requisicoes.incrementAndGet();
            if (tentativa == 1) {
                byte[] resp = "{\"error\":\"Service Unavailable\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, resp.length);
                exchange.getResponseBody().write(resp);
            } else {
                byte[] resp = "{\"messageId\":\"msg-ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                exchange.getResponseBody().write(resp);
            }
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        OpenWaClient client = new OpenWaClient(true, baseUrl, "chave", "retry-sess", "258841234567", 5, 100, null);

        var res = client.enviarTextoDetalhado("258841234567", "Teste 503 com retry");
        assertTrue(res.isSucesso(), "A segunda tentativa deve ter sucesso");
        assertEquals(2, requisicoes.get(), "Deve ter executado exatamente 2 requisições (1 retry)");
    }

    @Test
    void testTimeoutNaoBloqueiaVenda() {
        mockServer.createContext("/api/sessions/slow-sess/messages/send-text", exchange -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {}
            byte[] resp = "{\"messageId\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        OpenWaClient client = new OpenWaClient(true, baseUrl, "chave", "slow-sess", "258841234567", 2, 100, null);
        WhatsappNotificacaoService service = new WhatsappNotificacaoService(null, null, client);

        Venda v = new Venda();
        v.setId(999L);
        v.setNumero(999L);
        v.setCriadaEm(LocalDateTime.now());
        v.setTotal(new BigDecimal("150.00"));

        long inicio = System.currentTimeMillis();
        service.notificarVendaAsync(v, null, null, BigDecimal.ZERO, "Operador Teste", 1L);
        long duracao = System.currentTimeMillis() - inicio;

        assertTrue(duracao < 500, "O método notificarVendaAsync deve retornar imediatamente (menos de 500ms)");
    }

    @Test
    void testDesativadoOuIncompletoNenhumPedidoSai() {
        AtomicInteger count = new AtomicInteger(0);
        mockServer.createContext("/api/sessions/inactive/messages/send-text", exchange -> {
            count.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        // Cliente desativado
        OpenWaClient clientDesativado = new OpenWaClient(false, baseUrl, "chave", "inactive", "258841234567", 5, 100, null);
        assertFalse(clientDesativado.enviarTexto("258841234567", "Teste desativado"));

        // Cliente sem chave
        OpenWaClient clientSemChave = new OpenWaClient(true, baseUrl, "", "inactive", "258841234567", 5, 100, null);
        assertFalse(clientSemChave.enviarTexto("258841234567", "Teste sem chave"));

        assertEquals(0, count.get(), "Nenhuma requisição HTTP deve ser emitida se desativado ou incompleto");
    }

    @Test
    void testNormalizacaoTelefoneERejeicaoInvalido() {
        assertEquals("258841234567", OpenWaClient.normalizarTelefone("+258 84 123-4567"));
        assertEquals("5511999998888", OpenWaClient.normalizarTelefone("+55 (11) 99999-8888"));
        assertNull(OpenWaClient.normalizarTelefone("12345"));
        assertNull(OpenWaClient.normalizarTelefone(""));
        assertNull(OpenWaClient.normalizarTelefone(null));
    }

    @Test
    void testMascaramentoTelefoneELogs() {
        String masc = OpenWaClient.mascararTelefone("258841234567");
        assertEquals("***567", masc);
        assertFalse(masc.contains("25884123"));

        String mascCurto = OpenWaClient.mascararTelefone("841");
        assertEquals("***841", mascCurto);
    }

    @Test
    void testLimiteDiario() {
        mockServer.createContext("/api/sessions/quota-sess/messages/send-text", exchange -> {
            byte[] resp = "{\"messageId\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        // Limite de 2 envios por dia
        OpenWaClient client = new OpenWaClient(true, baseUrl, "chave", "quota-sess", "258841234567", 5, 2, null);

        var r1 = client.enviarTextoDetalhado("258841234567", "Mensagem A");
        assertTrue(r1.isSucesso());

        var r2 = client.enviarTextoDetalhado("258841234567", "Mensagem B");
        assertTrue(r2.isSucesso());

        var r3 = client.enviarTextoDetalhado("258841234567", "Mensagem C");
        assertFalse(r3.isSucesso(), "A terceira mensagem deve ser bloqueada pelo teto diário");
        assertTrue(r3.getMensagem().toLowerCase().contains("limite diário"));
    }

    @Test
    void testDeduplicacao60Segundos() {
        AtomicInteger contagem = new AtomicInteger(0);
        mockServer.createContext("/api/sessions/dedup-sess/messages/send-text", exchange -> {
            contagem.incrementAndGet();
            byte[] resp = "{\"messageId\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + mockPort;
        OpenWaClient client = new OpenWaClient(true, baseUrl, "chave", "dedup-sess", "258841234567", 5, 100, null);

        var r1 = client.enviarTextoDetalhado("258841234567", "Mensagem Repetida");
        assertTrue(r1.isSucesso());
        assertEquals(1, contagem.get());

        // Segunda mensagem idêntica em menos de 60 segundos
        var r2 = client.enviarTextoDetalhado("258841234567", "Mensagem Repetida");
        assertTrue(r2.isSucesso(), "Retorna sucesso amigável por agrupamento");
        assertEquals(1, contagem.get(), "Não deve disparar uma segunda requisição HTTP");
    }

    @Test
    void testLiveOpenWaConexaoSeDisponivel() {
        OpenWaClient client = new OpenWaClient();
        if (client.isAtivo()) {
            NotificacaoWhatsAppClient.StatusSessao status = client.verificarStatusSessao();
            System.out.println("[TESTE LIVE OPENWA] Status da sessao: " + status);
            assertEquals(NotificacaoWhatsAppClient.StatusSessao.CONECTADO, status, "A sessão local do OpenWA deve estar conectada");

            NotificacaoWhatsAppClient.ResultadoEnvio res = client.enviarTextoDetalhado(
                    client.getTelefoneDono(),
                    "BStore: teste live de notificacao via OpenWA 100% operacional!"
            );
            System.out.println("[TESTE LIVE OPENWA] Resultado: " + res.isSucesso() + " | " + res.getMensagem());
            assertTrue(res.isSucesso(), "O envio live para o OpenWA deve retornar sucesso");
        }
    }

    @Test
    void testResilienciaSemLancamentoDeExcecao() {
        WhatsappNotificacaoService service = new WhatsappNotificacaoService();

        assertDoesNotThrow(() -> service.notificarVendaAsync(null, null, null, null, null, null));
        assertDoesNotThrow(() -> service.notificarBaixoEstoqueAsync(null, null));
        assertDoesNotThrow(() -> service.notificarNovoProdutoAsync(null, null));
        assertDoesNotThrow(() -> service.notificarAberturaCaixaAsync(null, null, null));
        assertDoesNotThrow(() -> service.notificarFechoCaixaAsync(null, null, null, null, null, null));
    }

    @Test
    void testFormatoProdutoStockBaixo() {
        Produto p = new Produto("Refrigerante Coca-Cola 330ml", new BigDecimal("50.00"), new BigDecimal("2.000"), "un", null);
        p.setStockMinimo(new BigDecimal("5.000"));

        assertTrue(p.stockAbaixoMinimo());

        p.setQuantidadeStock(new BigDecimal("10.000"));
        assertFalse(p.stockAbaixoMinimo());
    }
}
