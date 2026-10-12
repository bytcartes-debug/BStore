package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import dao.ProdutoDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.MovimentoStock;
import model.Produto;
import service.BarracaService;
import service.StockService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProdutoRoutes {

    private final BarracaService service;
    private final ProdutoDAO produtoDAO;
    private final StockService stockService;

    public ProdutoRoutes(BarracaService service, ProdutoDAO produtoDAO, StockService stockService) {
        this.service = service;
        this.produtoDAO = produtoDAO;
        this.stockService = stockService;
    }

    public void register(Javalin app) {
        app.get("/api/produtos", this::listarProdutos);
        app.get("/api/produtos/barcode/{codigo}", this::buscarPorCodigoBarras);
        app.post("/api/produtos", this::criarProduto);
        app.put("/api/produtos/{id}", this::atualizarProduto);
        app.delete("/api/produtos/{id}", this::deletarProduto);
        app.get("/api/produtos/{id}/movimentos", this::listarMovimentosProduto);

        app.post("/api/stock/entradas", this::registarEntradaStock);
        app.post("/api/stock/ajustes", this::registarAjusteStock);
        app.get("/api/stock/contagem/previa", this::previaContagem);
        app.post("/api/stock/contagem/previa", this::previaContagem);
        app.post("/api/stock/contagem", this::confirmarContagem);
        app.get("/api/stock/exportar", this::exportarStockCsv);
        app.get("/api/stock/movimentos", this::listarMovimentosStock);
        app.get("/api/stock/integridade/{id}", this::verificarIntegridadeStock);
        app.get("/api/stock/falta-repor", this::obterProdutosFaltaRepor);
    }

    private void listarProdutos(Context ctx) {
        boolean incluirArquivados = "true".equalsIgnoreCase(ctx.queryParam("incluirArquivados"));
        if (ctx.queryParam("page") != null) {
            ctx.json(produtoDAO.listarPagina(ApiSecurity.utilizadorId(ctx),
                ApiHelpers.inteiroQuery(ctx, "page", 1),
                ApiHelpers.inteiroQuery(ctx, "pageSize", 25),
                ctx.queryParam("q"),
                ApiHelpers.longQuery(ctx, "categoriaId"),
                ApiHelpers.queryPadrao(ctx, "stock", "all"),
                ApiHelpers.queryPadrao(ctx, "sort", "name"),
                incluirArquivados)
                .map(this::produtoJson));
            return;
        }
        ctx.json(service.listarProdutos(ApiSecurity.utilizadorId(ctx), incluirArquivados).stream()
            .map(this::produtoJson).collect(Collectors.toList()));
    }

    private void exportarStockCsv(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        byte[] bytes = service.exportarStockCsv(uid);
        String nomeFicheiro = "stock_inventario_" + util.Fuso.hoje() + ".csv";
        ctx.contentType("text/csv; charset=UTF-8")
           .header("Content-Disposition", "attachment; filename=\"" + nomeFicheiro + "\"")
           .result(bytes);
    }

    private void criarProduto(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        BigDecimal custo = body.containsKey("custo") ? ApiHelpers.decimalObrigatorio(body, "custo") : BigDecimal.ZERO;
        BigDecimal stockMaximo = (body.containsKey("stockMaximo") && body.get("stockMaximo") != null && !body.get("stockMaximo").toString().isBlank())
            ? ApiHelpers.decimalObrigatorio(body, "stockMaximo") : null;
        Produto produto = service.criarProduto(
            ApiHelpers.textoObrigatorio(body, "nome"),
            ApiHelpers.decimalObrigatorio(body, "preco"),
            custo,
            ApiHelpers.decimalComPadrao(body, "stock", "0"),
            ApiHelpers.textoComPadrao(body, "unidade", "un"),
            ApiHelpers.decimalComPadrao(body, "stockMinimo", "5"),
            stockMaximo,
            ApiHelpers.longObrigatorio(body, "categoriaId"),
            ApiHelpers.texto(body, "codigoBarras"),
            uid
        );
        ctx.status(HttpStatus.CREATED).json(produtoJson(produto));
    }

    private void atualizarProduto(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        BigDecimal custo = body.containsKey("custo") ? ApiHelpers.decimalObrigatorio(body, "custo") : null;
        BigDecimal stock = body.containsKey("stock") ? ApiHelpers.decimalObrigatorio(body, "stock") : null;
        BigDecimal stockMaximo = (body.containsKey("stockMaximo") && body.get("stockMaximo") != null && !body.get("stockMaximo").toString().isBlank())
            ? ApiHelpers.decimalObrigatorio(body, "stockMaximo") : null;
        Produto produto = service.actualizarProduto(
            ApiHelpers.idPath(ctx),
            ApiHelpers.textoObrigatorio(body, "nome"),
            ApiHelpers.decimalObrigatorio(body, "preco"),
            custo,
            stock,
            ApiHelpers.textoComPadrao(body, "unidade", "un"),
            ApiHelpers.decimalComPadrao(body, "stockMinimo", "5"),
            stockMaximo,
            ApiHelpers.longObrigatorio(body, "categoriaId"),
            ApiHelpers.texto(body, "codigoBarras"),
            uid
        );
        ctx.json(produtoJson(produto));
    }

    private void buscarPorCodigoBarras(Context ctx) {
        Produto produto = service.buscarPorCodigoBarras(ctx.pathParam("codigo"), ApiSecurity.utilizadorId(ctx));
        if (produto == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
        }
        ctx.json(produtoJson(produto));
    }

    private void deletarProduto(Context ctx) {
        service.eliminarProduto(ApiHelpers.idPath(ctx), ApiSecurity.utilizadorId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void obterProdutosFaltaRepor(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        ctx.json(service.obterProdutosFaltaRepor(uid));
    }

    @SuppressWarnings("unchecked")
    private void registarEntradaStock(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Object rawBody = ctx.bodyAsClass(Object.class);
        List<Map<String, Object>> itens = new ArrayList<>();
        String motivoGeral = "Entrada de stock";

        if (rawBody instanceof List) {
            for (Object obj : (List<?>) rawBody) {
                if (obj instanceof Map) itens.add((Map<String, Object>) obj);
            }
        } else if (rawBody instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rawBody;
            if (map.containsKey("itens") && map.get("itens") instanceof List) {
                if (map.containsKey("motivo") && map.get("motivo") != null) motivoGeral = map.get("motivo").toString();
                for (Object obj : (List<?>) map.get("itens")) {
                    if (obj instanceof Map) itens.add((Map<String, Object>) obj);
                }
            } else {
                itens.add(map);
            }
        }

        if (itens.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para entrada de stock.");
        }

        List<MovimentoStock> movimentos = stockService.registarEntradaLote(uid, itens, motivoGeral, uid);
        ctx.status(HttpStatus.CREATED).json(movimentos.stream().map(this::movimentoJson).collect(Collectors.toList()));
    }

    private void registarAjusteStock(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Long produtoId = ApiHelpers.longObrigatorio(body, "produtoId");
        BigDecimal novaQtd = body.containsKey("novaQuantidade") && body.get("novaQuantidade") != null
                ? ApiHelpers.decimalObrigatorio(body, "novaQuantidade") : null;
        BigDecimal diferenca = body.containsKey("diferenca") && body.get("diferenca") != null
                ? ApiHelpers.decimalObrigatorio(body, "diferenca") : null;
        String tipo = body.containsKey("tipo") && body.get("tipo") != null ? body.get("tipo").toString() : null;
        String motivo = ApiHelpers.textoObrigatorio(body, "motivo");

        MovimentoStock mov = stockService.registarAjuste(uid, produtoId, novaQtd, diferenca, tipo, motivo, uid);
        if (mov == null) {
            ctx.status(HttpStatus.OK).json(Map.of("mensagem", "Sem alteração de stock"));
        } else {
            ctx.status(HttpStatus.CREATED).json(movimentoJson(mov));
        }
    }

    @SuppressWarnings("unchecked")
    private void previaContagem(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        List<Map<String, Object>> contagens = new ArrayList<>();
        if ("POST".equalsIgnoreCase(ctx.method().name()) || (ctx.body() != null && !ctx.body().isBlank())) {
            Object raw = ctx.bodyAsClass(Object.class);
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) {
                    if (o instanceof Map) contagens.add((Map<String, Object>) o);
                }
            } else if (raw instanceof Map && ((Map<?, ?>) raw).containsKey("itens")) {
                for (Object o : (List<?>) ((Map<?, ?>) raw).get("itens")) {
                    if (o instanceof Map) contagens.add((Map<String, Object>) o);
                }
            }
        }
        ctx.json(stockService.previaContagem(uid, contagens));
    }

    @SuppressWarnings("unchecked")
    private void confirmarContagem(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Object raw = ctx.bodyAsClass(Object.class);
        List<Map<String, Object>> contagens = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map) contagens.add((Map<String, Object>) o);
            }
        } else if (raw instanceof Map && ((Map<?, ?>) raw).containsKey("itens")) {
            for (Object o : (List<?>) ((Map<?, ?>) raw).get("itens")) {
                if (o instanceof Map) contagens.add((Map<String, Object>) o);
            }
        }
        List<MovimentoStock> movs = stockService.confirmarContagem(uid, contagens, uid);
        ctx.status(HttpStatus.CREATED).json(movs.stream().map(this::movimentoJson).collect(Collectors.toList()));
    }

    private void listarMovimentosProduto(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long produtoId = ApiHelpers.idPath(ctx);
        int page = ApiHelpers.inteiroQuery(ctx, "page", 1);
        int pageSize = ApiHelpers.inteiroQuery(ctx, "pageSize", 25);
        ctx.json(stockService.listarMovimentosPorProduto(uid, produtoId, page, pageSize).map(this::movimentoJson));
    }

    private void listarMovimentosStock(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        int page = ApiHelpers.inteiroQuery(ctx, "page", 1);
        int pageSize = ApiHelpers.inteiroQuery(ctx, "pageSize", 25);
        String tipo = ctx.queryParam("tipo");
        LocalDate inicioData = ApiHelpers.dataQuery(ctx, "inicio");
        LocalDate fimData = ApiHelpers.dataQuery(ctx, "fim");
        LocalDateTime inicio = inicioData != null ? inicioData.atStartOfDay() : null;
        LocalDateTime fim = fimData != null ? fimData.atTime(23, 59, 59) : null;
        ctx.json(stockService.listarMovimentos(uid, page, pageSize, tipo, inicio, fim).map(this::movimentoJson));
    }

    private void verificarIntegridadeStock(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long produtoId = ApiHelpers.idPath(ctx);
        boolean integro = stockService.verificarIntegridadeStock(uid, produtoId);
        ctx.json(Map.of("produtoId", produtoId, "integro", integro));
    }

    private Map<String, Object> produtoJson(Produto produto) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", produto.getId());
        resultado.put("nome", produto.getNome());
        resultado.put("preco", ApiHelpers.decimalJson(produto.getPreco(), 2));
        BigDecimal custo = produto.getCusto() != null ? produto.getCusto() : BigDecimal.ZERO;
        resultado.put("custo", ApiHelpers.decimalJson(custo, 2));
        BigDecimal margem = BigDecimal.ZERO;
        if (produto.getPreco() != null && produto.getPreco().compareTo(BigDecimal.ZERO) > 0) {
            margem = produto.getPreco().subtract(custo)
                .divide(produto.getPreco(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
        }
        resultado.put("margem", ApiHelpers.decimalJson(margem, 2));
        resultado.put("ativo", produto.isAtivo());
        resultado.put("stock", ApiHelpers.decimalJson(produto.getQuantidadeStock(), 3));
        resultado.put("stockMinimo", ApiHelpers.decimalJson(produto.getStockMinimo(), 3));
        resultado.put("stockMaximo", produto.getStockMaximo() != null ? ApiHelpers.decimalJson(produto.getStockMaximo(), 3) : null);
        resultado.put("unidade", produto.getUnidade());
        resultado.put("codigoBarras", produto.getCodigoBarras());
        resultado.put("categoriaId", produto.getCategoria() == null ? null : produto.getCategoria().getId());
        resultado.put("categoriaNome", produto.getCategoria() == null ? "" : produto.getCategoria().getNome());
        return resultado;
    }

    private Map<String, Object> movimentoJson(MovimentoStock m) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", m.getId());
        res.put("produtoId", m.getProduto() != null ? m.getProduto().getId() : null);
        res.put("produtoNome", m.getProduto() != null ? m.getProduto().getNome() : "");
        res.put("tipo", m.getTipo());
        res.put("quantidade", ApiHelpers.decimalJson(m.getQuantidade(), 3));
        res.put("custoUnitario", ApiHelpers.decimalJson(m.getCustoUnitario(), 2));
        res.put("motivo", m.getMotivo() == null ? "" : m.getMotivo());
        res.put("referenciaTipo", m.getReferenciaTipo());
        res.put("referenciaId", m.getReferenciaId());
        res.put("criadoEm", m.getCriadoEm() != null ? m.getCriadoEm().toString() : null);
        return res;
    }
}
