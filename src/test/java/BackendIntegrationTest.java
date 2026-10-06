import api.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dao.*;
import model.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import service.BarracaService;
import service.VendaIdempotenteService;
import util.JPAUtil;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class BackendIntegrationTest {
    static ApiServer server;
    static final ObjectMapper json = new ObjectMapper();
    final BarracaService service = new BarracaService();
    final VendaIdempotenteService safe = new VendaIdempotenteService();
    Usuario user;
    Categoria category;
    Produto product;
    String cookie;

    @BeforeAll static void start() {
        assertTrue(System.getenv("DATABASE_URL").startsWith("jdbc:h2:mem:"), "Os testes só podem usar H2 em memória.");
        JPAUtil.inicializar();
        server = new ApiServer();
        server.start(0);
    }
    @AfterAll static void stop() { if (server != null) server.stop(); JPAUtil.fechar(); }
    @BeforeEach void seed() {
        user = new Usuario("Teste", UUID.randomUUID() + "@example.test", "SenhaTeste2026", "operator");
        user.aplicarDiasAcesso(30);
        user = new UsuarioDAO().salvar(user);
        category = service.criarCategoria("Mercearia", "", user.getId());
        product = service.criarProduto("Arroz", new BigDecimal("80.00"), new BigDecimal("100.000"), "kg", new BigDecimal("5.000"), category.getId(), "12345", user.getId());
        cookie = "bstore_session=" + new SessaoDAO().criar(user).getId();
    }
    List<Map<String, Object>> cart(Produto p, String amount) { return List.of(Map.of("produtoId", p.getId(), "quantidade", amount)); }
    BigDecimal stock(Produto p) { return new ProdutoDAO().buscarPorId(p.getId(), user.getId()).getQuantidadeStock(); }
    HttpResponse<String> request(String method, String path, Object body, String key) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
            .header("Cookie", cookie).header("Content-Type", "application/json");
        if (key != null) builder.header("Idempotency-Key", key);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void replayKeepsSnapshotAndDoesNotDiscountStockAgain() throws Exception {
        String key = UUID.randomUUID().toString();
        VendaIdempotenteService.Resultado first = safe.registar(cart(product, "1.250"), user.getId(), key);
        assertFalse(first.repetido);
        service.actualizarProduto(product.getId(), "Arroz novo", new BigDecimal("90.00"), stock(product), "kg", new BigDecimal("5.000"), category.getId(), "12345", user.getId());
        VendaIdempotenteService.Resultado replay = new VendaIdempotenteService().registar(cart(product, "1.25"), user.getId(), key);
        assertTrue(replay.repetido);
        assertEquals(first.json, replay.json);
        assertEquals("100.00", json.readTree(replay.json).get("total").asText());
        assertEquals(new BigDecimal("98.750"), stock(product));
        assertEquals(1, service.listarVendas(user.getId()).size());
    }

    @Test void sameKeyWithAnotherCartIsRejected() {
        String key = UUID.randomUUID().toString();
        safe.registar(cart(product, "1"), user.getId(), key);
        assertThrows(BarracaService.ConflitoException.class, () -> safe.registar(cart(product, "2"), user.getId(), key));
        assertEquals(new BigDecimal("99.000"), stock(product));
    }

    @Test void failedSaleRollsBackReservationAndStock() {
        Produto other = service.criarProduto("Sal", new BigDecimal("20.00"), new BigDecimal("1.000"), "un", BigDecimal.ZERO, category.getId(), null, user.getId());
        String key = UUID.randomUUID().toString();
        List<Map<String, Object>> items = List.of(cart(product, "2").get(0), cart(other, "2").get(0));
        assertThrows(BarracaService.ConflitoException.class, () -> safe.registar(items, user.getId(), key));
        assertEquals(new BigDecimal("100.000"), stock(product));
        assertEquals(0, service.listarVendas(user.getId()).size());
        assertNull(JPAUtil.emTransacao(user.getId(), em -> em.find(PedidoVenda.class, new PedidoVendaId(user.getId(), key))));
        assertFalse(safe.registar(cart(product, "2"), user.getId(), key).repetido);
    }

    @Test void concurrentRetriesCommitOnlyOneSale() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<VendaIdempotenteService.Resultado>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> { start.await(); return safe.registar(cart(product, "2"), user.getId(), key); }));
            start.countDown();
            Set<String> results = new HashSet<>();
            int created = 0;
            for (Future<VendaIdempotenteService.Resultado> future : futures) {
                VendaIdempotenteService.Resultado result = future.get(20, TimeUnit.SECONDS);
                results.add(result.json);
                if (!result.repetido) created++;
            }
            assertEquals(1, created);
            assertEquals(1, results.size());
            assertEquals(new BigDecimal("98.000"), stock(product));
            assertEquals(1, service.listarVendas(user.getId()).size());
        } finally { pool.shutdownNow(); }
    }

    @Test void keysAndQueriesAreScopedToUser() {
        String key = UUID.randomUUID().toString();
        safe.registar(cart(product, "1"), user.getId(), key);
        Usuario other = new UsuarioDAO().salvar(new Usuario("Outro", UUID.randomUUID() + "@example.test", "SenhaTeste2026", "superuser"));
        assertThrows(BarracaService.RecursoNaoEncontradoException.class, () -> safe.registar(cart(product, "1"), other.getId(), key));
        Categoria c = service.criarCategoria("Outro", "", other.getId());
        Produto p = service.criarProduto("Outro produto", BigDecimal.TEN, BigDecimal.TEN, "un", BigDecimal.ZERO, c.getId(), null, other.getId());
        assertFalse(safe.registar(cart(p, "1"), other.getId(), key).repetido);
        assertEquals(1, new ProdutoDAO().listarPagina(user.getId(), 1, 25, "", null, "all", "name").getTotal());
    }

    @Test void productPaginationFiltersAndLiteralWildcards() {
        for (int i = 0; i < 60; i++) service.criarProduto(String.format("Produto %02d", i), BigDecimal.valueOf(i), BigDecimal.valueOf(i), "un", BigDecimal.valueOf(5), category.getId(), null, user.getId());
        service.criarProduto("Promoção 10%_!", BigDecimal.TEN, BigDecimal.TEN, "un", BigDecimal.ZERO, category.getId(), null, user.getId());
        ProdutoDAO dao = new ProdutoDAO();
        Pagina<Produto> first = dao.listarPagina(user.getId(), 1, 25, "Produto", category.getId(), "all", "name");
        Pagina<Produto> last = dao.listarPagina(user.getId(), 3, 25, "Produto", category.getId(), "all", "name");
        assertEquals(60, first.getTotal()); assertEquals(25, first.getItems().size()); assertEquals(10, last.getItems().size());
        assertEquals("Produto 50", last.getItems().get(0).getNome());
        assertEquals(1, dao.listarPagina(user.getId(), 1, 25, "%_!", null, "all", "name").getTotal());
        assertEquals(6, dao.listarPagina(user.getId(), 1, 25, "", null, "low", "stock").getTotal());
        assertThrows(IllegalArgumentException.class, () -> dao.listarPagina(user.getId(), 0, 25, "", null, "all", "name"));
        assertThrows(IllegalArgumentException.class, () -> dao.listarPagina(user.getId(), 1, 101, "", null, "all", "name"));
        assertThrows(IllegalArgumentException.class, () -> dao.listarPagina(user.getId(), 1, 25, "", null, "all", "injection"));
    }

    void history(int count) {
        JPAUtil.emTransacao(user.getId(), em -> {
            Produto managed = em.find(Produto.class, product.getId());
            for (int i = 0; i < count; i++) {
                Venda v = new Venda(LocalDate.now().minusDays(i % 7), BigDecimal.ONE, managed);
                v.setUsuarioId(user.getId()); em.persist(v);
            }
            return null;
        });
    }

    @Test void salesPaginationDateRangeAndDailyAggregation() {
        history(70);
        VendaDAO dao = new VendaDAO();
        assertEquals(7, dao.totaisPorDia(LocalDate.now().minusDays(6), LocalDate.now(), user.getId()).size());
        assertEquals(new BigDecimal("800.00"), dao.totaisPorDia(LocalDate.now(), LocalDate.now(), user.getId()).get(LocalDate.now()));
        assertEquals(10, dao.recentes(user.getId(), 10).size());
        Pagina<Venda> today = dao.listarPagina(user.getId(), 2, 6, "arroz", LocalDate.now(), LocalDate.now());
        assertEquals(10, today.getTotal()); assertEquals(4, today.getItems().size());
        assertThrows(IllegalArgumentException.class, () -> dao.listarPagina(user.getId(), 1, 25, "", LocalDate.now(), LocalDate.now().minusDays(1)));
    }

    @Test void httpContractsReplayPaginationAndBoundedDashboardQueries() throws Exception {
        history(140);
        HttpResponse<String> list = request("GET", "/api/produtos?page=1&pageSize=25", null, null);
        assertEquals(200, list.statusCode()); assertEquals(1, json.readTree(list.body()).get("total").asInt());
        assertTrue(json.readTree(request("GET", "/api/produtos", null, null).body()).isArray());
        assertEquals(400, request("GET", "/api/produtos?page=-1", null, null).statusCode());
        assertEquals(400, request("GET", "/api/vendas?page=1&inicio=invalida", null, null).statusCode());
        SessionFactory factory = JPAUtil.emTransacao(user.getId(), em -> em.getEntityManagerFactory().unwrap(SessionFactory.class));
        factory.getStatistics().setStatisticsEnabled(true); factory.getStatistics().clear();
        HttpResponse<String> result = request("GET", "/api/dashboard", null, null);
        assertEquals(200, result.statusCode());
        assertEquals(10, json.readTree(result.body()).get("vendasRecentes").size());
        assertEquals("1600.00", json.readTree(result.body()).get("totalVendasHoje").asText());
        assertTrue(factory.getStatistics().getPrepareStatementCount() <= 12, "Consultas do dashboard: " + factory.getStatistics().getPrepareStatementCount());
        String key = UUID.randomUUID().toString();
        HttpResponse<String> first = request("POST", "/api/vendas/lote", cart(product, "1"), key);
        HttpResponse<String> replay = request("POST", "/api/vendas/lote", cart(product, "1"), key);
        assertEquals(201, first.statusCode()); assertEquals(200, replay.statusCode());
        assertEquals(first.body(), replay.body()); assertEquals("true", replay.headers().firstValue("Idempotency-Replayed").orElse(""));
        assertEquals(409, request("POST", "/api/vendas/lote", cart(product, "2"), key).statusCode());
        assertEquals(400, request("POST", "/api/vendas/lote", cart(product, "1"), "bad").statusCode());
    }

    @Test void categoryCountsAreGrouped() {
        Categoria empty = service.criarCategoria("Vazia", "", user.getId());
        Map<Long, Long> counts = new CategoriaDAO().contarProdutosPorCategoria(user.getId());
        assertEquals(1L, counts.get(category.getId()));
        assertEquals(0L, counts.getOrDefault(empty.getId(), 0L));
        assertEquals(2, new CategoriaDAO().contarTodos(user.getId()));
    }
}
