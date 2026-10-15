package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import dao.SessaoDAO;
import dao.UsuarioDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.Sessao;
import model.Usuario;
import service.BarracaService;
import service.RateLimiterLogin;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthRoutes {

    private final UsuarioDAO usuarioDAO;
    private final SessaoDAO sessaoDAO;
    private final RateLimiterLogin rateLimiterLogin;

    public AuthRoutes(UsuarioDAO usuarioDAO, SessaoDAO sessaoDAO, RateLimiterLogin rateLimiterLogin) {
        this.usuarioDAO = usuarioDAO;
        this.sessaoDAO = sessaoDAO;
        this.rateLimiterLogin = rateLimiterLogin;
    }

    public void register(Javalin app) {
        app.post("/api/auth/login", this::login);
        app.get("/api/auth/me", this::obterSessaoActual);
        app.post("/api/auth/logout", this::logout);

        app.get("/api/usuarios", this::listarUsuarios);
        app.post("/api/usuarios", this::criarUsuario);
        app.put("/api/usuarios/{id}", this::atualizarUsuario);
        app.post("/api/usuarios/{id}/alterar-senha", this::alterarSenha);
        app.delete("/api/usuarios/{id}", this::deletarUsuario);
    }

    private void login(Context ctx) {
        String ip = ApiSecurity.obterIpCliente(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        String email = ApiHelpers.textoObrigatorio(body, "email").toLowerCase();
        String senha = ApiHelpers.textoObrigatorio(body, "password");

        String chaveIp = "ip:" + ip;
        String chaveEmail = "email:" + email;

        if (rateLimiterLogin.estaBloqueado(chaveIp) || rateLimiterLogin.estaBloqueado(chaveEmail)) {
            String emailMascarado = ApiSecurity.mascararEmail(email);
            System.err.println("[RATE_LIMIT_LOGIN] Tentativa de login bloqueada: IP=" + ip + ", email=" + emailMascarado);
            ApiSecurity.responderErro(ctx, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas tentativas. Tente novamente daqui a 10 minutos.");
            return;
        }

        Usuario usuario = usuarioDAO.buscarPorEmail(email);
        if (usuario == null || !usuario.verificarSenha(senha)) {
            rateLimiterLogin.registarFalha(chaveIp);
            rateLimiterLogin.registarFalha(chaveEmail);
            throw new ApiSecurity.AutenticacaoException("Email ou senha incorretos.");
        }
        if (usuario.isExpirado()) {
            throw new ApiSecurity.PermissaoException("Conta expirada. Contacte o administrador.");
        }
        rateLimiterLogin.limpar(chaveIp);
        rateLimiterLogin.limpar(chaveEmail);
        if (usuario.usaHashLegado()) {
            usuario.setSenha(senha);
            usuarioDAO.actualizar(usuario);
        }
        Sessao sessao = sessaoDAO.criar(usuario);
        ApiSecurity.definirCookieSessao(ctx, sessao.getId(), ApiSecurity.SESSION_MAX_AGE_SECONDS);
        ctx.json(ApiHelpers.utilizadorJson(usuario));
    }

    private void obterSessaoActual(Context ctx) {
        ctx.json(ApiHelpers.utilizadorJson(ApiSecurity.utilizador(ctx).usuario));
    }

    private void logout(Context ctx) {
        sessaoDAO.revogar(ApiSecurity.utilizador(ctx).sessaoId);
        ApiSecurity.definirCookieSessao(ctx, "", 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listarUsuarios(Context ctx) {
        ApiSecurity.exigirSuperuser(ctx);
        List<Map<String, Object>> resultado = usuarioDAO.listarTodos().stream().map(ApiHelpers::utilizadorJson).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void criarUsuario(Context ctx) {
        ApiSecurity.exigirSuperuser(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        String email = ApiHelpers.textoObrigatorio(body, "email").toLowerCase();
        if (usuarioDAO.buscarPorEmail(email) != null) {
            throw new BarracaService.ConflitoException("Já existe um utilizador com este email.");
        }
        String role = ApiHelpers.textoComPadrao(body, "role", "operator");
        ApiHelpers.validarRole(role);
        Usuario usuario = new Usuario(ApiHelpers.textoObrigatorio(body, "nome"), email, ApiHelpers.textoObrigatorio(body, "password"), role);
        if (!"superuser".equals(role)) {
            usuario.aplicarDiasAcesso(ApiHelpers.intComPadrao(body, "diasAcesso", 30));
        }
        usuario = usuarioDAO.salvar(usuario);
        ctx.status(HttpStatus.CREATED).json(ApiHelpers.utilizadorJson(usuario));
    }

    private void atualizarUsuario(Context ctx) {
        ApiSecurity.exigirSuperuser(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Usuario usuario = usuarioDAO.buscarPorId(ApiHelpers.idPath(ctx));
        if (usuario == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Utilizador não encontrado.");
        }
        if (body.containsKey("nome")) usuario.setNome(ApiHelpers.textoObrigatorio(body, "nome"));
        if (body.containsKey("email")) {
            String email = ApiHelpers.textoObrigatorio(body, "email").toLowerCase();
            Usuario existente = usuarioDAO.buscarPorEmail(email);
            if (existente != null && !existente.getId().equals(usuario.getId())) {
                throw new BarracaService.ConflitoException("Já existe um utilizador com este email.");
            }
            usuario.setEmail(email);
        }
        if (body.containsKey("role")) {
            String role = ApiHelpers.textoObrigatorio(body, "role");
            ApiHelpers.validarRole(role);
            usuario.setRole(role);
        }
        if (body.containsKey("password") && ApiHelpers.texto(body, "password") != null && !ApiHelpers.texto(body, "password").isBlank()) {
            usuario.setSenha(ApiHelpers.texto(body, "password"));
            sessaoDAO.revogarDoUtilizador(usuario.getId());
        }
        if (body.containsKey("diasAcesso") && !"superuser".equals(usuario.getRole())) {
            usuario.aplicarDiasAcesso(ApiHelpers.intComPadrao(body, "diasAcesso", 30));
        }
        ctx.json(ApiHelpers.utilizadorJson(usuarioDAO.actualizar(usuario)));
    }

    private void alterarSenha(Context ctx) {
        Long alvoId = ApiHelpers.idPath(ctx);
        ApiSecurity.UtilizadorAutenticado actual = ApiSecurity.utilizador(ctx);
        if (!"superuser".equals(actual.usuario.getRole()) && !actual.usuario.getId().equals(alvoId)) {
            throw new ApiSecurity.PermissaoException("Acesso negado.");
        }
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Usuario usuario = usuarioDAO.buscarPorId(alvoId);
        if (usuario == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Utilizador não encontrado.");
        }
        if (!"superuser".equals(actual.usuario.getRole())) {
            String senhaAtual = ApiHelpers.textoObrigatorio(body, "senhaAtual");
            if (!usuario.verificarSenha(senhaAtual)) {
                throw new ApiSecurity.AutenticacaoException("Senha atual incorreta.");
            }
        }
        usuario.setSenha(ApiHelpers.textoObrigatorio(body, "novaSenha"));
        usuarioDAO.actualizar(usuario);
        sessaoDAO.revogarDoUtilizador(alvoId);
        ApiSecurity.definirCookieSessao(ctx, "", 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deletarUsuario(Context ctx) {
        ApiSecurity.exigirSuperuser(ctx);
        Long id = ApiHelpers.idPath(ctx);
        if (id.equals(ApiSecurity.utilizadorId(ctx))) {
            throw new IllegalArgumentException("Não é possível apagar a própria conta.");
        }
        if (usuarioDAO.contarTodos() <= 1) {
            throw new IllegalStateException("Não é possível apagar o único utilizador.");
        }
        Usuario alvo = usuarioDAO.buscarPorId(id);
        if (alvo == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Utilizador não encontrado.");
        }
        if ("superuser".equals(alvo.getRole())) {
            long totalSuperusers = usuarioDAO.listarTodos().stream()
                    .filter(u -> "superuser".equals(u.getRole()))
                    .count();
            if (totalSuperusers <= 1) {
                throw new IllegalStateException("Não é possível apagar o único super-administrador do sistema.");
            }
        }
        sessaoDAO.revogarDoUtilizador(id);
        usuarioDAO.eliminar(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
