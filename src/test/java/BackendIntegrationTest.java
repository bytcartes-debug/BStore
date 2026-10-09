import api.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dao.*;
import model.*;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import service.BarracaService;
import service.VendaIdempotenteService;
import util.JPAUtil;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
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
                Long num = (long) (i + 1);
                LocalDate dia = LocalDate.now().minusDays(i % 7);
                Venda v = new Venda(user.getId(), num, dia.atTime(12, 0), managed.getPreco(), managed.getCusto(), "CONCLUIDA", null, null, user.getId());
                em.persist(v);
                ItemVenda iv = new ItemVenda(v, managed, BigDecimal.ONE, managed.getPreco(), managed.getCusto(), null, user.getId());
                iv.setDataVenda(dia);
                em.persist(iv);
                v.addItem(iv);
                PagamentoVenda pv = new PagamentoVenda(user.getId(), v, "DINHEIRO", managed.getPreco(), BigDecimal.ZERO);
                em.persist(pv);
                v.addPagamento(pv);
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

    @Test void stockIntegrityMaintainedAcrossOperations() throws Exception {
        service.StockService stockService = new service.StockService();
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));

        // 1. Entrada de stock
        Map<String, Object> entrada = Map.of(
            "produtoId", product.getId(),
            "quantidade", "50.000",
            "custoUnitario", "60.00",
            "motivo", "Compra grossista"
        );
        HttpResponse<String> respEntrada = request("POST", "/api/stock/entradas", entrada, null);
        assertEquals(201, respEntrada.statusCode());
        assertEquals(new BigDecimal("150.000"), stock(product));
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));

        // 2. Venda
        request("POST", "/api/vendas/lote", cart(product, "20.000"), UUID.randomUUID().toString());
        assertEquals(new BigDecimal("130.000"), stock(product));
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));

        // 3. Ajuste
        Map<String, Object> ajuste = Map.of(
            "produtoId", product.getId(),
            "diferenca", "-10.000",
            "motivo", "Danificado"
        );
        HttpResponse<String> respAjuste = request("POST", "/api/stock/ajustes", ajuste, null);
        assertEquals(201, respAjuste.statusCode());
        assertEquals(new BigDecimal("120.000"), stock(product));
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));

        // 4. Contagem de inventario
        Map<String, Object> contagem = Map.of(
            "itens", List.of(Map.of("produtoId", product.getId(), "quantidadeContada", "115.000"))
        );
        HttpResponse<String> respContagem = request("POST", "/api/stock/contagem", contagem, null);
        assertEquals(201, respContagem.statusCode());
        assertEquals(new BigDecimal("115.000"), stock(product));
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));

        // 5. Venda e Anulação
        HttpResponse<String> respVenda = request("POST", "/api/vendas/lote", cart(product, "5.000"), UUID.randomUUID().toString());
        assertEquals(201, respVenda.statusCode());
        long vendaId = json.readTree(respVenda.body()).get("id").asLong();
        assertEquals(new BigDecimal("110.000"), stock(product));
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));

        HttpResponse<String> respAnular = request("POST", "/api/vendas/" + vendaId + "/anular", Map.of("motivo", "Devolução teste"), null);
        assertEquals(200, respAnular.statusCode());
        assertEquals(new BigDecimal("115.000"), stock(product));
        assertTrue(stockService.verificarIntegridadeStock(user.getId(), product.getId()));
    }

    @Test void weightedAverageCostCalculatedCorrectly() throws Exception {
        Produto item = service.criarProduto("Farinha", new BigDecimal("100.00"), BigDecimal.ZERO, "kg", BigDecimal.ZERO, category.getId(), null, user.getId());
        request("POST", "/api/stock/entradas", Map.of("produtoId", item.getId(), "quantidade", "10.000", "custoUnitario", "100.00"), null);
        Produto pos1 = new ProdutoDAO().buscarPorId(item.getId(), user.getId());
        assertEquals(new BigDecimal("100.00"), pos1.getCusto());

        request("POST", "/api/stock/entradas", Map.of("produtoId", item.getId(), "quantidade", "10.000", "custoUnitario", "200.00"), null);
        Produto pos2 = new ProdutoDAO().buscarPorId(item.getId(), user.getId());
        assertEquals(new BigDecimal("150.00"), pos2.getCusto());
    }

    @Test void directStockModificationViaPutIsRejected() throws Exception {
        Map<String, Object> body = Map.of(
            "nome", product.getNome(),
            "preco", "80.00",
            "stock", "999.000",
            "categoriaId", category.getId()
        );
        HttpResponse<String> resp = request("PUT", "/api/produtos/" + product.getId(), body, null);
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Entrada ou Ajuste"));
        assertEquals(new BigDecimal("100.000"), stock(product));
    }

    @Test void archivingPreservesHistoryAndHidesFromSale() throws Exception {
        request("POST", "/api/vendas/lote", cart(product, "1.000"), UUID.randomUUID().toString());
        HttpResponse<String> del = request("DELETE", "/api/produtos/" + product.getId(), null, null);
        assertEquals(204, del.statusCode());

        ProdutoDAO pdao = new ProdutoDAO();
        Produto managed = pdao.buscarPorId(product.getId(), user.getId());
        assertNotNull(managed);
        assertFalse(managed.isAtivo());

        HttpResponse<String> list = request("GET", "/api/produtos", null, null);
        assertFalse(list.body().contains("\"id\":" + product.getId()));

        HttpResponse<String> listArq = request("GET", "/api/produtos?incluirArquivados=true", null, null);
        assertTrue(listArq.body().contains("\"id\":" + product.getId()));

        assertThrows(BarracaService.ConflitoException.class, () ->
            service.registarVenda(product.getId(), BigDecimal.ONE, "", user.getId())
        );
    }

    @Test void splitPaymentAndFiadoCreateDocumentAndDebtCorrectly() throws Exception {
        // 1. Pagamento dividido: DINHEIRO MT 50.00 + MPESA MT 30.00 = MT 80.00 (preço do arroz)
        Map<String, Object> splitSale = Map.of(
            "itens", List.of(Map.of("produtoId", product.getId(), "quantidade", "1.000")),
            "pagamentos", List.of(
                Map.of("metodo", "DINHEIRO", "valor", "50.00"),
                Map.of("metodo", "MPESA", "valor", "30.00")
            ),
            "observacao", "Venda dividida teste"
        );
        HttpResponse<String> respSplit = request("POST", "/api/vendas/lote", splitSale, UUID.randomUUID().toString());
        assertEquals(201, respSplit.statusCode());
        JsonNode splitJson = json.readTree(respSplit.body());
        assertEquals("80.00", splitJson.get("total").asText());
        assertEquals("0.00", splitJson.get("troco").asText());
        long vendaId = splitJson.get("id").asLong();

        HttpResponse<String> detailResp = request("GET", "/api/vendas/" + vendaId, null, null);
        assertEquals(200, detailResp.statusCode());
        JsonNode detailJson = json.readTree(detailResp.body());
        assertEquals(2, detailJson.get("pagamentos").size());
        assertEquals(1, detailJson.get("itens").size());

        // 2. Venda a fiado: cria cliente primeiro
        HttpResponse<String> devResp = request("POST", "/api/devedores", Map.of("nome", "Cliente Fiado", "divida", "10.00"), null);
        assertEquals(201, devResp.statusCode());
        long clienteId = json.readTree(devResp.body()).get("id").asLong();

        Map<String, Object> fiadoSale = Map.of(
            "itens", List.of(Map.of("produtoId", product.getId(), "quantidade", "1.000")),
            "pagamentos", List.of(Map.of("metodo", "FIADO", "valor", "80.00")),
            "clienteId", clienteId,
            "observacao", "Fiado teste"
        );
        HttpResponse<String> respFiado = request("POST", "/api/vendas/lote", fiadoSale, UUID.randomUUID().toString());
        assertEquals(201, respFiado.statusCode());

        // Saldo do cliente deve somar 10.00 anterior + 80.00 novo = 90.00
        HttpResponse<String> devList = request("GET", "/api/devedores", null, null);
        assertEquals(200, devList.statusCode());
        JsonNode devs = json.readTree(devList.body());
        JsonNode c = null;
        for (JsonNode n : devs) { if (n.get("id").asLong() == clienteId) c = n; }
        assertNotNull(c);
        assertEquals("90.00", c.get("saldo").asText());

        // 3. Pagamento parcial da dívida
        Map<String, Object> pagBody = Map.of("valor", "40.00", "metodo", "MPESA", "observacao", "Amortização");
        HttpResponse<String> pagResp = request("POST", "/api/devedores/" + clienteId + "/pagamentos", pagBody, null);
        assertEquals(201, pagResp.statusCode());
        assertEquals("50.00", json.readTree(pagResp.body()).get("saldoRestante").asText());

        // Pagar mais que o saldo é rejeitado
        HttpResponse<String> pagExcessivo = request("POST", "/api/devedores/" + clienteId + "/pagamentos",
            Map.of("valor", "100.00", "metodo", "DINHEIRO"), null);
        assertEquals(400, pagExcessivo.statusCode());

        // Apagar devedor com pagamentos é bloqueado
        HttpResponse<String> delDev = request("DELETE", "/api/devedores/" + clienteId, null, null);
        assertEquals(409, delDev.statusCode());
    }

    @Test void anulacaoRestoresStockIdempotentlyAndCancelsDebt() throws Exception {
        BigDecimal stockAntes = stock(product);

        // Criar venda a fiado
        HttpResponse<String> devResp = request("POST", "/api/devedores", Map.of("nome", "Devedor Anulação", "divida", "0.01"), null);
        long clienteId = json.readTree(devResp.body()).get("id").asLong();

        Map<String, Object> sale = Map.of(
            "itens", List.of(Map.of("produtoId", product.getId(), "quantidade", "2.000")),
            "pagamentos", List.of(Map.of("metodo", "FIADO", "valor", "160.00")),
            "clienteId", clienteId,
            "observacao", "Para anular"
        );
        HttpResponse<String> saleResp = request("POST", "/api/vendas/lote", sale, UUID.randomUUID().toString());
        assertEquals(201, saleResp.statusCode());
        long vendaId = json.readTree(saleResp.body()).get("id").asLong();
        assertEquals(stockAntes.subtract(new BigDecimal("2.000")), stock(product));

        // Anular venda
        HttpResponse<String> anularResp = request("POST", "/api/vendas/" + vendaId + "/anular", Map.of("motivo", "Engano no produto"), null);
        assertEquals(200, anularResp.statusCode());
        assertEquals("ANULADA", json.readTree(anularResp.body()).get("estado").asText());
        assertEquals(stockAntes, stock(product)); // Stock reposto!

        // Repetir anulação é idempotente: não repõe o stock novamente
        HttpResponse<String> anularAgain = request("POST", "/api/vendas/" + vendaId + "/anular", Map.of("motivo", "Repetição"), null);
        assertEquals(200, anularAgain.statusCode());
        assertEquals(stockAntes, stock(product)); // Stock intacto!

        // Dívida correspondente foi cancelada
        HttpResponse<String> devList = request("GET", "/api/devedores", null, null);
        for (JsonNode n : json.readTree(devList.body())) {
            if (n.get("id").asLong() == clienteId) {
                assertEquals("0.01", n.get("saldo").asText());
            }
        }
    }

    @Test void cannotHaveNegativeStockEvenWithConcurrentSalesOfLastItem() throws Exception {
        Produto scarce = service.criarProduto("Item Raro", new BigDecimal("50.00"), BigDecimal.ONE, "un", BigDecimal.ZERO, category.getId(), null, user.getId());
        assertEquals(new BigDecimal("1.000"), stock(scarce));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> f1 = pool.submit(() -> {
                start.await();
                try {
                    safe.registar(cart(scarce, "1"), user.getId(), UUID.randomUUID().toString());
                    return 201;
                } catch (BarracaService.ConflitoException e) {
                    return 409;
                }
            });
            Future<Integer> f2 = pool.submit(() -> {
                start.await();
                try {
                    safe.registar(cart(scarce, "1"), user.getId(), UUID.randomUUID().toString());
                    return 201;
                } catch (BarracaService.ConflitoException e) {
                    return 409;
                }
            });
            start.countDown();
            int r1 = f1.get(10, TimeUnit.SECONDS);
            int r2 = f2.get(10, TimeUnit.SECONDS);
            assertTrue((r1 == 201 && r2 == 409) || (r1 == 409 && r2 == 201));
            assertEquals(new BigDecimal("0.000"), stock(scarce));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void userIsolationForSalesMovementsAndPayments() throws Exception {
        request("POST", "/api/vendas/lote", cart(product, "1.000"), UUID.randomUUID().toString());

        Usuario other = new UsuarioDAO().salvar(new Usuario("Isolado", UUID.randomUUID() + "@example.test", "SenhaTeste2026", "operator"));
        other.aplicarDiasAcesso(30);
        String otherCookie = "bstore_session=" + new SessaoDAO().criar(other).getId();

        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/vendas"))
            .header("Cookie", otherCookie).GET().build();
        HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());

        HttpRequest reqMov = HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/stock/movimentos"))
            .header("Cookie", otherCookie).GET().build();
        HttpResponse<String> respMov = HttpClient.newHttpClient().send(reqMov, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, respMov.statusCode());
        assertEquals(0, json.readTree(respMov.body()).get("total").asInt());
    }

    @Test void caixaFechoReturnsAccurateDailySummary() throws Exception {
        request("POST", "/api/vendas/lote", cart(product, "1.000"), UUID.randomUUID().toString());
        HttpResponse<String> resp = request("GET", "/api/caixa/fecho?data=" + LocalDate.now(), null, null);
        assertEquals(200, resp.statusCode());
        JsonNode j = json.readTree(resp.body());
        assertTrue(j.get("totalVendido").asDouble() >= 80.00);
        assertTrue(j.get("numeroVendas").asLong() >= 1);
        assertEquals(LocalDate.now().toString(), j.get("data").asText());
    }

    @Test void migrationV5V6PreservesLegacyDataAndStock() throws Exception {
        String testDbUrl = "jdbc:h2:mem:migration_legacy_test_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";

        // 1. Aplicar migrações antigas até V4
        Flyway.configure()
            .dataSource(testDbUrl, "sa", "")
            .locations("classpath:db/migration/h2")
            .target("4")
            .load()
            .migrate();

        // 2. Inserir dados legados no esquema V4
        try (Connection conn = DriverManager.getConnection(testDbUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO usuarios (id, nome, email, password_hash, role) VALUES (101, 'Usuario Legado', 'legado@test.local', 'hash', 'operator')");
            stmt.executeUpdate("INSERT INTO categorias (id, nome, usuario_id) VALUES (201, 'Legada', 101)");
            stmt.executeUpdate("INSERT INTO produtos (id, nome, preco, quantidade_stock, stock_minimo, unidade, usuario_id, categoria_id) VALUES (301, 'Produto Legado 1', 100.00, 25.000, 5.000, 'un', 101, 201)");
            stmt.executeUpdate("INSERT INTO produtos (id, nome, preco, quantidade_stock, stock_minimo, unidade, usuario_id, categoria_id) VALUES (302, 'Produto Legado 2', 50.00, 0.000, 5.000, 'un', 101, 201)");
            stmt.executeUpdate("INSERT INTO vendas (id, data_venda, quantidade, preco_unitario, total, observacao, produto_id, usuario_id) VALUES (401, '2026-09-01', 2.000, 100.00, 200.00, 'Venda legada 1', 301, 101)");
            stmt.executeUpdate("INSERT INTO vendas (id, data_venda, quantidade, preco_unitario, total, observacao, produto_id, usuario_id) VALUES (402, '2026-09-02', 1.000, 50.00, 50.00, 'Venda legada 2', 302, 101)");
            stmt.executeUpdate("INSERT INTO devedores (id, nome, divida, data, usuario_id) VALUES (501, 'Devedor Legado', 500.00, '2026-09-01', 101)");
        }

        // 3. Executar migrações V5 e V6
        Flyway.configure()
            .dataSource(testDbUrl, "sa", "")
            .locations("classpath:db/migration/h2")
            .load()
            .migrate();

        // 4. Validar integridade e preservação
        try (Connection conn = DriverManager.getConnection(testDbUrl, "sa", "");
             Statement stmt = conn.createStatement()) {

            // Produtos: custo = 0.00, ativo = true, stock intacto
            try (ResultSet rs = stmt.executeQuery("SELECT id, custo, ativo, quantidade_stock FROM produtos ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(301L, rs.getLong("id"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("custo"));
                assertTrue(rs.getBoolean("ativo"));
                assertEquals(new BigDecimal("25.000"), rs.getBigDecimal("quantidade_stock"));

                assertTrue(rs.next());
                assertEquals(302L, rs.getLong("id"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("custo"));
                assertTrue(rs.getBoolean("ativo"));
                assertEquals(new BigDecimal("0.000"), rs.getBigDecimal("quantidade_stock"));
            }

            // Movimentos de stock: apenas produto com stock > 0 gerou STOCK_INICIAL
            try (ResultSet rs = stmt.executeQuery("SELECT produto_id, tipo, quantidade FROM movimentos_stock")) {
                assertTrue(rs.next());
                assertEquals(301L, rs.getLong("produto_id"));
                assertEquals("STOCK_INICIAL", rs.getString("tipo"));
                assertEquals(new BigDecimal("25.000"), rs.getBigDecimal("quantidade"));
                assertFalse(rs.next(), "Produto com stock zero não deve gerar movimento inicial.");
            }

            // Vendas (cabeçalhos): 2 vendas criadas a partir das linhas legadas
            try (ResultSet rs = stmt.executeQuery("SELECT id, numero, total, total_custo, estado FROM vendas ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong("numero"));
                assertEquals(new BigDecimal("200.00"), rs.getBigDecimal("total"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("total_custo"));
                assertEquals("CONCLUIDA", rs.getString("estado"));

                assertTrue(rs.next());
                assertEquals(2L, rs.getLong("numero"));
                assertEquals(new BigDecimal("50.00"), rs.getBigDecimal("total"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("total_custo"));
                assertEquals("CONCLUIDA", rs.getString("estado"));
            }

            // Venda itens: 2 itens ligados aos cabeçalhos com custo 0
            try (ResultSet rs = stmt.executeQuery("SELECT id, venda_id, total, custo_unitario FROM venda_itens ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(401L, rs.getLong("id"));
                assertNotNull(rs.getObject("venda_id"));
                assertEquals(new BigDecimal("200.00"), rs.getBigDecimal("total"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("custo_unitario"));

                assertTrue(rs.next());
                assertEquals(402L, rs.getLong("id"));
                assertNotNull(rs.getObject("venda_id"));
                assertEquals(new BigDecimal("50.00"), rs.getBigDecimal("total"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("custo_unitario"));
            }

            // Pagamentos venda: cada venda legada recebeu pagamento DINHEIRO com o valor total
            try (ResultSet rs = stmt.executeQuery("SELECT metodo, valor, troco FROM pagamentos_venda ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals("DINHEIRO", rs.getString("metodo"));
                assertEquals(new BigDecimal("200.00"), rs.getBigDecimal("valor"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("troco"));

                assertTrue(rs.next());
                assertEquals("DINHEIRO", rs.getString("metodo"));
                assertEquals(new BigDecimal("50.00"), rs.getBigDecimal("valor"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("troco"));
            }

            // Devedor preservado
            try (ResultSet rs = stmt.executeQuery("SELECT id, divida FROM devedores WHERE id = 501")) {
                assertTrue(rs.next());
                assertEquals(new BigDecimal("500.00"), rs.getBigDecimal("divida"));
            }
        }
    }
}
