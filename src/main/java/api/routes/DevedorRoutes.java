package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import dao.DevedorDAO;
import dao.PagamentoDividaDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.Devedor;
import model.PagamentoDivida;
import service.BarracaService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DevedorRoutes {

    private final BarracaService service;
    private final DevedorDAO devedorDAO;

    public DevedorRoutes(BarracaService service, DevedorDAO devedorDAO) {
        this.service = service;
        this.devedorDAO = devedorDAO;
    }

    public void register(Javalin app) {
        app.get("/api/devedores", this::listarDevedores);
        app.post("/api/devedores", this::criarDevedor);
        app.delete("/api/devedores/{id}", this::deletarDevedor);
        app.post("/api/devedores/{id}/pagamentos", this::registarPagamentoDivida);
        app.get("/api/devedores/{id}/pagamentos", this::listarPagamentosDivida);
    }

    private void listarDevedores(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        PagamentoDividaDAO pagDAO = new PagamentoDividaDAO();
        List<Map<String, Object>> resultado = devedorDAO.listarTodos(uid).stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("nome", d.getNome());
            item.put("divida", ApiHelpers.decimalJson(d.getDivida(), 2));
            BigDecimal pagos = pagDAO.totalPagoPorDevedor(uid, d.getId());
            BigDecimal saldo = d.getDivida().subtract(pagos);
            if (saldo.compareTo(BigDecimal.ZERO) < 0) saldo = BigDecimal.ZERO;
            item.put("saldo", ApiHelpers.decimalJson(saldo, 2));
            item.put("totalPago", ApiHelpers.decimalJson(pagos, 2));
            item.put("descricao", d.getDescricao() == null ? "" : d.getDescricao());
            item.put("data", d.getData() != null ? d.getData().format(ApiHelpers.DATA_FORMATADA) : "");
            item.put("vendaId", d.getVendaId());
            return item;
        }).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void criarDevedor(Context ctx) {
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        BigDecimal divida = ApiHelpers.dinheiro(ApiHelpers.decimalObrigatorio(body, "divida"));
        if (divida.signum() <= 0) {
            throw new IllegalArgumentException("A dívida deve ser maior que zero.");
        }
        Devedor devedor = new Devedor();
        devedor.setNome(ApiHelpers.textoObrigatorio(body, "nome"));
        devedor.setDivida(divida);
        devedor.setDescricao(ApiHelpers.texto(body, "descricao"));
        devedor.setData(util.Fuso.hoje());
        devedor.setUsuarioId(ApiSecurity.utilizadorId(ctx));
        devedor = devedorDAO.salvar(devedor);
        ctx.status(HttpStatus.CREATED).json(Map.of("id", devedor.getId(), "saldo", ApiHelpers.decimalJson(divida, 2)));
    }

    private void registarPagamentoDivida(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        BigDecimal valor = ApiHelpers.decimalObrigatorio(body, "valor");
        String metodo = ApiHelpers.textoObrigatorio(body, "metodo");
        String observacao = ApiHelpers.texto(body, "observacao");
        Map<String, Object> resultado = service.registarPagamentoDivida(id, valor, metodo, observacao, uid);
        ctx.status(HttpStatus.CREATED).json(resultado);
    }

    private void listarPagamentosDivida(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long id = ApiHelpers.idPath(ctx);
        List<PagamentoDivida> pagamentos = new PagamentoDividaDAO().listarPorDevedor(uid, id);
        List<Map<String, Object>> resultado = pagamentos.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("valor", ApiHelpers.decimalJson(p.getValor(), 2));
            item.put("metodo", p.getMetodo());
            item.put("criadoEm", p.getCriadoEm() != null ? p.getCriadoEm().toString() : "");
            item.put("observacao", p.getObservacao() == null ? "" : p.getObservacao());
            return item;
        }).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void deletarDevedor(Context ctx) {
        try {
            if (!devedorDAO.deletar(ApiHelpers.idPath(ctx), ApiSecurity.utilizadorId(ctx))) {
                throw new BarracaService.RecursoNaoEncontradoException("Dívida não encontrada.");
            }
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalStateException e) {
            throw new BarracaService.ConflitoException(e.getMessage());
        }
    }
}
