package api;

import dao.SessaoDAO;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.Sessao;
import model.Usuario;
import util.EnvUtil;

import java.util.Map;

public class ApiSecurity {

    public static final String SESSION_COOKIE = "bstore_session";
    public static final String AUTH_ATTRIBUTE = "authenticatedUser";
    public static final int SESSION_MAX_AGE_SECONDS = 14 * 24 * 60 * 60;

    public static void autenticar(Context ctx, SessaoDAO sessaoDAO) {
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

    public static void validarOrigem(Context ctx) {
        if ("GET".equals(ctx.method().name()) || "HEAD".equals(ctx.method().name()) || "OPTIONS".equals(ctx.method().name())) {
            return;
        }
        String origin = ctx.header("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }
        String host = ctx.header("Host");
        boolean mesmaOrigem = host != null && (origin.equals("http://" + host) || origin.equals("https://" + host));
        String origemConfigurada = EnvUtil.get("CORS_ALLOWED_ORIGIN");
        boolean origemDesenvolvimento = origemConfigurada != null && origin.equals(origemConfigurada.trim());
        if (!mesmaOrigem && !origemDesenvolvimento) {
            throw new PermissaoException("Origem não permitida.");
        }
    }

    public static UtilizadorAutenticado utilizador(Context ctx) {
        UtilizadorAutenticado utilizador = ctx.attribute(AUTH_ATTRIBUTE);
        if (utilizador == null) {
            throw new AutenticacaoException("Sessão inválida ou expirada.");
        }
        return utilizador;
    }

    public static Long utilizadorId(Context ctx) {
        return utilizador(ctx).usuario.getId();
    }

    public static void exigirSuperuser(Context ctx) {
        if (!"superuser".equals(utilizador(ctx).usuario.getRole())) {
            throw new PermissaoException("Acesso negado.");
        }
    }

    public static void definirCookieSessao(Context ctx, String valor, int maxAge) {
        StringBuilder cookie = new StringBuilder(SESSION_COOKIE + "=" + valor + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge);
        if (EnvUtil.getBoolean("SESSION_COOKIE_SECURE", false)) {
            cookie.append("; Secure");
        }
        ctx.header("Set-Cookie", cookie.toString());
    }

    public static void responderErro(Context ctx, HttpStatus status, String mensagem) {
        ctx.status(status).json(Map.of("erro", mensagem == null || mensagem.isBlank() ? "Pedido inválido." : mensagem));
    }

    public static String obterIpCliente(Context ctx) {
        String xff = ctx.header("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] ips = xff.split(",");
            return ips[ips.length - 1].trim();
        }
        return ctx.ip();
    }

    public static String mascararEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String user = parts[0];
        String maskedUser = user.length() <= 2 ? user.charAt(0) + "***" : user.substring(0, 2) + "***";
        return maskedUser + "@" + parts[1];
    }

    public static final class UtilizadorAutenticado {
        public final String sessaoId;
        public final Usuario usuario;

        public UtilizadorAutenticado(String sessaoId, Usuario usuario) {
            this.sessaoId = sessaoId;
            this.usuario = usuario;
        }
    }

    public static class AutenticacaoException extends RuntimeException {
        public AutenticacaoException(String message) { super(message); }
    }

    public static class PermissaoException extends RuntimeException {
        public PermissaoException(String message) { super(message); }
    }
}
