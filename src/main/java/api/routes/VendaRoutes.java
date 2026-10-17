package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import dao.VendaDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.Venda;
import service.BarracaService;
import service.VendaIdempotenteService;
import util.VendaJson;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VendaRoutes {

    private final BarracaService service;
    private final VendaDAO vendaDAO;
    private final VendaIdempotenteService vendaIdempotente;
    private final service.notificacao.WhatsappNotificacaoService whatsappNotificacaoService;

    public VendaRoutes(BarracaService service, VendaDAO vendaDAO, VendaIdempotenteService vendaIdempotente) {
        this(service, vendaDAO, vendaIdempotente, new service.notificacao.WhatsappNotificacaoService());
    }

    public VendaRoutes(BarracaService service, VendaDAO vendaDAO, VendaIdempotenteService vendaIdempotente,
                       service.notificacao.WhatsappNotificacaoService whatsappNotificacaoService) {
        this.service = service;
        this.vendaDAO = vendaDAO;
        this.vendaIdempotente = vendaIdempotente;
        this.whatsappNotificacaoService = whatsappNotificacaoService;
    }

    public void register(Javalin app) {
        app.get("/api/vendas/exportar", this::exportarVendasCsv);
        app.get("/api/vendas", this::listarVendas);
        app.get("/api/vendas/{id}", this::obterVenda);
        app.post("/api/vendas", this::registarVenda);
        app.post("/api/vendas/lote", this::registarVendaLote);
        app.post("/api/vendas/{id}/anular", this::anularVenda);
        app.post("/api/vendas/{id}/devolver", this::devolverVenda);
        app.get("/api/vendas/{id}/devolucoes", this::listarDevolucoesVenda);
    }

    private void listarVendas(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        if (ctx.queryParam("page") != null) {
            int page = ApiHelpers.inteiroQuery(ctx, "page", 1);
            int pageSize = ApiHelpers.inteiroQuery(ctx, "pageSize", 25);
            String q = ctx.queryParam("q");
            LocalDate inicio = ApiHelpers.dataQuery(ctx, "inicio");
            LocalDate fim = ApiHelpers.dataQuery(ctx, "fim");
            String metodo = ctx.queryParam("metodo");
            String estado = ctx.queryParam("estado");
            ctx.json(vendaDAO.listarPagina(uid, page, pageSize, q, inicio, fim, metodo, estado).map(this::vendaJson));
            return;
        }
        ctx.json(service.listarVendas(uid).stream().map(this::vendaJson).collect(Collectors.toList()));
    }

    private void exportarVendasCsv(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        LocalDate inicio = ApiHelpers.dataQuery(ctx, "inicio");
        LocalDate fim = ApiHelpers.dataQuery(ctx, "fim");
        String metodo = ctx.queryParam("metodo");
        String estado = ctx.queryParam("estado");
        String q = ctx.queryParam("q");
        byte[] bytes = service.exportarVendasCsv(inicio, fim, metodo, estado, q, uid);
        String nomeFicheiro = "vendas_" + util.Fuso.hoje() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void obterVenda(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        Venda venda = service.buscarVendaPorId(id, uid);
        ctx.json(vendaJson(venda));
    }

    private void registarVenda(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        String operadorNome = ApiSecurity.utilizador(ctx).usuario.getNome();
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Venda venda = service.registarVenda(
            ApiHelpers.longObrigatorio(body, "produtoId"),
            ApiHelpers.decimalObrigatorio(body, "quantidade"),
            ApiHelpers.texto(body, "observacao"),
            uid);
        whatsappNotificacaoService.notificarVendaAsync(venda, venda.getItens(), venda.getPagamentos(), BigDecimal.ZERO, operadorNome, uid);
        if (venda.getItens() != null) {
            List<model.Produto> baixos = new ArrayList<>();
            for (model.ItemVenda iv : venda.getItens()) {
                if (iv.getProduto() != null && iv.getProduto().stockAbaixoMinimo()) {
                    baixos.add(iv.getProduto());
                }
            }
            if (!baixos.isEmpty()) {
                whatsappNotificacaoService.notificarBaixoEstoqueAsync(baixos, uid);
            }
        }
        ctx.status(HttpStatus.CREATED).json(vendaJson(venda));
    }

    @SuppressWarnings("unchecked")
    private void registarVendaLote(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        String operadorNome = ApiSecurity.utilizador(ctx).usuario.getNome();
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
            VendaIdempotenteService.Resultado resultado =
                vendaIdempotente.registar(itens, pagamentos, clienteId, observacao, uuidCliente, uid, chave);
            ctx.header("Idempotency-Replayed", Boolean.toString(resultado.repetido));
            if (!resultado.repetido) {
                dispararNotificacoesVenda(resultado.dados, operadorNome, uid);
            }
            ctx.status(resultado.repetido ? HttpStatus.OK : HttpStatus.CREATED)
                .contentType("application/json")
                .result(resultado.json);
            return;
        }

        Map<String, Object> resultado = service.registarVendaLote(itens, pagamentos, clienteId, observacao, uuidCliente, uid);
        dispararNotificacoesVenda(resultado, operadorNome, uid);
        ctx.status(HttpStatus.CREATED).json(VendaJson.lote(resultado));
    }

    @SuppressWarnings("unchecked")
    private void dispararNotificacoesVenda(Map<String, Object> resultado, String operadorNome, Long uid) {
        if (resultado == null) return;
        Venda venda = (Venda) resultado.get("venda");
        if (venda == null) return;
        List<model.ItemVenda> itensCriados = (List<model.ItemVenda>) resultado.get("itensCriados");
        BigDecimal troco = (BigDecimal) resultado.get("troco");
        whatsappNotificacaoService.notificarVendaAsync(venda, itensCriados, venda.getPagamentos(), troco, operadorNome, uid);

        List<model.Produto> produtosBaixoStock = (List<model.Produto>) resultado.get("produtosBaixoStock");
        if (produtosBaixoStock != null && !produtosBaixoStock.isEmpty()) {
            whatsappNotificacaoService.notificarBaixoEstoqueAsync(produtosBaixoStock, uid);
        }
    }

    private void anularVenda(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        String motivo = ApiHelpers.textoObrigatorio(body, "motivo");
        Venda venda = service.anularVenda(id, motivo, uid);
        ctx.json(vendaJson(venda));
    }

    @SuppressWarnings("unchecked")
    private void devolverVenda(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long opId = ApiSecurity.utilizador(ctx).usuario.getId();
        Long vendaId = ApiHelpers.idPath(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        String motivo = ApiHelpers.texto(body, "motivo");
        String uuidCliente = ApiHelpers.texto(body, "uuidCliente");
        if (uuidCliente == null || uuidCliente.isBlank()) {
            uuidCliente = ctx.header("Idempotency-Key");
        }
        List<Map<String, Object>> itensRaw = (List<Map<String, Object>>) body.get("itens");
        if (itensRaw == null || itensRaw.isEmpty()) {
            throw new IllegalArgumentException("Lista de itens a devolver está vazia.");
        }
        List<BarracaService.DevolucaoItemParam> params = new ArrayList<>();
        for (Map<String, Object> item : itensRaw) {
            Long itemVendaId = ApiHelpers.longObrigatorio(item, "itemVendaId");
            BigDecimal qtd = ApiHelpers.decimalObrigatorio(item, "quantidade");
            params.add(new BarracaService.DevolucaoItemParam(itemVendaId, qtd));
        }
        Map<String, Object> devolucao = service.processarDevolucao(vendaId, params, motivo, uuidCliente, opId, uid);
        ctx.status(HttpStatus.CREATED).json(devolucao);
    }

    private void listarDevolucoesVenda(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long vendaId = ApiHelpers.idPath(ctx);
        ctx.json(service.listarDevolucoesVenda(vendaId, uid));
    }

    private Map<String, Object> vendaJson(Venda venda) {
        return VendaJson.venda(venda);
    }
}
