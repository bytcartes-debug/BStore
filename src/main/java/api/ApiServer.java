package api;

import api.routes.AuthRoutes;
import api.routes.CaixaRoutes;
import api.routes.CategoriaRoutes;
import api.routes.ConfiguracaoRoutes;
import api.routes.DashboardRoutes;
import api.routes.DevedorRoutes;
import api.routes.IaRoutes;
import api.routes.ProdutoRoutes;
import api.routes.VendaRoutes;
import service.ia.IaControloService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dao.CategoriaDAO;
import dao.DevedorDAO;
import dao.ProdutoDAO;
import dao.SessaoDAO;
import dao.UsuarioDAO;
import dao.VendaDAO;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import service.BarracaService;
import service.RateLimiterLogin;
import service.StockService;
import service.VendaIdempotenteService;
import util.JPAUtil;

import java.util.Map;
import java.util.UUID;

public class ApiServer {

    private final BarracaService service = new BarracaService();
    private final VendaIdempotenteService vendaIdempotente = new VendaIdempotenteService();
    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final SessaoDAO sessaoDAO = new SessaoDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final VendaDAO vendaDAO = new VendaDAO();
    private final StockService stockService = new StockService();
    private final RateLimiterLogin rateLimiterLogin = new RateLimiterLogin();
    private final IaControloService iaControloService = new IaControloService();
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

    public RateLimiterLogin getRateLimiterLogin() {
        return rateLimiterLogin;
    }

    private void configurarErros() {
        app.exception(VendaIdempotenteService.ChaveReutilizadaException.class,
            (e, ctx) -> ctx.status(HttpStatus.CONFLICT).json(Map.of("erro", e.getMessage(), "codigo", "CHAVE_REUTILIZADA")));
        app.exception(ApiSecurity.AutenticacaoException.class, (e, ctx) -> ApiSecurity.responderErro(ctx, HttpStatus.UNAUTHORIZED, e.getMessage()));
        app.exception(ApiSecurity.PermissaoException.class, (e, ctx) -> ApiSecurity.responderErro(ctx, HttpStatus.FORBIDDEN, e.getMessage()));
        app.exception(BarracaService.RecursoNaoEncontradoException.class,
            (e, ctx) -> ApiSecurity.responderErro(ctx, HttpStatus.NOT_FOUND, e.getMessage()));
        app.exception(BarracaService.ConflitoException.class,
            (e, ctx) -> ApiSecurity.responderErro(ctx, HttpStatus.CONFLICT, e.getMessage()));
        app.exception(IllegalArgumentException.class,
            (e, ctx) -> ApiSecurity.responderErro(ctx, HttpStatus.BAD_REQUEST, e.getMessage()));
        app.exception(IllegalStateException.class,
            (e, ctx) -> ApiSecurity.responderErro(ctx, HttpStatus.CONFLICT, e.getMessage()));
        app.exception(Exception.class,
            (e, ctx) -> {
                String errorId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                System.err.println("[ERRO INTERNO " + errorId + "] " + e.getMessage());
                e.printStackTrace();
                ApiSecurity.responderErro(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno. Tente novamente. Código: " + errorId);
            });
    }

    private void configurarSeguranca() {
        app.before(ctx -> {
            if (!ctx.path().startsWith("/api/") || rotaPublica(ctx.path())) {
                return;
            }
            ApiSecurity.autenticar(ctx, sessaoDAO);
            ApiSecurity.validarOrigem(ctx);
            JPAUtil.setCurrentUser(ApiSecurity.utilizadorId(ctx));
        });

        app.after(ctx -> JPAUtil.clearCurrentUser());
    }

    private boolean rotaPublica(String path) {
        return "/api/health".equals(path) || "/api/saude".equals(path) || "/api/auth/login".equals(path);
    }

    private void configurarRotas() {
        app.get("/api/health", ctx -> ctx.json(Map.of("estado", "ok")));
        app.get("/api/saude", ctx -> ctx.json(Map.of("estado", "ok", "fuso", "Africa/Maputo")));

        new AuthRoutes(usuarioDAO, sessaoDAO, rateLimiterLogin).register(app);
        new DashboardRoutes(service, vendaDAO, stockService, categoriaDAO, devedorDAO).register(app);
        new CategoriaRoutes(service, categoriaDAO).register(app);
        new ProdutoRoutes(service, produtoDAO, stockService).register(app);
        new VendaRoutes(service, vendaDAO, vendaIdempotente).register(app);
        new CaixaRoutes(service).register(app);
        new DevedorRoutes(service, devedorDAO).register(app);
        new ConfiguracaoRoutes(service).register(app);
        new IaRoutes(iaControloService).register(app);

        app.error(HttpStatus.NOT_FOUND, ctx -> {
            if (!ctx.path().startsWith("/api")) {
                ctx.result(getClass().getResourceAsStream("/public/index.html"));
                ctx.contentType("text/html");
            }
        });
    }

    public static class AutenticacaoException extends ApiSecurity.AutenticacaoException {
        public AutenticacaoException(String message) { super(message); }
    }

    public static class PermissaoException extends ApiSecurity.PermissaoException {
        public PermissaoException(String message) { super(message); }
    }
}
