package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dao.DevedorDAO;
import dao.UsuarioDAO;
import dao.CategoriaDAO;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import model.*;
import service.BarracaService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ApiServer {

    private final BarracaService service      = new BarracaService();
    private final DevedorDAO     devedorDAO   = new DevedorDAO();
    private final CategoriaDAO   categoriaDAO = new CategoriaDAO();
    private final UsuarioDAO     usuarioDAO   = new UsuarioDAO();
    private final Javalin        app;

    public ApiServer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        this.app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
            config.staticFiles.add("/public", io.javalin.http.staticfiles.Location.CLASSPATH);
        });

        configurarRotas();
    }

    public void start(int port) { app.start(port); System.out.println("[FlexStock] Servidor a correr em http://localhost:" + port); }
    public void stop()          { app.stop(); }

    // ── Helpers ───────────────────────────────────────────────────────
    private Long getUserId(Context ctx) {
        String h = ctx.header("X-User-Id");
        if (h == null || h.isBlank()) throw new IllegalStateException("Sem autorização");
        return Long.parseLong(h);
    }

    private boolean isSuperuser(Context ctx) {
        String role = ctx.header("X-Role");
        return "superuser".equals(role);
    }

    private void configurarRotas() {
        // ── Dashboard ──────────────────────────────────────────────────
        app.get("/api/dashboard", this::getDashboard);

        // ── Categorias ─────────────────────────────────────────────────
        app.get("/api/categorias",              this::listarCategorias);
        app.get("/api/categorias/{id}/produtos", this::listarProdutosPorCategoria);
        app.post("/api/categorias",             this::criarCategoria);
        app.put("/api/categorias/{id}",         this::atualizarCategoria);
        app.delete("/api/categorias/{id}",      this::deletarCategoria);

        // ── Produtos ───────────────────────────────────────────────────
        app.get("/api/produtos",           this::listarProdutos);
        app.post("/api/produtos",          this::criarProduto);
        app.put("/api/produtos/{id}",      this::atualizarProduto);
        app.delete("/api/produtos/{id}",   this::deletarProduto);

        // ── Vendas ─────────────────────────────────────────────────────
        app.get("/api/vendas",             this::listarVendas);
        app.post("/api/vendas",            this::registarVenda);
        app.post("/api/vendas/lote",       this::registarVendaLote);

        // ── Devedores ──────────────────────────────────────────────────
        app.get("/api/devedores",          this::listarDevedores);
        app.post("/api/devedores",         this::criarDevedor);
        app.delete("/api/devedores/{id}",  this::deletarDevedor);

        // ── Auth ───────────────────────────────────────────────────────
        app.post("/api/auth/login",        this::login);

        // ── Utilizadores (só superuser) ────────────────────────────────
        app.get("/api/usuarios",           this::listarUsuarios);
        app.post("/api/usuarios",          this::criarUsuario);
        app.put("/api/usuarios/{id}",      this::atualizarUsuario);
        app.delete("/api/usuarios/{id}",   this::deletarUsuario);

        // ── SPA Fallback ───────────────────────────────────────────────
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
            Long uid = getUserId(ctx);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
            List<Map<String, Object>> vendasPorDia = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate dia = LocalDate.now().minusDays(i);
                double total = service.totalVendasPeriodo(dia, dia, uid);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dia", dia.format(fmt));
                item.put("total", total);
                vendasPorDia.add(item);
            }

            List<Produto> alertas = service.produtosComStockBaixo(uid);
            List<Map<String, Object>> alertasJson = alertas.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId()); m.put("nome", p.getNome());
                m.put("stock", p.getQuantidadeStock()); m.put("stockMinimo", p.getStockMinimo());
                return m;
            }).collect(Collectors.toList());

            List<Venda> recentes = service.listarVendas(uid).stream().limit(10).collect(Collectors.toList());
            List<Map<String, Object>> recentesJson = recentes.stream().map(v -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", v.getId()); m.put("produto", v.getProduto().getNome());
                m.put("quantidade", v.getQuantidade()); m.put("total", v.getTotal());
                m.put("data", v.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                return m;
            }).collect(Collectors.toList());

            long totalDevedores = devedorDAO.listarTodos(uid).size();

            Map<String, Object> dashboard = new LinkedHashMap<>();
            dashboard.put("totalVendasHoje", service.totalVendasHoje(uid));
            dashboard.put("totalProdutos",   service.totalProdutos(uid));
            dashboard.put("totalCategorias", service.listarCategorias(uid).size());
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
        try {
            Long uid = getUserId(ctx);
            List<Categoria> cats = service.listarCategorias(uid);
            List<Map<String, Object>> result = cats.stream().map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",            c.getId());
                m.put("nome",          c.getNome());
                m.put("descricao",     c.getDescricao() != null ? c.getDescricao() : "");
                m.put("icone",         "🏷️");
                m.put("totalProdutos", categoriaDAO.contarProdutos(c.getId(), uid));
                return m;
            }).collect(Collectors.toList());
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    private void listarProdutosPorCategoria(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Long id  = Long.parseLong(ctx.pathParam("id"));
            List<Produto> prods = service.listarProdutosPorCategoria(id, uid);
            List<Map<String, Object>> result = prods.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",   p.getId());
                m.put("nome", p.getNome());
                m.put("preco", p.getPreco());
                m.put("stock", p.getQuantidadeStock());
                return m;
            }).collect(Collectors.toList());
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    private void criarCategoria(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            Categoria c = service.criarCategoria(body.get("nome"), body.get("descricao"), uid);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", c.getId(), "nome", c.getNome()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void atualizarCategoria(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            Categoria c = service.buscarCategoria(id);
            if (c == null || !uid.equals(c.getUsuarioId())) { ctx.status(HttpStatus.NOT_FOUND); return; }
            c.setNome(body.get("nome")); c.setDescricao(body.get("descricao"));
            service.actualizarCategoria(c);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void deletarCategoria(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Long id = Long.parseLong(ctx.pathParam("id"));
            service.eliminarCategoria(id, uid);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PRODUTOS
    // ═══════════════════════════════════════════════════════════════════
    private void listarProdutos(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            List<Produto> produtos = service.listarProdutos(uid);
            List<Map<String, Object>> result = produtos.stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",           p.getId());
                m.put("nome",         p.getNome());
                m.put("preco",        p.getPreco());
                m.put("custo",        0.0);
                m.put("stock",        p.getQuantidadeStock());
                m.put("stockMinimo",  p.getStockMinimo());
                m.put("unidade",      p.getUnidade() != null ? p.getUnidade() : "un");
                m.put("categoriaId",  p.getCategoria() != null ? p.getCategoria().getId() : null);
                m.put("categoriaNome", p.getCategoria() != null ? p.getCategoria().getNome() : "");
                return m;
            }).collect(Collectors.toList());
            ctx.json(result);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    private void criarProduto(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String nome      = (String) body.get("nome");
            Double preco     = ((Number) body.get("preco")).doubleValue();
            Double stock     = body.get("stock") != null ? ((Number) body.get("stock")).doubleValue() : 0.0;
            Double stockMin  = body.get("stockMinimo") != null ? ((Number) body.get("stockMinimo")).doubleValue() : 5.0;
            String unidade   = (String) body.getOrDefault("unidade", "un");
            Long catId       = ((Number) body.get("categoriaId")).longValue();
            Categoria cat    = service.buscarCategoria(catId);
            Produto p = service.criarProduto(nome, preco, stock, unidade, stockMin, cat, uid);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", p.getId()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void atualizarProduto(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Produto p = service.listarProdutos(uid).stream()
                    .filter(x -> x.getId().equals(id)).findFirst().orElse(null);
            if (p == null) { ctx.status(HttpStatus.NOT_FOUND); return; }
            p.setNome((String) body.get("nome"));
            p.setPreco(((Number) body.get("preco")).doubleValue());
            p.setQuantidadeStock(body.get("stock") != null ? ((Number) body.get("stock")).doubleValue() : 0.0);
            p.setStockMinimo(body.get("stockMinimo") != null ? ((Number) body.get("stockMinimo")).doubleValue() : 5.0);
            if (body.get("unidade") != null) p.setUnidade((String) body.get("unidade"));
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
        try {
            Long uid = getUserId(ctx);
            List<Venda> vendas = service.listarVendas(uid);
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
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    private void registarVenda(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Long produtoId = ((Number) body.get("produtoId")).longValue();
            double qtd     = ((Number) body.get("quantidade")).doubleValue();
            Venda v = service.registarVenda(produtoId, qtd, null, uid);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", v.getId(), "total", v.getTotal()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void registarVendaLote(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            List<Map<String, Object>> itens = ctx.bodyAsClass(List.class);
            if (itens == null || itens.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", "Carrinho está vazio")); return;
            }
            Map<String, Object> resultado = service.registarVendaLote(itens, uid);
            ctx.status(HttpStatus.CREATED).json(resultado);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DEVEDORES
    // ═══════════════════════════════════════════════════════════════════
    private void listarDevedores(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            List<Devedor> devedores = devedorDAO.listarTodos(uid);
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
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    private void criarDevedor(Context ctx) {
        try {
            Long uid = getUserId(ctx);
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Devedor d = new Devedor();
            d.setNome((String) body.get("nome"));
            d.setDivida(((Number) body.get("divida")).doubleValue());
            d.setDescricao((String) body.getOrDefault("descricao", ""));
            d.setData(LocalDate.now());
            d.setUsuarioId(uid);
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

    // ═══════════════════════════════════════════════════════════════════
    //  AUTH
    // ═══════════════════════════════════════════════════════════════════
    private void login(Context ctx) {
        try {
            Map<String, String> body = ctx.bodyAsClass(Map.class);
            String email = body.get("email");
            String senha = body.get("password");
            if (email == null || senha == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", "Email e senha obrigatórios")); return;
            }
            Usuario u = usuarioDAO.buscarPorEmail(email);
            if (u == null || !u.verificarSenha(senha)) {
                ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("erro", "Email ou senha incorretos")); return;
            }
            if (u.isExpirado()) {
                ctx.status(HttpStatus.FORBIDDEN).json(Map.of("erro", "Conta expirada. Contacte o administrador.")); return;
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id",            u.getId());
            resp.put("nome",          u.getNome());
            resp.put("email",         u.getEmail());
            resp.put("role",          u.getRole());
            resp.put("diasRestantes", u.diasRestantes()); // -1 = permanente
            ctx.json(resp);
        } catch (Exception e) {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("erro", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILIZADORES (só superuser)
    // ═══════════════════════════════════════════════════════════════════
    private void listarUsuarios(Context ctx) {
        if (!isSuperuser(ctx)) { ctx.status(HttpStatus.FORBIDDEN).json(Map.of("erro", "Acesso negado")); return; }
        List<Usuario> lista = usuarioDAO.listarTodos();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<Map<String, Object>> result = lista.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",             u.getId());
            m.put("nome",           u.getNome());
            m.put("email",          u.getEmail());
            m.put("role",           u.getRole());
            m.put("diasAcesso",     u.getDiasAcesso());
            m.put("dataExpiracao",  u.getDataExpiracao() != null ? u.getDataExpiracao().format(fmt) : null);
            m.put("diasRestantes",  u.diasRestantes());
            m.put("expirado",       u.isExpirado());
            return m;
        }).collect(Collectors.toList());
        ctx.json(result);
    }

    private void criarUsuario(Context ctx) {
        if (!isSuperuser(ctx)) { ctx.status(HttpStatus.FORBIDDEN).json(Map.of("erro", "Acesso negado")); return; }
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String nome  = (String) body.get("nome");
            String email = (String) body.get("email");
            String senha = (String) body.get("password");
            String role  = (String) body.getOrDefault("role", "operator");
            int diasAcesso = body.get("diasAcesso") != null ? ((Number) body.get("diasAcesso")).intValue() : 30;

            if (nome == null || email == null || senha == null || senha.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", "Nome, email e senha são obrigatórios")); return;
            }
            if (usuarioDAO.buscarPorEmail(email) != null) {
                ctx.status(HttpStatus.CONFLICT).json(Map.of("erro", "Já existe um utilizador com este email")); return;
            }
            Usuario u = new Usuario(nome, email, senha, role);
            u.aplicarDiasAcesso(diasAcesso);
            u = usuarioDAO.salvar(u);
            ctx.status(HttpStatus.CREATED).json(Map.of("id", u.getId(), "nome", u.getNome()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void atualizarUsuario(Context ctx) {
        if (!isSuperuser(ctx)) { ctx.status(HttpStatus.FORBIDDEN).json(Map.of("erro", "Acesso negado")); return; }
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Usuario u = usuarioDAO.buscarPorId(id);
            if (u == null) { ctx.status(HttpStatus.NOT_FOUND).json(Map.of("erro", "Utilizador não encontrado")); return; }
            if (body.get("nome")     != null) u.setNome((String) body.get("nome"));
            if (body.get("email")    != null) u.setEmail((String) body.get("email"));
            if (body.get("role")     != null) u.setRole((String) body.get("role"));
            if (body.get("password") != null && !((String) body.get("password")).isEmpty())
                u.setSenha((String) body.get("password"));
            // Renovar/alterar dias de acesso
            if (body.get("diasAcesso") != null) {
                int dias = ((Number) body.get("diasAcesso")).intValue();
                u.aplicarDiasAcesso(dias);
            }
            usuarioDAO.actualizar(u);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }

    private void deletarUsuario(Context ctx) {
        if (!isSuperuser(ctx)) { ctx.status(HttpStatus.FORBIDDEN).json(Map.of("erro", "Acesso negado")); return; }
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            if (usuarioDAO.contarTodos() <= 1) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", "Não é possível apagar o único utilizador")); return;
            }
            usuarioDAO.eliminar(id);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("erro", e.getMessage()));
        }
    }
}
