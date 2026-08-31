package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dao.DevedorDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import model.Categoria;
import model.Devedor;
import model.Produto;
import model.Venda;
import service.BarracaService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ApiServer {

    private final BarracaService service = new BarracaService();
    private final DevedorDAO devedorDAO  = new DevedorDAO();
    private final Javalin app;

    public ApiServer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        this.app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));

            // CORS — permite acesso de qualquer origem (celular, web, etc.)
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));

            // Serve os ficheiros estáticos do React (build do frontend)
            config.staticFiles.add("/public", io.javalin.http.staticfiles.Location.CLASSPATH);
        });

        configurarRotas();
    }

    public void start(int port) {
        app.start(port);
        System.out.println("[FlexStock] Servidor a correr em http://localhost:" + port);
    }

    public void stop() {
        app.stop();
    }

    private void configurarRotas() {
        // ── Dashboard ──────────────────────────────────────────────────
        app.get("/api/dashboard", this::getDashboard);

        // ── Categorias ─────────────────────────────────────────────────
        app.get("/api/categorias",        this::listarCategorias);
        app.post("/api/categorias",       this::criarCategoria);
        app.put("/api/categorias/{id}",   this::atualizarCategoria);
        app.delete("/api/categorias/{id}", this::deletarCategoria);

        // ── Produtos ───────────────────────────────────────────────────
        app.get("/api/produtos",          this::listarProdutos);
        app.post("/api/produtos",         this::criarProduto);
        app.put("/api/produtos/{id}",     this::atualizarProduto);
        app.delete("/api/produtos/{id}",  this::deletarProduto);

        // ── Vendas ─────────────────────────────────────────────────────
        app.get("/api/vendas",            this::listarVendas);
        app.post("/api/vendas",           this::registarVenda);

        // ── Devedores ──────────────────────────────────────────────────
        app.get("/api/devedores",         this::listarDevedores);
        app.post("/api/devedores",        this::criarDevedor);
        app.delete("/api/devedores/{id}", this::deletarDevedor);

        // ── SPA Fallback (React Router) ────────────────────────────────
        app.error(HttpStatus.NOT_FOUND, ctx -> {
            if (!ctx.path().startsWith("/api")) {
                ctx.result(getClass().getResourceAsStream("/public/index.html"));
                ctx.contentType("text/html");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DASHBOARD
    // ═══════════════════════════════════════════════════════════════════
    private void getDashboard(Context ctx) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
            List<Map<String, Object>> vendasPorDia = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate dia = LocalDate.now().minusDays(i);
                double total = service.totalVendasPeriodo(dia, dia);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dia", dia.format(fmt));
                item.put("total", total);
                vendasPorDia.add(item);
            }

            List<Produto> alertas = service.produtosComStockBaixo();
            List<Map<String, Object>> alertasJson = alertas.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("nome", p.getNome());
                m.put("stock", p.getQuantidadeStock());
                m.put("stockMinimo", p.getStockMinimo());
                return m;
            }).collect(Collectors.toList());

            List<Venda> recentes = service.listarVendas().stream().limit(10).collect(Collectors.toList());
            List<Map<String, Object>> recentesJson = recentes.stream().map(v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", v.getId());
                m.put("produto", v.getProduto().getNome());
                m.put("quantidade", v.getQuantidade());
                m.put("total", v.getTotal());
                m.put("data", v.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                return m;
            }).collect(Collectors.toList());

            long totalDevedores = devedorDAO.listarTodos().size();

            Map<String, Object> dashboard = new LinkedHashMap<>();
            dashboard.put("totalVendasHoje", service.totalVendasHoje());
            dashboard.put("totalProdutos",   service.totalProdutos());
            dashboard.put("totalCategorias", service.listarCategorias().size());
            dashboard.put("totalDevedores",  totalDevedores);
            dashboard.put("alertasStock",    alertasJson);
            dashboard.put("vendasRecentes",  recentesJson);
            dashboard.put("vendasPorDia",    vendasPorDia);

            ctx.json(dashboard);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CATEGORIAS
    // ═══════════════════════════════════════════════════════════════════
    private void listarCategorias(Context ctx) {
        List<Categoria> cats = service.listarCategorias();
        List<Map<String, Object>> result = cats.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       c.getId());
            m.put("nome",     c.getNome());
            m.put("descricao", c.getDescricao() != null ? c.getDescricao() : "");
            m.put("icone",    "🏷️");
            m.put("totalProdutos", c.getProdutos().size());
            return m;
        }).collect(Collectors.toList());
        ctx.json(result);
    }

    private void criarCategoria(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            Categoria c = service.criarCategoria(body.get("nome"), body.get("descricao"));
            ctx.status(HttpStatus.CREATED).json(Map.of("id", c.getId(), "nome", c.getNome()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void atualizarCategoria(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            Categoria c = service.buscarCategoria(id);
            if (c == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
            c.setNome(body.get("nome"));
            c.setDescricao(body.get("descricao"));
            service.actualizarCategoria(c);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void deletarCategoria(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            service.eliminarCategoria(id);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PRODUTOS
    // ═══════════════════════════════════════════════════════════════════
    private void listarProdutos(Context ctx) {
        List<Produto> produtos = service.listarProdutos();
        List<Map<String, Object>> result = produtos.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           p.getId());
            m.put("nome",         p.getNome());
            m.put("preco",        p.getPreco());
            m.put("custo",        0.0);
            m.put("stock",        p.getQuantidadeStock());
            m.put("stockMinimo",  p.getStockMinimo());
            m.put("categoriaId",  p.getCategoria() != null ? p.getCategoria().getId() : null);
            m.put("categoriaNome", p.getCategoria() != null ? p.getCategoria().getNome() : "");
            return m;
        }).collect(Collectors.toList());
        ctx.json(result);
    }

    private void criarProduto(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String nome        = (String) body.get("nome");
            Double preco       = ((Number) body.get("preco")).doubleValue();
            Integer stock      = ((Number) body.getOrDefault("stock", 0)).intValue();
            Integer stockMin   = ((Number) body.getOrDefault("stockMinimo", 5)).intValue();
            Long catId         = ((Number) body.get("categoriaId")).longValue();
            Categoria cat      = service.buscarCategoria(catId);
            Produto p = service.criarProduto(nome, preco, stock, "un", stockMin, cat);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", p.getId()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void atualizarProduto(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Produto p = service.listarProdutos().stream()
                    .filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (p == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
            p.setNome((String) body.get("nome"));
            p.setPreco(((Number) body.get("preco")).doubleValue());
            p.setQuantidadeStock(((Number) body.getOrDefault("stock", 0)).intValue());
            p.setStockMinimo(((Number) body.getOrDefault("stockMinimo", 5)).intValue());
            Long catId = ((Number) body.get("categoriaId")).longValue();
            p.setCategoria(service.buscarCategoria(catId));
            service.actualizarProduto(p);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void deletarProduto(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            service.eliminarProduto(id);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VENDAS
    // ═══════════════════════════════════════════════════════════════════
    private void listarVendas(Context ctx) {
        List<Venda> vendas = service.listarVendas();
        List<Map<String, Object>> result = vendas.stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",         v.getId());
            m.put("produto",    v.getProduto().getNome());
            m.put("quantidade", v.getQuantidade());
            m.put("total",      v.getTotal());
            m.put("data",       v.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            return m;
        }).collect(Collectors.toList());
        ctx.json(result);
    }

    private void registarVenda(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Long produtoId   = ((Number) body.get("produtoId")).longValue();
            Integer quantidade = ((Number) body.get("quantidade")).intValue();
            Venda v = service.registarVenda(produtoId, quantidade, null);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", v.getId(), "total", v.getTotal()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DEVEDORES
    // ═══════════════════════════════════════════════════════════════════
    private void listarDevedores(Context ctx) {
        List<Devedor> devedores = devedorDAO.listarTodos();
        List<Map<String, Object>> result = devedores.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        d.getId());
            m.put("nome",      d.getNome());
            m.put("divida",    d.getDivida());
            m.put("descricao", d.getDescricao() != null ? d.getDescricao() : "");
            m.put("data",      d.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            return m;
        }).collect(Collectors.toList());
        ctx.json(result);
    }

    private void criarDevedor(Context ctx) {
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Devedor d = new Devedor();
            d.setNome((String) body.get("nome"));
            d.setDivida(((Number) body.get("divida")).doubleValue());
            d.setDescricao((String) body.getOrDefault("descricao", ""));
            d.setData(LocalDate.now());
            d = devedorDAO.salvar(d);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", d.getId()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void deletarDevedor(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            devedorDAO.deletar(id);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }
}
