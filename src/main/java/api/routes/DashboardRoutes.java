package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import dao.CategoriaDAO;
import dao.DevedorDAO;
import dao.ItemVendaDAO;
import dao.PagamentoVendaDAO;
import dao.VendaDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import model.Produto;
import model.Venda;
import service.BarracaService;
import service.StockService;
import util.VendaJson;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardRoutes {

    private final BarracaService service;
    private final VendaDAO vendaDAO;
    private final StockService stockService;
    private final CategoriaDAO categoriaDAO;
    private final DevedorDAO devedorDAO;

    public DashboardRoutes(BarracaService service, VendaDAO vendaDAO, StockService stockService,
                           CategoriaDAO categoriaDAO, DevedorDAO devedorDAO) {
        this.service = service;
        this.vendaDAO = vendaDAO;
        this.stockService = stockService;
        this.categoriaDAO = categoriaDAO;
        this.devedorDAO = devedorDAO;
    }

    public void register(Javalin app) {
        app.get("/api/dashboard", this::getDashboard);
    }

    private void getDashboard(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        LocalDate hoje = util.Fuso.hoje();
        LocalDate inicio = ApiHelpers.dataQuery(ctx, "inicio");
        LocalDate fim = ApiHelpers.dataQuery(ctx, "fim");
        boolean filtroPersonalizado = (inicio != null || fim != null);
        if (inicio == null) inicio = hoje.minusDays(6);
        if (fim == null) fim = hoje;
        if (inicio.isAfter(fim)) {
            LocalDate tmp = inicio;
            inicio = fim;
            fim = tmp;
        }

        Map<LocalDate, BigDecimal> totais;
        try {
            totais = vendaDAO.totaisPorDia(inicio, fim, uid);
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar totaisPorDia: " + e.getMessage());
            totais = Collections.emptyMap();
        }

        List<Map<String, Object>> vendasPorDia = new ArrayList<>();
        long diasDiff = java.time.temporal.ChronoUnit.DAYS.between(inicio, fim);
        if (diasDiff <= 90) {
            for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dia", dia.format(DateTimeFormatter.ofPattern("dd/MM")));
                item.put("dataIso", dia.toString());
                item.put("total", ApiHelpers.decimalJson(totais.getOrDefault(dia, BigDecimal.ZERO), 2));
                vendasPorDia.add(item);
            }
        } else {
            for (Map.Entry<LocalDate, BigDecimal> entry : totais.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dia", entry.getKey().format(DateTimeFormatter.ofPattern("dd/MM")));
                item.put("dataIso", entry.getKey().toString());
                item.put("total", ApiHelpers.decimalJson(entry.getValue(), 2));
                vendasPorDia.add(item);
            }
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
        BigDecimal totalVendasPeriodo;
        BigDecimal lucroPeriodo;
        try {
            totalVendasPeriodo = vendaDAO.totalVendasPeriodo(inicio, fim, uid);
            lucroPeriodo = vendaDAO.lucroEstimadoPeriodo(inicio, fim, uid);
        } catch (Exception e) {
            totalVendasPeriodo = BigDecimal.ZERO;
            lucroPeriodo = BigDecimal.ZERO;
        }

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

        dashboard.put("inicio", inicio.toString());
        dashboard.put("fim", fim.toString());
        dashboard.put("filtroPersonalizado", filtroPersonalizado);
        dashboard.put("totalVendasHoje", ApiHelpers.decimalJson(totalVendasHoje, 2));
        dashboard.put("lucroHoje", ApiHelpers.decimalJson(lucroHoje, 2));
        dashboard.put("lucroUltimos7Dias", ApiHelpers.decimalJson(lucro7Dias, 2));
        dashboard.put("totalVendasPeriodo", ApiHelpers.decimalJson(totalVendasPeriodo, 2));
        dashboard.put("lucroPeriodo", ApiHelpers.decimalJson(lucroPeriodo, 2));
        dashboard.put("valorTotalStockCusto", ApiHelpers.decimalJson(valorTotalStockCusto, 2));
        try { dashboard.put("totalProdutos", service.totalProdutos(uid)); } catch (Exception e) { dashboard.put("totalProdutos", 0L); }
        try { dashboard.put("totalCategorias", categoriaDAO.contarTodos(uid)); } catch (Exception e) { dashboard.put("totalCategorias", 0L); }
        try { dashboard.put("totalDevedores", devedorDAO.contarTodos(uid)); } catch (Exception e) { dashboard.put("totalDevedores", 0L); }
        dashboard.put("alertasStock", alertas != null ? alertas : Collections.emptyList());

        Map<String, String> metodoFormatado = new LinkedHashMap<>();
        try {
            Map<String, BigDecimal> totaisMetodo = new PagamentoVendaDAO().totaisPorMetodo(uid, inicio, fim);
            if (totaisMetodo != null) {
                totaisMetodo.forEach((k, v) -> metodoFormatado.put(k, ApiHelpers.decimalJson(v != null ? v : BigDecimal.ZERO, 2)));
            }
        } catch (Exception e) {
            System.err.println("[DASHBOARD] Falha ao carregar vendasPorMetodo: " + e.getMessage());
        }
        dashboard.put("vendasPorMetodo", metodoFormatado);

        List<Map<String, Object>> maisVendidos = new ArrayList<>();
        try {
            List<Object[]> maisVendidosRaw = new ItemVendaDAO().produtosMaisVendidos(uid, inicio, fim, 5);
            if (maisVendidosRaw != null) {
                for (Object[] r : maisVendidosRaw) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("produtoId", r[0]);
                    item.put("nome", r[1]);
                    item.put("quantidade", ApiHelpers.decimalJson(ApiHelpers.toBigDecimal(r[2]), 3));
                    item.put("quantidadeTotal", ApiHelpers.decimalJson(ApiHelpers.toBigDecimal(r[2]), 3));
                    item.put("total", ApiHelpers.decimalJson(ApiHelpers.toBigDecimal(r[3]), 2));
                    item.put("valorTotal", ApiHelpers.decimalJson(ApiHelpers.toBigDecimal(r[3]), 2));
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
        dashboard.put("perdasMes", ApiHelpers.decimalJson(perdasMes != null ? perdasMes : BigDecimal.ZERO, 2));

        ctx.json(dashboard);
    }

    private Map<String, Object> produtoStockJson(Produto produto) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", produto.getId());
        resultado.put("nome", produto.getNome());
        resultado.put("stock", ApiHelpers.decimalJson(produto.getQuantidadeStock(), 3));
        resultado.put("stockMinimo", ApiHelpers.decimalJson(produto.getStockMinimo(), 3));
        resultado.put("stockMaximo", produto.getStockMaximo() != null ? ApiHelpers.decimalJson(produto.getStockMaximo(), 3) : null);
        return resultado;
    }

    private Map<String, Object> vendaJson(Venda venda) {
        return VendaJson.venda(venda);
    }
}
