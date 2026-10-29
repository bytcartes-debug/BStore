package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.SessaoCaixa;
import service.BarracaService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CaixaRoutes {

    private final BarracaService service;
    private final service.notificacao.WhatsappNotificacaoService whatsappNotificacaoService;
    private final service.AuditoriaService auditoriaService;

    public CaixaRoutes(BarracaService service) {
        this(service, new service.notificacao.WhatsappNotificacaoService(), new service.AuditoriaService());
    }

    public CaixaRoutes(BarracaService service, service.notificacao.WhatsappNotificacaoService whatsappNotificacaoService) {
        this(service, whatsappNotificacaoService, new service.AuditoriaService());
    }

    public CaixaRoutes(BarracaService service, service.notificacao.WhatsappNotificacaoService whatsappNotificacaoService, service.AuditoriaService auditoriaService) {
        this.service = service;
        this.whatsappNotificacaoService = whatsappNotificacaoService;
        this.auditoriaService = auditoriaService;
    }

    public void register(Javalin app) {
        app.get("/api/caixa/fecho", this::fechoCaixa);
        app.get("/api/caixa/atual", this::obterSessaoCaixaAtual);
        app.post("/api/caixa/abrir", this::abrirSessaoCaixa);
        app.post("/api/caixa/fechar", this::fecharSessaoCaixa);
        app.get("/api/caixa/historico", this::listarHistoricoCaixa);
        app.get("/api/caixa/exportar", this::exportarCaixaCsv);
        app.get("/api/caixa/despesas/exportar", this::exportarDespesasCaixaCsv);
        app.get("/api/caixa/despesas", this::listarDespesasCaixa);
        app.post("/api/caixa/despesas", this::registarDespesaCaixa);
        app.post("/api/caixa/resumo-diario/whatsapp", this::enviarResumoDiarioWhatsapp);
    }

    private void fechoCaixa(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        LocalDate data = ApiHelpers.dataQuery(ctx, "data");
        if (data == null) data = util.Fuso.hoje();
        ctx.json(service.fechoCaixa(data, uid));
    }

    private void enviarResumoDiarioWhatsapp(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        LocalDate data = ApiHelpers.dataQuery(ctx, "data");
        if (data == null) data = util.Fuso.hoje();
        Map<String, Object> dadosFecho = service.fechoCaixa(data, uid);
        var res = whatsappNotificacaoService.notificarResumoDiario(data, dadosFecho, uid);
        if (res.isSucesso()) {
            ctx.json(Map.of("sucesso", true, "mensagem", "Resumo executivo do dia enviado com sucesso para o WhatsApp do proprietário!"));
        } else {
            ctx.status(HttpStatus.BAD_GATEWAY).json(Map.of("sucesso", false, "erro", res.getMensagem()));
        }
    }

    private void obterSessaoCaixaAtual(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        ctx.json(service.obterResumoSessaoAtual(uid));
    }

    private void abrirSessaoCaixa(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long opId = ApiSecurity.utilizador(ctx).usuario.getId();
        String operadorNome = ApiSecurity.utilizador(ctx).usuario.getNome();
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        BigDecimal inicial = ApiHelpers.decimalComPadrao(body, "valorInicial", "0");
        String nota = ApiHelpers.texto(body, "notaAbertura");
        SessaoCaixa sessao = service.abrirSessaoCaixa(inicial, nota, opId, uid);
        whatsappNotificacaoService.notificarAberturaCaixaAsync(sessao, operadorNome, uid);
        auditoriaService.registrarAsync(
            uid,
            ApiSecurity.utilizador(ctx).usuario.getEmail(),
            "CAIXA",
            "ABERTURA_CAIXA",
            sessao.getId(),
            "Abertura de caixa com valor inicial de " + sessao.getValorInicial() + " MT",
            ApiSecurity.obterIpCliente(ctx),
            ctx.userAgent()
        );
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", sessao.getId());
        res.put("abertaEm", sessao.getAbertaEm().toString());
        res.put("valorInicial", ApiHelpers.decimalJson(sessao.getValorInicial(), 2));
        res.put("estado", sessao.getEstado());
        ctx.status(HttpStatus.CREATED).json(res);
    }

    private void fecharSessaoCaixa(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long opId = ApiSecurity.utilizador(ctx).usuario.getId();
        String operadorNome = ApiSecurity.utilizador(ctx).usuario.getNome();
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Long sessaoId = body.containsKey("sessaoId") && body.get("sessaoId") != null ? ApiHelpers.longObrigatorio(body, "sessaoId") : null;
        if (sessaoId == null) {
            SessaoCaixa aberta = service.buscarSessaoAberta(uid);
            if (aberta == null) throw new BarracaService.RecursoNaoEncontradoException("Não há sessão de caixa aberta.");
            sessaoId = aberta.getId();
        }
        Map<String, Object> resumo = service.obterResumoSessaoAtual(uid);
        BigDecimal contado = ApiHelpers.decimalComPadrao(body, "valorContado", "0");
        String nota = ApiHelpers.texto(body, "notaFecho");
        SessaoCaixa sessao = service.fecharSessaoCaixa(sessaoId, contado, nota, opId, uid);

        BigDecimal vendasDinheiro = BigDecimal.ZERO;
        BigDecimal despesasDinheiro = BigDecimal.ZERO;
        Long totalVendas = 0L;
        if (resumo != null) {
            try {
                if (resumo.get("vendasDinheiro") != null) vendasDinheiro = new BigDecimal(resumo.get("vendasDinheiro").toString().replace(',', '.'));
                if (resumo.get("despesasDinheiro") != null) despesasDinheiro = new BigDecimal(resumo.get("despesasDinheiro").toString().replace(',', '.'));
                if (resumo.get("totalVendas") != null) totalVendas = Long.valueOf(resumo.get("totalVendas").toString());
            } catch (Exception ignored) {}
        }
        whatsappNotificacaoService.notificarFechoCaixaAsync(sessao, totalVendas, vendasDinheiro, despesasDinheiro, operadorNome, uid);
        auditoriaService.registrarAsync(
            uid,
            ApiSecurity.utilizador(ctx).usuario.getEmail(),
            "CAIXA",
            "FECHO_CAIXA",
            sessao.getId(),
            String.format("Fecho de caixa: Contado=%s MT, Esperado=%s MT, Diferença=%s MT. Nota: %s",
                sessao.getValorContado(), sessao.getValorEsperado(), sessao.getDiferenca(),
                sessao.getNotaFecho() != null ? sessao.getNotaFecho() : ""),
            ApiSecurity.obterIpCliente(ctx),
            ctx.userAgent()
        );

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", sessao.getId());
        res.put("abertaEm", sessao.getAbertaEm().toString());
        res.put("fechadaEm", sessao.getFechadaEm() != null ? sessao.getFechadaEm().toString() : "");
        res.put("valorInicial", ApiHelpers.decimalJson(sessao.getValorInicial(), 2));
        res.put("valorEsperado", ApiHelpers.decimalJson(sessao.getValorEsperado(), 2));
        res.put("valorContado", ApiHelpers.decimalJson(sessao.getValorContado(), 2));
        res.put("diferenca", ApiHelpers.decimalJson(sessao.getDiferenca(), 2));
        res.put("notaFecho", sessao.getNotaFecho() != null ? sessao.getNotaFecho() : "");
        res.put("estado", sessao.getEstado());
        ctx.json(res);
    }

    private void listarHistoricoCaixa(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        int limite = ApiHelpers.inteiroQuery(ctx, "limite", 50);
        LocalDate inicio = ApiHelpers.dataQuery(ctx, "inicio");
        LocalDate fim = ApiHelpers.dataQuery(ctx, "fim");
        List<SessaoCaixa> sessoes = service.listarHistoricoSessoes(uid, inicio, fim, limite);
        List<Map<String, Object>> res = new ArrayList<>();
        for (SessaoCaixa s : sessoes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("abertaEm", s.getAbertaEm().toString());
            m.put("fechadaEm", s.getFechadaEm() != null ? s.getFechadaEm().toString() : null);
            m.put("valorInicial", ApiHelpers.decimalJson(s.getValorInicial(), 2));
            m.put("valorEsperado", s.getValorEsperado() != null ? ApiHelpers.decimalJson(s.getValorEsperado(), 2) : null);
            m.put("valorContado", s.getValorContado() != null ? ApiHelpers.decimalJson(s.getValorContado(), 2) : null);
            m.put("diferenca", s.getDiferenca() != null ? ApiHelpers.decimalJson(s.getDiferenca(), 2) : null);
            m.put("notaAbertura", s.getNotaAbertura() != null ? s.getNotaAbertura() : "");
            m.put("notaFecho", s.getNotaFecho() != null ? s.getNotaFecho() : "");
            m.put("estado", s.getEstado());
            res.add(m);
        }
        ctx.json(res);
    }

    private void exportarCaixaCsv(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        int limite = ApiHelpers.inteiroQuery(ctx, "limite", 200);
        LocalDate inicio = ApiHelpers.dataQuery(ctx, "inicio");
        LocalDate fim = ApiHelpers.dataQuery(ctx, "fim");
        byte[] bytes = service.exportarCaixaCsv(uid, inicio, fim, limite);
        String nomeFicheiro = "fechos_caixa_" + util.Fuso.hoje() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void exportarDespesasCaixaCsv(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long sessaoId = ApiHelpers.longQuery(ctx, "sessaoId");
        byte[] bytes = service.exportarDespesasCaixaCsv(sessaoId, uid);
        String nomeFicheiro = "despesas_caixa_" + util.Fuso.hoje() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void listarDespesasCaixa(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long sessaoId = ApiHelpers.longQuery(ctx, "sessaoId");
        ctx.json(service.listarDespesasCaixa(sessaoId, uid));
    }

    private void registarDespesaCaixa(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long opId = ApiSecurity.utilizador(ctx).usuario.getId();
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        BigDecimal valor = ApiHelpers.decimalObrigatorio(body, "valor");
        String categoria = ApiHelpers.texto(body, "categoria");
        String descricao = ApiHelpers.textoObrigatorio(body, "descricao");
        Map<String, Object> res = service.registarDespesaCaixa(valor, categoria, descricao, opId, uid);
        Long despesaId = null;
        if (res.get("id") != null) {
            try {
                despesaId = Long.valueOf(res.get("id").toString());
            } catch (Exception ignored) {}
        }
        auditoriaService.registrarAsync(
            uid,
            ApiSecurity.utilizador(ctx).usuario.getEmail(),
            "CAIXA",
            "DESPESA_SANGRIA",
            despesaId,
            String.format("Despesa/Sangria no caixa de %s MT [%s]: %s", valor, categoria != null ? categoria : "Geral", descricao),
            ApiSecurity.obterIpCliente(ctx),
            ctx.userAgent()
        );
        ctx.status(HttpStatus.CREATED).json(res);
    }
}
