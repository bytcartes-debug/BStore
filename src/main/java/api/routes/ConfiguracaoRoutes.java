package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.DefinicaoLoja;
import model.MetodoPagamento;
import service.BarracaService;
import service.notificacao.NotificacaoWhatsAppClient;
import service.notificacao.WhatsappNotificacaoService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfiguracaoRoutes {

    private final BarracaService service;
    private final WhatsappNotificacaoService whatsappNotificacaoService;

    public ConfiguracaoRoutes(BarracaService service) {
        this(service, new WhatsappNotificacaoService());
    }

    public ConfiguracaoRoutes(BarracaService service, WhatsappNotificacaoService whatsappNotificacaoService) {
        this.service = service;
        this.whatsappNotificacaoService = whatsappNotificacaoService;
    }

    public void register(Javalin app) {
        app.get("/api/metodos-pagamento", this::listarMetodosPagamento);
        app.post("/api/metodos-pagamento", this::criarMetodoPagamento);
        app.put("/api/metodos-pagamento/{id}/toggle", this::alternarMetodoPagamento);
        app.put("/api/metodos-pagamento/{id}", this::actualizarMetodoPagamento);
        app.delete("/api/metodos-pagamento/{id}", this::eliminarMetodoPagamento);

        app.get("/api/definicoes", this::obterDefinicoesLoja);
        app.put("/api/definicoes", this::atualizarDefinicoesLoja);
        app.get("/api/definicoes/whatsapp/status", this::obterStatusWhatsapp);
        app.post("/api/definicoes/whatsapp/testar", this::testarEnvioWhatsapp);
    }

    private Map<String, Object> metodoPagamentoJson(MetodoPagamento m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("nome", m.getNome());
        map.put("tipo", m.getTipo());
        map.put("ativo", m.getAtivo());
        map.put("ordem", m.getOrdem());
        return map;
    }

    private void listarMetodosPagamento(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        boolean apenasAtivos = Boolean.parseBoolean(ctx.queryParam("apenasAtivos"));
        List<MetodoPagamento> metodos = apenasAtivos
                ? service.listarMetodosPagamentoAtivos(uid)
                : service.listarMetodosPagamento(uid);
        ctx.json(metodos.stream().map(this::metodoPagamentoJson).collect(Collectors.toList()));
    }

    private void criarMetodoPagamento(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        String nome = ApiHelpers.textoObrigatorio(body, "nome");
        String tipo = body.get("tipo") != null ? body.get("tipo").toString() : "DIGITAL";
        MetodoPagamento m = service.criarMetodoPagamento(nome, tipo, uid);
        ctx.status(HttpStatus.CREATED).json(metodoPagamentoJson(m));
    }

    private void alternarMetodoPagamento(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        MetodoPagamento m = service.alternarMetodoPagamento(id, uid);
        ctx.json(metodoPagamentoJson(m));
    }

    private void actualizarMetodoPagamento(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        String nome = body.get("nome") != null ? body.get("nome").toString() : null;
        Boolean ativo = body.get("ativo") != null ? Boolean.valueOf(body.get("ativo").toString()) : null;
        Integer ordem = body.get("ordem") != null ? Integer.valueOf(body.get("ordem").toString()) : null;
        MetodoPagamento m = service.actualizarMetodoPagamento(id, nome, ativo, ordem, uid);
        ctx.json(metodoPagamentoJson(m));
    }

    private void eliminarMetodoPagamento(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        service.eliminarMetodoPagamento(id, uid);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void obterDefinicoesLoja(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        DefinicaoLoja def = service.obterDefinicoesLoja(uid);
        WhatsappNotificacaoService.ConfiguracaoWhatsApp cfg = whatsappNotificacaoService.carregarConfiguracao(uid);
        NotificacaoWhatsAppClient.StatusSessao status = whatsappNotificacaoService.verificarStatusSessao();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("controloCaixa", def.getControloCaixa());
        res.put("nomeLoja", def.getNomeLoja() != null ? def.getNomeLoja() : "");
        res.put("whatsappNotificacoesActivas", cfg.activa);
        res.put("whatsappTelefoneDono", def.getWhatsappTelefoneDono() != null ? def.getWhatsappTelefoneDono() : (cfg.telefone != null ? cfg.telefone : ""));
        res.put("whatsappConectado", status.isConectado());
        res.put("whatsappStatus", status.name());
        res.put("whatsappMensagemStatus", status.getDescricao());
        ctx.json(res);
    }

    private void atualizarDefinicoesLoja(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Boolean controlo = body.containsKey("controloCaixa") ? Boolean.TRUE.equals(body.get("controloCaixa")) : null;
        String nome = ApiHelpers.texto(body, "nomeLoja");
        Boolean whatsappActivo = body.containsKey("whatsappNotificacoesActivas") ? Boolean.TRUE.equals(body.get("whatsappNotificacoesActivas")) : null;
        String whatsappTelefone = ApiHelpers.texto(body, "whatsappTelefoneDono");

        DefinicaoLoja def = service.atualizarDefinicoesLoja(controlo, nome, whatsappActivo, whatsappTelefone, uid);
        WhatsappNotificacaoService.ConfiguracaoWhatsApp cfg = whatsappNotificacaoService.carregarConfiguracao(uid);
        NotificacaoWhatsAppClient.StatusSessao status = whatsappNotificacaoService.verificarStatusSessao();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("controloCaixa", def.getControloCaixa());
        res.put("nomeLoja", def.getNomeLoja() != null ? def.getNomeLoja() : "");
        res.put("whatsappNotificacoesActivas", cfg.activa);
        res.put("whatsappTelefoneDono", def.getWhatsappTelefoneDono() != null ? def.getWhatsappTelefoneDono() : (cfg.telefone != null ? cfg.telefone : ""));
        res.put("whatsappConectado", status.isConectado());
        res.put("whatsappStatus", status.name());
        res.put("whatsappMensagemStatus", status.getDescricao());
        ctx.json(res);
    }

    private void obterStatusWhatsapp(Context ctx) {
        NotificacaoWhatsAppClient.StatusSessao status = whatsappNotificacaoService.verificarStatusSessao();
        ctx.json(Map.of(
                "conectado", status.isConectado(),
                "status", status.name(),
                "mensagem", status.getDescricao()
        ));
    }

    private void testarEnvioWhatsapp(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        try {
            NotificacaoWhatsAppClient.ResultadoEnvio resultado = whatsappNotificacaoService.testarConexao(uid);
            if (resultado.isSucesso()) {
                ctx.json(Map.of("sucesso", true, "mensagem", resultado.getMensagem()));
            } else {
                ctx.status(HttpStatus.BAD_GATEWAY).json(Map.of("sucesso", false, "erro", resultado.getMensagem()));
            }
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("sucesso", false, "erro", e.getMessage()));
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("sucesso", false, "erro", "Erro ao comunicar com OpenWA: " + e.getMessage()));
        }
    }
}
