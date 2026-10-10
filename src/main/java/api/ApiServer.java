package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dao.CategoriaDAO;
import dao.DevedorDAO;
import dao.SessaoDAO;
import dao.UsuarioDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import model.Categoria;
import model.DefinicaoLoja;
import model.Devedor;
import model.Produto;
import model.Sessao;
import model.SessaoCaixa;
import model.Usuario;
import model.Venda;
import service.BarracaService;
import util.JPAUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ApiServer {

    private static final String SESSION_COOKIE = "bstore_session";
    private static final String AUTH_ATTRIBUTE = "authenticatedUser";
    private static final int SESSION_MAX_AGE_SECONDS = 14 * 24 * 60 * 60;
    private static final DateTimeFormatter DATA_FORMATADA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BarracaService service = new BarracaService();
    private final service.VendaIdempotenteService vendaIdempotente = new service.VendaIdempotenteService();
    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final SessaoDAO sessaoDAO = new SessaoDAO();
    private final dao.ProdutoDAO produtoDAO = new dao.ProdutoDAO();
    private final dao.VendaDAO vendaDAO = new dao.VendaDAO();
    private final service.StockService stockService = new service.StockService();
    private final service.RateLimiterLogin rateLimiterLogin = new service.RateLimiterLogin();
    private final Javalin app;

    public ApiServer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        this.app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
            config.staticFiles.add("/public", io.javalin.http.staticfiles.Location.CLASSPATH);
        });
        configurarErros();
        configurarSeguranca();
        configurarRotas();
    }

    public void start(int port) {
        app.start(port);
        System.out.println("[FlexStock] Servidor a correr em http://localhost:" + port);
    }

    public int port() { return app.port(); }

    public void stop() {
        app.stop();
    }

    public service.RateLimiterLogin getRateLimiterLogin() {
        return rateLimiterLogin;
    }

    private void configurarErros() {
        app.exception(service.VendaIdempotenteService.ChaveReutilizadaException.class,
            (e, ctx) -> ctx.status(HttpStatus.CONFLICT).json(Map.of("erro", e.getMessage(), "codigo", "CHAVE_REUTILIZADA")));
        app.exception(AutenticacaoException.class, (e, ctx) -> responderErro(ctx, HttpStatus.UNAUTHORIZED, e.getMessage()));
        app.exception(PermissaoException.class, (e, ctx) -> responderErro(ctx, HttpStatus.FORBIDDEN, e.getMessage()));
        app.exception(BarracaService.RecursoNaoEncontradoException.class,
            (e, ctx) -> responderErro(ctx, HttpStatus.NOT_FOUND, e.getMessage()));
        app.exception(BarracaService.ConflitoException.class,
            (e, ctx) -> responderErro(ctx, HttpStatus.CONFLICT, e.getMessage()));
        app.exception(IllegalArgumentException.class,
            (e, ctx) -> responderErro(ctx, HttpStatus.BAD_REQUEST, e.getMessage()));
        app.exception(IllegalStateException.class,
            (e, ctx) -> responderErro(ctx, HttpStatus.CONFLICT, e.getMessage()));
        app.exception(Exception.class,
            (e, ctx) -> {
                String errorId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                System.err.println("[ERRO INTERNO " + errorId + "] " + e.getMessage());
                e.printStackTrace();
                responderErro(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno. Tente novamente. Código: " + errorId);
            });
    }

    private void configurarSeguranca() {
        app.before(ctx -> {
            if (!ctx.path().startsWith("/api/") || rotaPublica(ctx.path())) {
                return;
            }
            autenticar(ctx);
            validarOrigem(ctx);
            // Injectar o ID do utilizador no contexto do thread para RLS.
            // Todas as queries à BD neste pedido serão filtradas por este utilizador.
            JPAUtil.setCurrentUser(utilizadorId(ctx));
        });

        // Limpar o contexto RLS após cada pedido, independentemente do resultado.
        // Evita que um ID de utilizador "vaze" para o próximo pedido no mesmo thread.
        app.after(ctx -> JPAUtil.clearCurrentUser());
    }

    private boolean rotaPublica(String path) {
        return "/api/health".equals(path) || "/api/auth/login".equals(path);
    }

    private void autenticar(Context ctx) {
        Sessao sessao = sessaoDAO.buscarActiva(ctx.cookie(SESSION_COOKIE));
        if (sessao == null) {
            throw new AutenticacaoException("Sessão inválida ou expirada.");
        }
        Usuario usuario = sessao.getUsuario();
        if (usuario.isExpirado()) {
            throw new PermissaoException("Conta expirada. Contacte o administrador.");
        }
        ctx.attribute(AUTH_ATTRIBUTE, new UtilizadorAutenticado(sessao.getId(), usuario));
    }

    private void validarOrigem(Context ctx) {
        if ("GET".equals(ctx.method().name()) || "HEAD".equals(ctx.method().name()) || "OPTIONS".equals(ctx.method().name())) {
            return;
        }
        String origin = ctx.header("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }
        String host = ctx.header("Host");
        boolean mesmaOrigem = host != null && (origin.equals("http://" + host) || origin.equals("https://" + host));
        String origemConfigurada = System.getenv("CORS_ALLOWED_ORIGIN");
        boolean origemDesenvolvimento = origemConfigurada != null && origin.equals(origemConfigurada.trim());
        if (!mesmaOrigem && !origemDesenvolvimento) {
            throw new PermissaoException("Origem não permitida.");
        }
    }

    private UtilizadorAutenticado utilizador(Context ctx) {
        UtilizadorAutenticado utilizador = ctx.attribute(AUTH_ATTRIBUTE);
        if (utilizador == null) {
            throw new AutenticacaoException("Sessão inválida ou expirada.");
        }
        return utilizador;
    }

    private Long utilizadorId(Context ctx) {
        return utilizador(ctx).usuario.getId();
    }

    private void exigirSuperuser(Context ctx) {
        if (!"superuser".equals(utilizador(ctx).usuario.getRole())) {
            throw new PermissaoException("Acesso negado.");
        }
    }

    private void configurarRotas() {
        app.get("/api/health", ctx -> ctx.json(Map.of("estado", "ok")));

        app.post("/api/auth/login", this::login);
        app.get("/api/auth/me", this::obterSessaoActual);
        app.post("/api/auth/logout", this::logout);

        app.get("/api/dashboard", this::getDashboard);

        app.get("/api/categorias", this::listarCategorias);
        app.get("/api/categorias/{id}/produtos", this::listarProdutosPorCategoria);
        app.post("/api/categorias", this::criarCategoria);
        app.put("/api/categorias/{id}", this::atualizarCategoria);
        app.delete("/api/categorias/{id}", this::deletarCategoria);

        app.get("/api/produtos", this::listarProdutos);
        app.get("/api/produtos/barcode/{codigo}", this::buscarPorCodigoBarras);
        app.post("/api/produtos", this::criarProduto);
        app.put("/api/produtos/{id}", this::atualizarProduto);
        app.delete("/api/produtos/{id}", this::deletarProduto);
        app.get("/api/produtos/{id}/movimentos", this::listarMovimentosProduto);

        app.post("/api/stock/entradas", this::registarEntradaStock);
        app.post("/api/stock/ajustes", this::registarAjusteStock);
        app.get("/api/stock/contagem/previa", this::previaContagem);
        app.post("/api/stock/contagem/previa", this::previaContagem);
        app.post("/api/stock/contagem", this::confirmarContagem);
        app.get("/api/stock/exportar", this::exportarStockCsv);
        app.get("/api/stock/movimentos", this::listarMovimentosStock);
        app.get("/api/stock/integridade/{id}", this::verificarIntegridadeStock);

        app.get("/api/vendas/exportar", this::exportarVendasCsv);
        app.get("/api/vendas", this::listarVendas);
        app.get("/api/vendas/{id}", this::obterVenda);
        app.post("/api/vendas", this::registarVenda);
        app.post("/api/vendas/lote", this::registarVendaLote);
        app.post("/api/vendas/{id}/anular", this::anularVenda);

        app.get("/api/metodos-pagamento", this::listarMetodosPagamento);
        app.post("/api/metodos-pagamento", this::criarMetodoPagamento);
        app.put("/api/metodos-pagamento/{id}/toggle", this::alternarMetodoPagamento);
        app.put("/api/metodos-pagamento/{id}", this::actualizarMetodoPagamento);
        app.delete("/api/metodos-pagamento/{id}", this::eliminarMetodoPagamento);

        app.get("/api/definicoes", this::obterDefinicoesLoja);
        app.put("/api/definicoes", this::atualizarDefinicoesLoja);

        app.get("/api/caixa/fecho", this::fechoCaixa);
        app.get("/api/caixa/atual", this::obterSessaoCaixaAtual);
        app.post("/api/caixa/abrir", this::abrirSessaoCaixa);
        app.post("/api/caixa/fechar", this::fecharSessaoCaixa);
        app.get("/api/caixa/historico", this::listarHistoricoCaixa);
        app.get("/api/caixa/exportar", this::exportarCaixaCsv);
        app.get("/api/caixa/despesas/exportar", this::exportarDespesasCaixaCsv);
        app.get("/api/caixa/despesas", this::listarDespesasCaixa);
        app.post("/api/caixa/despesas", this::registarDespesaCaixa);

        app.post("/api/vendas/{id}/devolver", this::devolverVenda);
        app.get("/api/vendas/{id}/devolucoes", this::listarDevolucoesVenda);
        app.get("/api/stock/falta-repor", this::obterProdutosFaltaRepor);

        app.get("/api/devedores", this::listarDevedores);
        app.post("/api/devedores", this::criarDevedor);
        app.delete("/api/devedores/{id}", this::deletarDevedor);
        app.post("/api/devedores/{id}/pagamentos", this::registarPagamentoDivida);
        app.get("/api/devedores/{id}/pagamentos", this::listarPagamentosDivida);

        app.get("/api/usuarios", this::listarUsuarios);
        app.post("/api/usuarios", this::criarUsuario);
        app.put("/api/usuarios/{id}", this::atualizarUsuario);
        app.post("/api/usuarios/{id}/alterar-senha", this::alterarSenha);
        app.delete("/api/usuarios/{id}", this::deletarUsuario);

        app.error(HttpStatus.NOT_FOUND, ctx -> {
            if (!ctx.path().startsWith("/api")) {
                ctx.result(getClass().getResourceAsStream("/public/index.html"));
                ctx.contentType("text/html");
            }
        });
    }

    private String obterIpCliente(Context ctx) {
        String xff = ctx.header("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return ctx.ip();
    }

    private void login(Context ctx) {
        String ip = obterIpCliente(ctx);
        Map<String, Object> body = corpo(ctx);
        String email = textoObrigatorio(body, "email").toLowerCase();
        String senha = textoObrigatorio(body, "password");

        String chaveIp = "ip:" + ip;
        String chaveEmail = "email:" + email;

        if (rateLimiterLogin.estaBloqueado(chaveIp) || rateLimiterLogin.estaBloqueado(chaveEmail)) {
            responderErro(ctx, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas tentativas. Tente novamente daqui a 10 minutos.");
            return;
        }

        Usuario usuario = usuarioDAO.buscarPorEmail(email);
        if (usuario == null || !usuario.verificarSenha(senha)) {
            rateLimiterLogin.registarFalha(chaveIp);
            rateLimiterLogin.registarFalha(chaveEmail);
            throw new AutenticacaoException("Email ou senha incorretos.");
        }
        if (usuario.isExpirado()) {
            throw new PermissaoException("Conta expirada. Contacte o administrador.");
        }
        rateLimiterLogin.limpar(chaveIp);
        rateLimiterLogin.limpar(chaveEmail);
        if (usuario.usaHashLegado()) {
            usuario.setSenha(senha);
            usuarioDAO.actualizar(usuario);
        }
        Sessao sessao = sessaoDAO.criar(usuario);
        definirCookieSessao(ctx, sessao.getId(), SESSION_MAX_AGE_SECONDS);
        ctx.json(utilizadorJson(usuario));
    }

    private void obterSessaoActual(Context ctx) {
        ctx.json(utilizadorJson(utilizador(ctx).usuario));
    }

    private void logout(Context ctx) {
        sessaoDAO.revogar(utilizador(ctx).sessaoId);
        definirCookieSessao(ctx, "", 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getDashboard(Context ctx) {
        Long uid = utilizadorId(ctx);
        LocalDate hoje = LocalDate.now();

        Map<LocalDate, BigDecimal> totais;
        try {
            totais = vendaDAO.totaisPorDia(hoje.minusDays(6), hoje, uid);
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar totaisPorDia: " + e.getMessage());
            totais = Collections.emptyMap();
        }

        List<Map<String, Object>> vendasPorDia = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dia", dia.format(DateTimeFormatter.ofPattern("dd/MM")));
            item.put("total", decimalJson(totais.getOrDefault(dia, BigDecimal.ZERO), 2));
            vendasPorDia.add(item);
        }

        List<Map<String, Object>> alertas;
        try {
            alertas = service.produtosComStockBaixo(uid).stream()
                .map(this::produtoStockJson).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar alertasStock: " + e.getMessage());
            alertas = Collections.emptyList();
        }

        List<Map<String, Object>> recentes;
        try {
            recentes = vendaDAO.recentes(uid, 10).stream()
                .map(this::vendaJson).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar vendasRecentes: " + e.getMessage());
            recentes = Collections.emptyList();
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        BigDecimal totalVendasHoje = totais.getOrDefault(hoje, BigDecimal.ZERO);

        Map<String, BigDecimal> lucros;
        try {
            lucros = vendaDAO.lucroDashboard(hoje, uid);
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar lucros: " + e.getMessage());
            lucros = Map.of("hoje", BigDecimal.ZERO, "7dias", BigDecimal.ZERO);
        }
        BigDecimal lucroHoje = lucros.getOrDefault("hoje", BigDecimal.ZERO);
        BigDecimal lucro7Dias = lucros.getOrDefault("7dias", BigDecimal.ZERO);

        BigDecimal valorTotalStockCusto;
        try {
            valorTotalStockCusto = stockService.calcularValorTotalStockCusto(uid);
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar valorTotalStockCusto: " + e.getMessage());
            valorTotalStockCusto = BigDecimal.ZERO;
        }
        if (valorTotalStockCusto == null) valorTotalStockCusto = BigDecimal.ZERO;

        dashboard.put("totalVendasHoje", decimalJson(totalVendasHoje, 2));
        dashboard.put("lucroHoje", decimalJson(lucroHoje, 2));
        dashboard.put("lucroUltimos7Dias", decimalJson(lucro7Dias, 2));
        dashboard.put("valorTotalStockCusto", decimalJson(valorTotalStockCusto, 2));
        try { dashboard.put("totalProdutos", service.totalProdutos(uid)); } catch (Exception e) { dashboard.put("totalProdutos", 0L); }
        try { dashboard.put("totalCategorias", categoriaDAO.contarTodos(uid)); } catch (Exception e) { dashboard.put("totalCategorias", 0L); }
        try { dashboard.put("totalDevedores", devedorDAO.contarTodos(uid)); } catch (Exception e) { dashboard.put("totalDevedores", 0L); }
        dashboard.put("alertasStock", alertas != null ? alertas : Collections.emptyList());

        Map<String, String> metodoFormatado = new LinkedHashMap<>();
        try {
            Map<String, BigDecimal> totaisMetodo = new dao.PagamentoVendaDAO().totaisPorMetodo(uid, hoje);
            if (totaisMetodo != null) {
                totaisMetodo.forEach((k, v) -> metodoFormatado.put(k, decimalJson(v != null ? v : BigDecimal.ZERO, 2)));
            }
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar vendasPorMetodo: " + e.getMessage());
        }
        dashboard.put("vendasPorMetodo", metodoFormatado);

        List<Map<String, Object>> maisVendidos = new ArrayList<>();
        try {
            List<Object[]> maisVendidosRaw = new dao.ItemVendaDAO().produtosMaisVendidos(uid, hoje.minusDays(6), hoje, 5);
            if (maisVendidosRaw != null) {
                for (Object[] r : maisVendidosRaw) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("produtoId", r[0]);
                    item.put("nome", r[1]);
                    item.put("quantidade", decimalJson(toBigDecimal(r[2]), 3));
                    item.put("quantidadeTotal", decimalJson(toBigDecimal(r[2]), 3));
                    item.put("total", decimalJson(toBigDecimal(r[3]), 2));
                    item.put("valorTotal", decimalJson(toBigDecimal(r[3]), 2));
                    maisVendidos.add(item);
                }
            }
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar produtosMaisVendidos: " + e.getMessage());
        }
        dashboard.put("produtosMaisVendidos", maisVendidos);

        dashboard.put("vendasRecentes", recentes != null ? recentes : Collections.emptyList());
        dashboard.put("vendasPorDia", vendasPorDia != null ? vendasPorDia : Collections.emptyList());
        dashboard.put("faltaReporCount", alertas != null ? alertas.size() : 0);

        BigDecimal perdasMes;
        try {
            perdasMes = service.calcularPerdasMesACusto(uid);
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar perdasMes: " + e.getMessage());
            perdasMes = BigDecimal.ZERO;
        }
        dashboard.put("perdasMes", decimalJson(perdasMes != null ? perdasMes : BigDecimal.ZERO, 2));

        ctx.json(dashboard);
    }

    private void listarCategorias(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<Long, Long> contagens = categoriaDAO.contarProdutosPorCategoria(uid);
        List<Map<String, Object>> resultado = service.listarCategorias(uid).stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("nome", c.getNome());
            item.put("descricao", c.getDescricao() == null ? "" : c.getDescricao());
            item.put("icone", "categoria");
            item.put("totalProdutos", contagens.getOrDefault(c.getId(), 0L));
            return item;
        }).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void listarProdutosPorCategoria(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long categoriaId = idPath(ctx);
        if (service.buscarCategoria(categoriaId, uid) == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        ctx.json(service.listarProdutosPorCategoria(categoriaId, uid).stream()
            .map(this::produtoJson).collect(Collectors.toList()));
    }

    private void criarCategoria(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        Categoria categoria = service.criarCategoria(textoObrigatorio(body, "nome"), texto(body, "descricao"), uid);
        ctx.status(HttpStatus.CREATED).json(categoriaJson(categoria, uid));
    }

    private void atualizarCategoria(Context ctx) {
        Long uid = utilizadorId(ctx);
        Categoria categoria = service.buscarCategoria(idPath(ctx), uid);
        if (categoria == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        Map<String, Object> body = corpo(ctx);
        categoria.setNome(textoObrigatorio(body, "nome"));
        categoria.setDescricao(texto(body, "descricao"));
        ctx.json(categoriaJson(service.actualizarCategoria(categoria, uid), uid));
    }

    private void deletarCategoria(Context ctx) {
        service.eliminarCategoria(idPath(ctx), utilizadorId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listarProdutos(Context ctx) {
        boolean incluirArquivados = "true".equalsIgnoreCase(ctx.queryParam("incluirArquivados"));
        if (ctx.queryParam("page") != null) {
            ctx.json(produtoDAO.listarPagina(utilizadorId(ctx), inteiroQuery(ctx, "page", 1), inteiroQuery(ctx, "pageSize", 25),
                ctx.queryParam("q"), longQuery(ctx, "categoriaId"), queryPadrao(ctx, "stock", "all"), queryPadrao(ctx, "sort", "name"), incluirArquivados)
                .map(this::produtoJson));
            return;
        }
        ctx.json(service.listarProdutos(utilizadorId(ctx), incluirArquivados).stream()
            .map(this::produtoJson).collect(Collectors.toList()));
    }

    private void exportarStockCsv(Context ctx) {
        Long uid = utilizadorId(ctx);
        byte[] bytes = service.exportarStockCsv(uid);
        String nomeFicheiro = "stock_inventario_" + LocalDate.now() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void criarProduto(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        BigDecimal custo = body.containsKey("custo") ? decimalObrigatorio(body, "custo") : BigDecimal.ZERO;
        BigDecimal stockMaximo = (body.containsKey("stockMaximo") && body.get("stockMaximo") != null && !body.get("stockMaximo").toString().isBlank())
            ? decimalObrigatorio(body, "stockMaximo") : null;
        Produto produto = service.criarProduto(
            textoObrigatorio(body, "nome"),
            decimalObrigatorio(body, "preco"),
            custo,
            decimalComPadrao(body, "stock", "0"),
            textoComPadrao(body, "unidade", "un"),
            decimalComPadrao(body, "stockMinimo", "5"),
            stockMaximo,
            longObrigatorio(body, "categoriaId"),
            texto(body, "codigoBarras"),
            uid
        );
        ctx.status(HttpStatus.CREATED).json(produtoJson(produto));
    }

    private void atualizarProduto(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        BigDecimal custo = body.containsKey("custo") ? decimalObrigatorio(body, "custo") : null;
        BigDecimal stock = body.containsKey("stock") ? decimalObrigatorio(body, "stock") : null;
        BigDecimal stockMaximo = (body.containsKey("stockMaximo") && body.get("stockMaximo") != null && !body.get("stockMaximo").toString().isBlank())
            ? decimalObrigatorio(body, "stockMaximo") : null;
        Produto produto = service.actualizarProduto(
            idPath(ctx),
            textoObrigatorio(body, "nome"),
            decimalObrigatorio(body, "preco"),
            custo,
            stock,
            textoComPadrao(body, "unidade", "un"),
            decimalComPadrao(body, "stockMinimo", "5"),
            stockMaximo,
            longObrigatorio(body, "categoriaId"),
            texto(body, "codigoBarras"),
            uid
        );
        ctx.json(produtoJson(produto));
    }

    private void buscarPorCodigoBarras(Context ctx) {
        Produto produto = service.buscarPorCodigoBarras(ctx.pathParam("codigo"), utilizadorId(ctx));
        if (produto == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
        }
        ctx.json(produtoJson(produto));
    }

    private void deletarProduto(Context ctx) {
        service.eliminarProduto(idPath(ctx), utilizadorId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listarVendas(Context ctx) {
        Long uid = utilizadorId(ctx);
        if (ctx.queryParam("page") != null) {
            int page = inteiroQuery(ctx, "page", 1);
            int pageSize = inteiroQuery(ctx, "pageSize", 25);
            String q = ctx.queryParam("q");
            LocalDate inicio = dataQuery(ctx, "inicio");
            LocalDate fim = dataQuery(ctx, "fim");
            String metodo = ctx.queryParam("metodo");
            String estado = ctx.queryParam("estado");
            ctx.json(vendaDAO.listarPagina(uid, page, pageSize, q, inicio, fim, metodo, estado).map(this::vendaJson));
            return;
        }
        ctx.json(service.listarVendas(uid).stream().map(this::vendaJson).collect(Collectors.toList()));
    }

    private void exportarVendasCsv(Context ctx) {
        Long uid = utilizadorId(ctx);
        LocalDate inicio = dataQuery(ctx, "inicio");
        LocalDate fim = dataQuery(ctx, "fim");
        String metodo = ctx.queryParam("metodo");
        String estado = ctx.queryParam("estado");
        String q = ctx.queryParam("q");
        byte[] bytes = service.exportarVendasCsv(inicio, fim, metodo, estado, q, uid);
        String nomeFicheiro = "vendas_" + LocalDate.now() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void obterVenda(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        Venda venda = service.buscarVendaPorId(id, uid);
        ctx.json(vendaJson(venda));
    }

    private void registarVenda(Context ctx) {
        Map<String, Object> body = corpo(ctx);
        Venda venda = service.registarVenda(
            longObrigatorio(body, "produtoId"), decimalObrigatorio(body, "quantidade"), texto(body, "observacao"), utilizadorId(ctx));
        ctx.status(HttpStatus.CREATED).json(vendaJson(venda));
    }

    @SuppressWarnings("unchecked")
    private void registarVendaLote(Context ctx) {
        Long uid = utilizadorId(ctx);
        String chave = ctx.header("Idempotency-Key");

        Object rawBody = ctx.bodyAsClass(Object.class);
        List<Map<String, Object>> itens = new ArrayList<>();
        List<Map<String, Object>> pagamentos = null;
        Long clienteId = null;
        String observacao = null;
        String uuidCliente = null;

        if (rawBody instanceof List) {
            for (Object o : (List<?>) rawBody) {
                if (o instanceof Map) itens.add((Map<String, Object>) o);
            }
        } else if (rawBody instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rawBody;
            if (map.containsKey("itens") && map.get("itens") instanceof List) {
                for (Object o : (List<?>) map.get("itens")) {
                    if (o instanceof Map) itens.add((Map<String, Object>) o);
                }
            }
            if (map.containsKey("pagamentos") && map.get("pagamentos") instanceof List) {
                pagamentos = new ArrayList<>();
                for (Object o : (List<?>) map.get("pagamentos")) {
                    if (o instanceof Map) pagamentos.add((Map<String, Object>) o);
                }
            }
            if (map.containsKey("clienteId") && map.get("clienteId") != null && !map.get("clienteId").toString().isBlank()) {
                clienteId = Long.valueOf(map.get("clienteId").toString());
            }
            if (map.containsKey("observacao") && map.get("observacao") != null) {
                observacao = map.get("observacao").toString();
            }
            if (map.containsKey("uuidCliente") && map.get("uuidCliente") != null && !map.get("uuidCliente").toString().isBlank()) {
                uuidCliente = map.get("uuidCliente").toString().trim();
            } else if (map.containsKey("uuid") && map.get("uuid") != null && !map.get("uuid").toString().isBlank()) {
                uuidCliente = map.get("uuid").toString().trim();
            }
        }

        if (itens.isEmpty()) {
            throw new IllegalArgumentException("Carrinho está vazio.");
        }

        if (chave != null) {
            service.VendaIdempotenteService.Resultado resultado =
                vendaIdempotente.registar(itens, pagamentos, clienteId, observacao, uuidCliente, uid, chave);
            ctx.header("Idempotency-Replayed", Boolean.toString(resultado.repetido));
            ctx.status(resultado.repetido ? HttpStatus.OK : HttpStatus.CREATED)
                .contentType("application/json")
                .result(resultado.json);
            return;
        }

        Map<String, Object> resultado = service.registarVendaLote(itens, pagamentos, clienteId, observacao, uuidCliente, uid);
        ctx.status(HttpStatus.CREATED).json(util.VendaJson.lote(resultado));
    }

    private Map<String, Object> metodoPagamentoJson(model.MetodoPagamento m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("nome", m.getNome());
        map.put("tipo", m.getTipo());
        map.put("ativo", m.getAtivo());
        map.put("ordem", m.getOrdem());
        return map;
    }

    private void listarMetodosPagamento(Context ctx) {
        Long uid = utilizadorId(ctx);
        boolean apenasAtivos = Boolean.parseBoolean(ctx.queryParam("apenasAtivos"));
        List<model.MetodoPagamento> metodos = apenasAtivos
                ? service.listarMetodosPagamentoAtivos(uid)
                : service.listarMetodosPagamento(uid);
        ctx.json(metodos.stream().map(this::metodoPagamentoJson).collect(Collectors.toList()));
    }

    private void criarMetodoPagamento(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        String nome = textoObrigatorio(body, "nome");
        String tipo = body.get("tipo") != null ? body.get("tipo").toString() : "DIGITAL";
        model.MetodoPagamento m = service.criarMetodoPagamento(nome, tipo, uid);
        ctx.status(HttpStatus.CREATED).json(metodoPagamentoJson(m));
    }

    private void alternarMetodoPagamento(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        model.MetodoPagamento m = service.alternarMetodoPagamento(id, uid);
        ctx.json(metodoPagamentoJson(m));
    }

    private void actualizarMetodoPagamento(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        Map<String, Object> body = corpo(ctx);
        String nome = body.get("nome") != null ? body.get("nome").toString() : null;
        Boolean ativo = body.get("ativo") != null ? Boolean.valueOf(body.get("ativo").toString()) : null;
        Integer ordem = body.get("ordem") != null ? Integer.valueOf(body.get("ordem").toString()) : null;
        model.MetodoPagamento m = service.actualizarMetodoPagamento(id, nome, ativo, ordem, uid);
        ctx.json(metodoPagamentoJson(m));
    }

    private void eliminarMetodoPagamento(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        service.eliminarMetodoPagamento(id, uid);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void anularVenda(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        Map<String, Object> body = corpo(ctx);
        String motivo = textoObrigatorio(body, "motivo");
        Venda venda = service.anularVenda(id, motivo, uid);
        ctx.json(vendaJson(venda));
    }

    private void fechoCaixa(Context ctx) {
        Long uid = utilizadorId(ctx);
        LocalDate data = dataQuery(ctx, "data");
        if (data == null) data = LocalDate.now();
        ctx.json(service.fechoCaixa(data, uid));
    }

    private void obterDefinicoesLoja(Context ctx) {
        Long uid = utilizadorId(ctx);
        DefinicaoLoja def = service.obterDefinicoesLoja(uid);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("controloCaixa", def.getControloCaixa());
        res.put("nomeLoja", def.getNomeLoja() != null ? def.getNomeLoja() : "");
        ctx.json(res);
    }

    private void atualizarDefinicoesLoja(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        boolean controlo = Boolean.TRUE.equals(body.get("controloCaixa"));
        String nome = texto(body, "nomeLoja");
        DefinicaoLoja def = service.atualizarDefinicoesLoja(controlo, nome, uid);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("controloCaixa", def.getControloCaixa());
        res.put("nomeLoja", def.getNomeLoja() != null ? def.getNomeLoja() : "");
        ctx.json(res);
    }

    private void obterSessaoCaixaAtual(Context ctx) {
        Long uid = utilizadorId(ctx);
        ctx.json(service.obterResumoSessaoAtual(uid));
    }

    private void abrirSessaoCaixa(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long opId = utilizador(ctx).usuario.getId();
        Map<String, Object> body = corpo(ctx);
        BigDecimal inicial = decimalComPadrao(body, "valorInicial", "0");
        String nota = texto(body, "notaAbertura");
        SessaoCaixa sessao = service.abrirSessaoCaixa(inicial, nota, opId, uid);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", sessao.getId());
        res.put("abertaEm", sessao.getAbertaEm().toString());
        res.put("valorInicial", decimalJson(sessao.getValorInicial(), 2));
        res.put("estado", sessao.getEstado());
        ctx.status(HttpStatus.CREATED).json(res);
    }

    private void fecharSessaoCaixa(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long opId = utilizador(ctx).usuario.getId();
        Map<String, Object> body = corpo(ctx);
        Long sessaoId = body.containsKey("sessaoId") && body.get("sessaoId") != null ? longObrigatorio(body, "sessaoId") : null;
        if (sessaoId == null) {
            SessaoCaixa aberta = service.buscarSessaoAberta(uid);
            if (aberta == null) throw new BarracaService.RecursoNaoEncontradoException("Não há sessão de caixa aberta.");
            sessaoId = aberta.getId();
        }
        BigDecimal contado = decimalComPadrao(body, "valorContado", "0");
        String nota = texto(body, "notaFecho");
        SessaoCaixa sessao = service.fecharSessaoCaixa(sessaoId, contado, nota, opId, uid);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", sessao.getId());
        res.put("abertaEm", sessao.getAbertaEm().toString());
        res.put("fechadaEm", sessao.getFechadaEm() != null ? sessao.getFechadaEm().toString() : "");
        res.put("valorInicial", decimalJson(sessao.getValorInicial(), 2));
        res.put("valorEsperado", decimalJson(sessao.getValorEsperado(), 2));
        res.put("valorContado", decimalJson(sessao.getValorContado(), 2));
        res.put("diferenca", decimalJson(sessao.getDiferenca(), 2));
        res.put("notaFecho", sessao.getNotaFecho() != null ? sessao.getNotaFecho() : "");
        res.put("estado", sessao.getEstado());
        ctx.json(res);
    }

    private void listarHistoricoCaixa(Context ctx) {
        Long uid = utilizadorId(ctx);
        int limite = 50;
        String limStr = ctx.queryParam("limite");
        if (limStr != null && !limStr.isBlank()) {
            try { limite = Integer.parseInt(limStr); } catch (NumberFormatException ignored) {}
        }
        List<SessaoCaixa> sessoes = service.listarHistoricoSessoes(uid, limite);
        List<Map<String, Object>> res = new ArrayList<>();
        for (SessaoCaixa s : sessoes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("abertaEm", s.getAbertaEm().toString());
            m.put("fechadaEm", s.getFechadaEm() != null ? s.getFechadaEm().toString() : null);
            m.put("valorInicial", decimalJson(s.getValorInicial(), 2));
            m.put("valorEsperado", s.getValorEsperado() != null ? decimalJson(s.getValorEsperado(), 2) : null);
            m.put("valorContado", s.getValorContado() != null ? decimalJson(s.getValorContado(), 2) : null);
            m.put("diferenca", s.getDiferenca() != null ? decimalJson(s.getDiferenca(), 2) : null);
            m.put("notaAbertura", s.getNotaAbertura() != null ? s.getNotaAbertura() : "");
            m.put("notaFecho", s.getNotaFecho() != null ? s.getNotaFecho() : "");
            m.put("estado", s.getEstado());
            res.add(m);
        }
        ctx.json(res);
    }

    private void exportarCaixaCsv(Context ctx) {
        Long uid = utilizadorId(ctx);
        int limite = inteiroQuery(ctx, "limite", 200);
        byte[] bytes = service.exportarCaixaCsv(uid, limite);
        String nomeFicheiro = "fechos_caixa_" + LocalDate.now() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void exportarDespesasCaixaCsv(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long sessaoId = longQuery(ctx, "sessaoId");
        byte[] bytes = service.exportarDespesasCaixaCsv(sessaoId, uid);
        String nomeFicheiro = "despesas_caixa_" + LocalDate.now() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void listarDespesasCaixa(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long sessaoId = longQuery(ctx, "sessaoId");
        ctx.json(service.listarDespesasCaixa(sessaoId, uid));
    }

    private void registarDespesaCaixa(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long opId = utilizador(ctx).usuario.getId();
        Map<String, Object> body = corpo(ctx);
        BigDecimal valor = decimalObrigatorio(body, "valor");
        String categoria = texto(body, "categoria");
        String descricao = textoObrigatorio(body, "descricao");
        Map<String, Object> res = service.registarDespesaCaixa(valor, categoria, descricao, opId, uid);
        ctx.status(HttpStatus.CREATED).json(res);
    }

    @SuppressWarnings("unchecked")
    private void devolverVenda(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long opId = utilizador(ctx).usuario.getId();
        Long vendaId = idPath(ctx);
        Map<String, Object> body = corpo(ctx);
        String motivo = texto(body, "motivo");
        String uuidCliente = texto(body, "uuidCliente");
        if (uuidCliente == null || uuidCliente.isBlank()) {
            uuidCliente = ctx.header("Idempotency-Key");
        }
        List<Map<String, Object>> itensRaw = (List<Map<String, Object>>) body.get("itens");
        if (itensRaw == null || itensRaw.isEmpty()) {
            throw new IllegalArgumentException("Lista de itens a devolver está vazia.");
        }
        List<BarracaService.DevolucaoItemParam> params = new ArrayList<>();
        for (Map<String, Object> item : itensRaw) {
            Long itemVendaId = longObrigatorio(item, "itemVendaId");
            BigDecimal qtd = decimalObrigatorio(item, "quantidade");
            params.add(new BarracaService.DevolucaoItemParam(itemVendaId, qtd));
        }
        Map<String, Object> devolucao = service.processarDevolucao(vendaId, params, motivo, uuidCliente, opId, uid);
        ctx.status(HttpStatus.CREATED).json(devolucao);
    }

    private void listarDevolucoesVenda(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long vendaId = idPath(ctx);
        ctx.json(service.listarDevolucoesVenda(vendaId, uid));
    }

    private void obterProdutosFaltaRepor(Context ctx) {
        Long uid = utilizadorId(ctx);
        ctx.json(service.obterProdutosFaltaRepor(uid));
    }

    @SuppressWarnings("unchecked")
    private void registarEntradaStock(Context ctx) {
        Long uid = utilizadorId(ctx);
        Object rawBody = ctx.bodyAsClass(Object.class);
        List<Map<String, Object>> itens = new ArrayList<>();
        String motivoGeral = "Entrada de stock";

        if (rawBody instanceof List) {
            for (Object obj : (List<?>) rawBody) {
                if (obj instanceof Map) itens.add((Map<String, Object>) obj);
            }
        } else if (rawBody instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rawBody;
            if (map.containsKey("itens") && map.get("itens") instanceof List) {
                if (map.containsKey("motivo") && map.get("motivo") != null) motivoGeral = map.get("motivo").toString();
                for (Object obj : (List<?>) map.get("itens")) {
                    if (obj instanceof Map) itens.add((Map<String, Object>) obj);
                }
            } else {
                itens.add(map);
            }
        }

        if (itens.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para entrada de stock.");
        }

        List<model.MovimentoStock> movimentos = stockService.registarEntradaLote(uid, itens, motivoGeral, uid);
        ctx.status(HttpStatus.CREATED).json(movimentos.stream().map(this::movimentoJson).collect(Collectors.toList()));
    }

    private void registarAjusteStock(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        Long produtoId = longObrigatorio(body, "produtoId");
        BigDecimal novaQtd = body.containsKey("novaQuantidade") && body.get("novaQuantidade") != null
                ? decimalObrigatorio(body, "novaQuantidade") : null;
        BigDecimal diferenca = body.containsKey("diferenca") && body.get("diferenca") != null
                ? decimalObrigatorio(body, "diferenca") : null;
        String tipo = body.containsKey("tipo") && body.get("tipo") != null ? body.get("tipo").toString() : null;
        String motivo = textoObrigatorio(body, "motivo");

        model.MovimentoStock mov = stockService.registarAjuste(uid, produtoId, novaQtd, diferenca, tipo, motivo, uid);
        if (mov == null) {
            ctx.status(HttpStatus.OK).json(Map.of("mensagem", "Sem alteração de stock"));
        } else {
            ctx.status(HttpStatus.CREATED).json(movimentoJson(mov));
        }
    }

    @SuppressWarnings("unchecked")
    private void previaContagem(Context ctx) {
        Long uid = utilizadorId(ctx);
        List<Map<String, Object>> contagens = new ArrayList<>();
        if ("POST".equalsIgnoreCase(ctx.method().name()) || (ctx.body() != null && !ctx.body().isBlank())) {
            Object raw = ctx.bodyAsClass(Object.class);
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) {
                    if (o instanceof Map) contagens.add((Map<String, Object>) o);
                }
            } else if (raw instanceof Map && ((Map<?, ?>) raw).containsKey("itens")) {
                for (Object o : (List<?>) ((Map<?, ?>) raw).get("itens")) {
                    if (o instanceof Map) contagens.add((Map<String, Object>) o);
                }
            }
        }
        ctx.json(stockService.previaContagem(uid, contagens));
    }

    @SuppressWarnings("unchecked")
    private void confirmarContagem(Context ctx) {
        Long uid = utilizadorId(ctx);
        Object raw = ctx.bodyAsClass(Object.class);
        List<Map<String, Object>> contagens = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map) contagens.add((Map<String, Object>) o);
            }
        } else if (raw instanceof Map && ((Map<?, ?>) raw).containsKey("itens")) {
            for (Object o : (List<?>) ((Map<?, ?>) raw).get("itens")) {
                if (o instanceof Map) contagens.add((Map<String, Object>) o);
            }
        }
        List<model.MovimentoStock> movs = stockService.confirmarContagem(uid, contagens, uid);
        ctx.status(HttpStatus.CREATED).json(movs.stream().map(this::movimentoJson).collect(Collectors.toList()));
    }

    private void listarMovimentosProduto(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long produtoId = idPath(ctx);
        int page = inteiroQuery(ctx, "page", 1);
        int pageSize = inteiroQuery(ctx, "pageSize", 25);
        ctx.json(stockService.listarMovimentosPorProduto(uid, produtoId, page, pageSize).map(this::movimentoJson));
    }

    private void listarMovimentosStock(Context ctx) {
        Long uid = utilizadorId(ctx);
        int page = inteiroQuery(ctx, "page", 1);
        int pageSize = inteiroQuery(ctx, "pageSize", 25);
        String tipo = ctx.queryParam("tipo");
        LocalDate inicioData = dataQuery(ctx, "inicio");
        LocalDate fimData = dataQuery(ctx, "fim");
        java.time.LocalDateTime inicio = inicioData != null ? inicioData.atStartOfDay() : null;
        java.time.LocalDateTime fim = fimData != null ? fimData.atTime(23, 59, 59) : null;
        ctx.json(stockService.listarMovimentos(uid, page, pageSize, tipo, inicio, fim).map(this::movimentoJson));
    }

    private void verificarIntegridadeStock(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long produtoId = idPath(ctx);
        boolean integro = stockService.verificarIntegridadeStock(uid, produtoId);
        ctx.json(Map.of("produtoId", produtoId, "integro", integro));
    }

    private Map<String, Object> movimentoJson(model.MovimentoStock m) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", m.getId());
        res.put("produtoId", m.getProduto() != null ? m.getProduto().getId() : null);
        res.put("produtoNome", m.getProduto() != null ? m.getProduto().getNome() : "");
        res.put("tipo", m.getTipo());
        res.put("quantidade", decimalJson(m.getQuantidade(), 3));
        res.put("custoUnitario", decimalJson(m.getCustoUnitario(), 2));
        res.put("motivo", m.getMotivo() == null ? "" : m.getMotivo());
        res.put("referenciaTipo", m.getReferenciaTipo());
        res.put("referenciaId", m.getReferenciaId());
        res.put("criadoEm", m.getCriadoEm() != null ? m.getCriadoEm().toString() : null);
        return res;
    }

    private void listarDevedores(Context ctx) {
        Long uid = utilizadorId(ctx);
        dao.PagamentoDividaDAO pagDAO = new dao.PagamentoDividaDAO();
        List<Map<String, Object>> resultado = devedorDAO.listarTodos(uid).stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("nome", d.getNome());
            item.put("divida", decimalJson(d.getDivida(), 2));
            BigDecimal pagos = pagDAO.totalPagoPorDevedor(uid, d.getId());
            BigDecimal saldo = d.getDivida().subtract(pagos);
            if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;
            item.put("saldo", decimalJson(saldo, 2));
            item.put("totalPago", decimalJson(pagos, 2));
            item.put("descricao", d.getDescricao() == null ? "" : d.getDescricao());
            item.put("data", d.getData() != null ? d.getData().format(DATA_FORMATADA) : "");
            item.put("vendaId", d.getVendaId());
            return item;
        }).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void criarDevedor(Context ctx) {
        Map<String, Object> body = corpo(ctx);
        BigDecimal divida = dinheiro(decimalObrigatorio(body, "divida"));
        if (divida.signum() <= 0) {
            throw new IllegalArgumentException("A dívida deve ser maior que zero.");
        }
        Devedor devedor = new Devedor();
        devedor.setNome(textoObrigatorio(body, "nome"));
        devedor.setDivida(divida);
        devedor.setDescricao(texto(body, "descricao"));
        devedor.setData(LocalDate.now());
        devedor.setUsuarioId(utilizadorId(ctx));
        devedor = devedorDAO.salvar(devedor);
        ctx.status(HttpStatus.CREATED).json(Map.of("id", devedor.getId(), "saldo", decimalJson(divida, 2)));
    }

    private void registarPagamentoDivida(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        Map<String, Object> body = corpo(ctx);
        BigDecimal valor = decimalObrigatorio(body, "valor");
        String metodo = textoObrigatorio(body, "metodo");
        String observacao = texto(body, "observacao");
        Map<String, Object> resultado = service.registarPagamentoDivida(id, valor, metodo, observacao, uid);
        ctx.status(HttpStatus.CREATED).json(resultado);
    }

    private void listarPagamentosDivida(Context ctx) {
        Long uid = utilizadorId(ctx);
        Long id = idPath(ctx);
        List<model.PagamentoDivida> pagamentos = new dao.PagamentoDividaDAO().listarPorDevedor(uid, id);
        List<Map<String, Object>> resultado = pagamentos.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("valor", decimalJson(p.getValor(), 2));
            item.put("metodo", p.getMetodo());
            item.put("criadoEm", p.getCriadoEm() != null ? p.getCriadoEm().toString() : "");
            item.put("observacao", p.getObservacao() == null ? "" : p.getObservacao());
            return item;
        }).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void deletarDevedor(Context ctx) {
        try {
            if (!devedorDAO.deletar(idPath(ctx), utilizadorId(ctx))) {
                throw new BarracaService.RecursoNaoEncontradoException("Dívida não encontrada.");
            }
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalStateException e) {
            throw new BarracaService.ConflitoException(e.getMessage());
        }
    }

    private void listarUsuarios(Context ctx) {
        exigirSuperuser(ctx);
        List<Map<String, Object>> resultado = usuarioDAO.listarTodos().stream().map(this::utilizadorJson).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void criarUsuario(Context ctx) {
        exigirSuperuser(ctx);
        Map<String, Object> body = corpo(ctx);
        String email = textoObrigatorio(body, "email").toLowerCase();
        if (usuarioDAO.buscarPorEmail(email) != null) {
            throw new BarracaService.ConflitoException("Já existe um utilizador com este email.");
        }
        String role = textoComPadrao(body, "role", "operator");
        validarRole(role);
        Usuario usuario = new Usuario(textoObrigatorio(body, "nome"), email, textoObrigatorio(body, "password"), role);
        if (!"superuser".equals(role)) {
            usuario.aplicarDiasAcesso(intComPadrao(body, "diasAcesso", 30));
        }
        usuario = usuarioDAO.salvar(usuario);
        ctx.status(HttpStatus.CREATED).json(utilizadorJson(usuario));
    }

    private void atualizarUsuario(Context ctx) {
        exigirSuperuser(ctx);
        Map<String, Object> body = corpo(ctx);
        Usuario usuario = usuarioDAO.buscarPorId(idPath(ctx));
        if (usuario == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Utilizador não encontrado.");
        }
        if (body.containsKey("nome")) usuario.setNome(textoObrigatorio(body, "nome"));
        if (body.containsKey("email")) {
            String email = textoObrigatorio(body, "email").toLowerCase();
            Usuario existente = usuarioDAO.buscarPorEmail(email);
            if (existente != null && !existente.getId().equals(usuario.getId())) {
                throw new BarracaService.ConflitoException("Já existe um utilizador com este email.");
            }
            usuario.setEmail(email);
        }
        if (body.containsKey("role")) {
            String role = textoObrigatorio(body, "role");
            validarRole(role);
            usuario.setRole(role);
        }
        if (body.containsKey("password") && texto(body, "password") != null && !texto(body, "password").isBlank()) {
            usuario.setSenha(texto(body, "password"));
            sessaoDAO.revogarDoUtilizador(usuario.getId());
        }
        if (body.containsKey("diasAcesso") && !"superuser".equals(usuario.getRole())) {
            usuario.aplicarDiasAcesso(intComPadrao(body, "diasAcesso", 30));
        }
        ctx.json(utilizadorJson(usuarioDAO.actualizar(usuario)));
    }

    private void alterarSenha(Context ctx) {
        Long alvoId = idPath(ctx);
        UtilizadorAutenticado actual = utilizador(ctx);
        if (!"superuser".equals(actual.usuario.getRole()) && !actual.usuario.getId().equals(alvoId)) {
            throw new PermissaoException("Acesso negado.");
        }
        Map<String, Object> body = corpo(ctx);
        Usuario usuario = usuarioDAO.buscarPorId(alvoId);
        if (usuario == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Utilizador não encontrado.");
        }
        if (!"superuser".equals(actual.usuario.getRole())) {
            String senhaAtual = textoObrigatorio(body, "senhaAtual");
            if (!usuario.verificarSenha(senhaAtual)) {
                throw new AutenticacaoException("Senha atual incorreta.");
            }
        }
        usuario.setSenha(textoObrigatorio(body, "novaSenha"));
        usuarioDAO.actualizar(usuario);
        sessaoDAO.revogarDoUtilizador(alvoId);
        definirCookieSessao(ctx, "", 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deletarUsuario(Context ctx) {
        exigirSuperuser(ctx);
        Long id = idPath(ctx);
        if (id.equals(utilizadorId(ctx))) {
            throw new IllegalArgumentException("Não é possível apagar a própria conta.");
        }
        if (usuarioDAO.contarTodos() <= 1) {
            throw new IllegalStateException("Não é possível apagar o único utilizador.");
        }
        if (usuarioDAO.buscarPorId(id) == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Utilizador não encontrado.");
        }
        sessaoDAO.revogarDoUtilizador(id);
        usuarioDAO.eliminar(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Map<String, Object> categoriaJson(Categoria categoria, Long usuarioId) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", categoria.getId());
        resultado.put("nome", categoria.getNome());
        resultado.put("descricao", categoria.getDescricao() == null ? "" : categoria.getDescricao());
        resultado.put("icone", "categoria");
        resultado.put("totalProdutos", categoriaDAO.contarProdutos(categoria.getId(), usuarioId));
        return resultado;
    }

    private Map<String, Object> produtoJson(Produto produto) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", produto.getId());
        resultado.put("nome", produto.getNome());
        resultado.put("preco", decimalJson(produto.getPreco(), 2));
        BigDecimal custo = produto.getCusto() != null ? produto.getCusto() : BigDecimal.ZERO;
        resultado.put("custo", decimalJson(custo, 2));
        BigDecimal margem = BigDecimal.ZERO;
        if (produto.getPreco() != null && produto.getPreco().compareTo(BigDecimal.ZERO) > 0) {
            margem = produto.getPreco().subtract(custo)
                .divide(produto.getPreco(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
        }
        resultado.put("margem", decimalJson(margem, 2));
        resultado.put("ativo", produto.isAtivo());
        resultado.put("stock", decimalJson(produto.getQuantidadeStock(), 3));
        resultado.put("stockMinimo", decimalJson(produto.getStockMinimo(), 3));
        resultado.put("stockMaximo", produto.getStockMaximo() != null ? decimalJson(produto.getStockMaximo(), 3) : null);
        resultado.put("unidade", produto.getUnidade());
        resultado.put("codigoBarras", produto.getCodigoBarras());
        resultado.put("categoriaId", produto.getCategoria() == null ? null : produto.getCategoria().getId());
        resultado.put("categoriaNome", produto.getCategoria() == null ? "" : produto.getCategoria().getNome());
        return resultado;
    }

    private Map<String, Object> produtoStockJson(Produto produto) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", produto.getId());
        resultado.put("nome", produto.getNome());
        resultado.put("stock", decimalJson(produto.getQuantidadeStock(), 3));
        resultado.put("stockMinimo", decimalJson(produto.getStockMinimo(), 3));
        resultado.put("stockMaximo", produto.getStockMaximo() != null ? decimalJson(produto.getStockMaximo(), 3) : null);
        return resultado;
    }

    private Map<String, Object> vendaJson(Venda venda) {
        return util.VendaJson.venda(venda);
    }

    private Map<String, Object> utilizadorJson(Usuario usuario) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", usuario.getId());
        resultado.put("nome", usuario.getNome());
        resultado.put("email", usuario.getEmail());
        resultado.put("role", usuario.getRole());
        resultado.put("diasAcesso", usuario.getDiasAcesso());
        resultado.put("dataExpiracao", usuario.getDataExpiracao() == null ? null : usuario.getDataExpiracao().format(DATA_FORMATADA));
        resultado.put("diasRestantes", usuario.diasRestantes());
        resultado.put("expirado", usuario.isExpirado());
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> corpo(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        if (body == null) {
            throw new IllegalArgumentException("Corpo do pedido inválido.");
        }
        return body;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itensVenda(Context ctx) {
        List<?> raw = ctx.bodyAsClass(List.class);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Carrinho está vazio.");
        }
        List<Map<String, Object>> itens = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Cada item da venda deve ser um objecto.");
            }
            itens.add((Map<String, Object>) item);
        }
        return itens;
    }

    private String queryPadrao(Context ctx, String campo, String padrao) {
        String valor = ctx.queryParam(campo);
        return valor == null ? padrao : valor;
    }

    private int inteiroQuery(Context ctx, String campo, int padrao) {
        try { return Integer.parseInt(queryPadrao(ctx, campo, Integer.toString(padrao))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Parâmetro " + campo + " inválido."); }
    }

    private Long longQuery(Context ctx, String campo) {
        String valor = ctx.queryParam(campo);
        if (valor == null || valor.isBlank()) return null;
        try {
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Parâmetro " + campo + " inválido."); }
    }

    private LocalDate dataQuery(Context ctx, String campo) {
        String valor = ctx.queryParam(campo);
        if (valor == null || valor.isBlank()) return null;
        try { return LocalDate.parse(valor); }
        catch (java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Use uma data válida no formato AAAA-MM-DD."); }
    }

    private Long idPath(Context ctx) {
        try {
            return Long.valueOf(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Identificador inválido.");
        }
    }

    private Long longObrigatorio(Map<String, Object> body, String campo) {
        Object value = body.get(campo);
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um identificador válido.");
        }
    }

    private BigDecimal decimalObrigatorio(Map<String, Object> body, String campo) {
        return BarracaService.decimal(body.get(campo), campo);
    }

    private BigDecimal decimalComPadrao(Map<String, Object> body, String campo, String padrao) {
        return body.get(campo) == null ? new BigDecimal(padrao) : decimalObrigatorio(body, campo);
    }

    private int intComPadrao(Map<String, Object> body, String campo, int padrao) {
        Object value = body.get(campo);
        if (value == null) return padrao;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser inteiro.");
        }
    }

    private String textoObrigatorio(Map<String, Object> body, String campo) {
        String value = texto(body, campo);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        return value.trim();
    }

    private String textoComPadrao(Map<String, Object> body, String campo, String padrao) {
        String value = texto(body, campo);
        return value == null || value.isBlank() ? padrao : value;
    }

    private String texto(Map<String, Object> body, String campo) {
        Object value = body.get(campo);
        if (value == null) return null;
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser texto.");
        }
        return (String) value;
    }

    private BigDecimal dinheiro(BigDecimal valor) {
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("Os valores monetários aceitam no máximo duas casas decimais.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String decimalJson(BigDecimal valor, int escala) {
        return valor == null ? null : valor.setScale(escala, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof Number) return new BigDecimal(val.toString());
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void validarRole(String role) {
        if (!"superuser".equals(role) && !"operator".equals(role)) {
            throw new IllegalArgumentException("Perfil inválido.");
        }
    }

    private void definirCookieSessao(Context ctx, String valor, int maxAge) {
        StringBuilder cookie = new StringBuilder(SESSION_COOKIE + "=" + valor + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge);
        if (Boolean.parseBoolean(System.getenv("SESSION_COOKIE_SECURE"))) {
            cookie.append("; Secure");
        }
        ctx.header("Set-Cookie", cookie.toString());
    }

    private void responderErro(Context ctx, HttpStatus status, String mensagem) {
        ctx.status(status).json(Map.of("erro", mensagem == null || mensagem.isBlank() ? "Pedido inválido." : mensagem));
    }

    private static final class UtilizadorAutenticado {
        private final String sessaoId;
        private final Usuario usuario;

        private UtilizadorAutenticado(String sessaoId, Usuario usuario) {
            this.sessaoId = sessaoId;
            this.usuario = usuario;
        }
    }

    private static final class AutenticacaoException extends RuntimeException {
        private AutenticacaoException(String message) { super(message); }
    }

    private static final class PermissaoException extends RuntimeException {
        private PermissaoException(String message) { super(message); }
    }
}
