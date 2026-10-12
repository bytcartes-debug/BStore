package api.routes;

import api.ApiHelpers;
import api.ApiSecurity;
import dao.CategoriaDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import model.Categoria;
import model.Produto;
import service.BarracaService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CategoriaRoutes {

    private final BarracaService service;
    private final CategoriaDAO categoriaDAO;

    public CategoriaRoutes(BarracaService service, CategoriaDAO categoriaDAO) {
        this.service = service;
        this.categoriaDAO = categoriaDAO;
    }

    public void register(Javalin app) {
        app.get("/api/categorias", this::listarCategorias);
        app.get("/api/categorias/{id}/produtos", this::listarProdutosPorCategoria);
        app.post("/api/categorias", this::criarCategoria);
        app.put("/api/categorias/{id}", this::atualizarCategoria);
        app.delete("/api/categorias/{id}", this::deletarCategoria);
    }

    private void listarCategorias(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<Long, Long> contagens = categoriaDAO.contarProdutosPorCategoria(uid);
        List<Map<String, Object>> resultado = service.listarCategorias(uid).stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("nome", c.getNome());
            item.put("descricao", c.getDescricao() == null ? "" : c.getDescricao());
            item.put("icone", "categoria");
            item.put("totalProdutos", contagens.getOrDefault(c.getId(), 0L));
            return item;
        }).collect(Collectors.toList());
        ctx.json(resultado);
    }

    private void listarProdutosPorCategoria(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Long categoriaId = ApiHelpers.idPath(ctx);
        if (service.buscarCategoria(categoriaId, uid) == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        ctx.json(service.listarProdutosPorCategoria(categoriaId, uid).stream()
            .map(this::produtoJson).collect(Collectors.toList()));
    }

    private void criarCategoria(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        Categoria categoria = service.criarCategoria(ApiHelpers.textoObrigatorio(body, "nome"), ApiHelpers.texto(body, "descricao"), uid);
        ctx.status(HttpStatus.CREATED).json(categoriaJson(categoria, uid));
    }

    private void atualizarCategoria(Context ctx) {
        Long uid = ApiSecurity.utilizadorId(ctx);
        Categoria categoria = service.buscarCategoria(ApiHelpers.idPath(ctx), uid);
        if (categoria == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        Map<String, Object> body = ApiHelpers.corpo(ctx);
        categoria.setNome(ApiHelpers.textoObrigatorio(body, "nome"));
        categoria.setDescricao(ApiHelpers.texto(body, "descricao"));
        ctx.json(categoriaJson(service.actualizarCategoria(categoria, uid), uid));
    }

    private void deletarCategoria(Context ctx) {
        service.eliminarCategoria(ApiHelpers.idPath(ctx), ApiSecurity.utilizadorId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Map<String, Object> categoriaJson(Categoria categoria, Long usuarioId) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", categoria.getId());
        resultado.put("nome", categoria.getNome());
        resultado.put("descricao", categoria.getDescricao() == null ? "" : categoria.getDescricao());
        resultado.put("icone", "categoria");
        resultado.put("totalProdutos", categoriaDAO.contarProdutos(categoria.getId(), usuarioId));
        return resultado;
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
}
