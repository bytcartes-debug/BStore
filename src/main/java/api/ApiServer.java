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
import model.Devedor;
import model.Produto;
import model.Sessao;
import model.Usuario;
import model.Venda;
import service.BarracaService;
import util.JPAUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            (e, ctx) -> responderErro(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno."));
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

        app.get("/api/vendas", this::listarVendas);
        app.post("/api/vendas", this::registarVenda);
        app.post("/api/vendas/lote", this::registarVendaLote);

        app.get("/api/devedores", this::listarDevedores);
        app.post("/api/devedores", this::criarDevedor);
        app.delete("/api/devedores/{id}", this::deletarDevedor);

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

    private void login(Context ctx) {
        Map<String, Object> body = corpo(ctx);
        String email = textoObrigatorio(body, "email").toLowerCase();
        String senha = textoObrigatorio(body, "password");
        Usuario usuario = usuarioDAO.buscarPorEmail(email);
        if (usuario == null || !usuario.verificarSenha(senha)) {
            throw new AutenticacaoException("Email ou senha incorretos.");
        }
        if (usuario.isExpirado()) {
            throw new PermissaoException("Conta expirada. Contacte o administrador.");
        }
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
        Map<LocalDate, BigDecimal> totais = vendaDAO.totaisPorDia(hoje.minusDays(6), hoje, uid);
        List<Map<String, Object>> vendasPorDia = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dia", dia.format(DateTimeFormatter.ofPattern("dd/MM")));
            item.put("total", decimalJson(totais.getOrDefault(dia, BigDecimal.ZERO), 2));
            vendasPorDia.add(item);
        }
        List<Map<String, Object>> alertas = service.produtosComStockBaixo(uid).stream()
            .map(this::produtoStockJson).collect(Collectors.toList());
        List<Map<String, Object>> recentes = vendaDAO.recentes(uid, 10).stream()
            .map(this::vendaJson).collect(Collectors.toList());

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalVendasHoje", decimalJson(totais.getOrDefault(hoje, BigDecimal.ZERO), 2));
        dashboard.put("totalProdutos", service.totalProdutos(uid));
        dashboard.put("totalCategorias", categoriaDAO.contarTodos(uid));
        dashboard.put("totalDevedores", devedorDAO.contarTodos(uid));
        dashboard.put("alertasStock", alertas);
        dashboard.put("vendasRecentes", recentes);
        dashboard.put("vendasPorDia", vendasPorDia);
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
        if (ctx.queryParam("page") != null) {
            ctx.json(produtoDAO.listarPagina(utilizadorId(ctx), inteiroQuery(ctx, "page", 1), inteiroQuery(ctx, "pageSize", 25),
                ctx.queryParam("q"), longQuery(ctx, "categoriaId"), queryPadrao(ctx, "stock", "all"), queryPadrao(ctx, "sort", "name"))
                .map(this::produtoJson));
            return;
        }
        ctx.json(service.listarProdutos(utilizadorId(ctx)).stream()
            .map(this::produtoJson).collect(Collectors.toList()));
    }

    private void criarProduto(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        Produto produto = service.criarProduto(
            textoObrigatorio(body, "nome"),
            decimalObrigatorio(body, "preco"),
            decimalComPadrao(body, "stock", "0"),
            textoComPadrao(body, "unidade", "un"),
            decimalComPadrao(body, "stockMinimo", "5"),
            longObrigatorio(body, "categoriaId"),
            texto(body, "codigoBarras"),
            uid
        );
        ctx.status(HttpStatus.CREATED).json(produtoJson(produto));
    }

    private void atualizarProduto(Context ctx) {
        Long uid = utilizadorId(ctx);
        Map<String, Object> body = corpo(ctx);
        Produto produto = service.actualizarProduto(
            idPath(ctx),
            textoObrigatorio(body, "nome"),
            decimalObrigatorio(body, "preco"),
            decimalComPadrao(body, "stock", "0"),
            textoComPadrao(body, "unidade", "un"),
            decimalComPadrao(body, "stockMinimo", "5"),
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
        if (ctx.queryParam("page") != null) {
            ctx.json(vendaDAO.listarPagina(utilizadorId(ctx), inteiroQuery(ctx, "page", 1), inteiroQuery(ctx, "pageSize", 25),
                ctx.queryParam("q"), dataQuery(ctx, "inicio"), dataQuery(ctx, "fim")).map(this::vendaJson));
            return;
        }
        ctx.json(service.listarVendas(utilizadorId(ctx)).stream().map(this::vendaJson).collect(Collectors.toList()));
    }

    private void registarVenda(Context ctx) {
        Map<String, Object> body = corpo(ctx);
        Venda venda = service.registarVenda(
            longObrigatorio(body, "produtoId"), decimalObrigatorio(body, "quantidade"), texto(body, "observacao"), utilizadorId(ctx));
        ctx.status(HttpStatus.CREATED).json(vendaJson(venda));
    }

    private void registarVendaLote(Context ctx) {
        List<Map<String, Object>> itens = itensVenda(ctx);
        String chave = ctx.header("Idempotency-Key");
        if (chave != null) {
            service.VendaIdempotenteService.Resultado resultado = vendaIdempotente.registar(itens, utilizadorId(ctx), chave);
            ctx.header("Idempotency-Replayed", Boolean.toString(resultado.repetido));
            ctx.status(resultado.repetido ? HttpStatus.OK : HttpStatus.CREATED).contentType("application/json").result(resultado.json);
            return;
        }
        ctx.status(HttpStatus.CREATED).json(util.VendaJson.lote(service.registarVendaLote(itens, utilizadorId(ctx))));
    }

    private void listarDevedores(Context ctx) {
        List<Map<String, Object>> resultado = devedorDAO.listarTodos(utilizadorId(ctx)).stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("nome", d.getNome());
            item.put("divida", decimalJson(d.getDivida(), 2));
            item.put("descricao", d.getDescricao() == null ? "" : d.getDescricao());
            item.put("data", d.getData().format(DATA_FORMATADA));
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
        ctx.status(HttpStatus.CREATED).json(Map.of("id", devedor.getId()));
    }

    private void deletarDevedor(Context ctx) {
        if (!devedorDAO.deletar(idPath(ctx), utilizadorId(ctx))) {
            throw new BarracaService.RecursoNaoEncontradoException("Dívida não encontrada.");
        }
        ctx.status(HttpStatus.NO_CONTENT);
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
        resultado.put("custo", "0.00");
        resultado.put("stock", decimalJson(produto.getQuantidadeStock(), 3));
        resultado.put("stockMinimo", decimalJson(produto.getStockMinimo(), 3));
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
