package api.routes;

import api.ApiSecurity;
import io.javalin.Javalin;
import io.javalin.http.Context;
import service.AuditoriaService;

public class AuditoriaRoutes {

    private final AuditoriaService auditoriaService;

    public AuditoriaRoutes(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    public void register(Javalin app) {
        app.get("/api/auditoria", this::listarAuditoria);
    }

    private void listarAuditoria(Context ctx) {
        ApiSecurity.exigirSuperuser(ctx);
        Long uid = ApiSecurity.utilizadorId(ctx);

        String tipo = ctx.queryParam("tipo");
        String acao = ctx.queryParam("acao");

        int pagina = 1;
        try {
            String p = ctx.queryParam("pagina");
            if (p != null && !p.isBlank()) pagina = Math.max(1, Integer.parseInt(p));
        } catch (NumberFormatException ignored) {}

        int limite = 50;
        try {
            String l = ctx.queryParam("limite");
            if (l != null && !l.isBlank()) limite = Math.max(10, Math.min(100, Integer.parseInt(l)));
        } catch (NumberFormatException ignored) {}

        ctx.json(auditoriaService.listarPaginado(uid, tipo, acao, pagina, limite));
    }
}
