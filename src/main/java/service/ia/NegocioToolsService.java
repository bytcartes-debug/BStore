package service.ia;

import dao.DevedorDAO;
import dao.ProdutoDAO;
import dao.VendaDAO;
import model.Devedor;
import model.Produto;
import util.Fuso;
import util.JPAUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class NegocioToolsService {

    private final VendaDAO vendaDAO = new VendaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();

    public Map<String, Object> executarFerramenta(Long usuarioId, String nome, Map<String, Object> params) {
        if (nome == null) return Map.of("erro", "Nome de ferramenta nulo");

        switch (nome.trim().toLowerCase()) {
            case "vendas_por_periodo":
                return vendasPorPeriodo(usuarioId, params);
            case "lucro_por_produto":
                return lucroPorProduto(usuarioId, params);
            case "stock_baixo":
                return stockBaixo(usuarioId, params);
            case "devedores_maiores":
                return devedoresMaiores(usuarioId, params);
            case "comparar_periodos":
                return compararPeriodos(usuarioId, params);
            case "despesas_por_periodo":
                return despesasPorPeriodo(usuarioId, params);
            default:
                return Map.of("erro", "Ferramenta desconhecida: " + nome);
        }
    }

    private LocalDate parseData(Object val, LocalDate padrao) {
        if (val == null) return padrao;
        try {
            return LocalDate.parse(val.toString().trim());
        } catch (Exception e) {
            return padrao;
        }
    }

    private int parseLimite(Object val, int padrao, int maximo) {
        if (val == null) return padrao;
        try {
            int n = Integer.parseInt(val.toString().trim());
            return Math.max(1, Math.min(n, maximo));
        } catch (Exception e) {
            return padrao;
        }
    }

    public Map<String, Object> vendasPorPeriodo(Long usuarioId, Map<String, Object> params) {
        LocalDate hoje = Fuso.hoje();
        LocalDate inicio = parseData(params.get("inicio"), hoje.minusDays(7));
        LocalDate fim = parseData(params.get("fim"), hoje);

        if (inicio.isAfter(fim)) {
            LocalDate tmp = inicio;
            inicio = fim;
            fim = tmp;
        }
        if (inicio.isBefore(fim.minusDays(365))) {
            inicio = fim.minusDays(365);
        }

        final LocalDate in = inicio;
        final LocalDate fi = fim;

        return JPAUtil.emTransacao(usuarioId, em -> {
            LocalDateTime inicioTs = in.atStartOfDay();
            LocalDateTime fimTs = fi.plusDays(1).atStartOfDay();

            BigDecimal totalVendas = em.createQuery(
                "SELECT COALESCE(SUM(v.total), 0) FROM Venda v WHERE v.usuarioId = :uid " +
                "AND v.estado != 'ANULADA' AND v.criadaEm >= :inicio AND v.criadaEm < :fim", BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getSingleResult();

            Long contagem = em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.usuarioId = :uid " +
                "AND v.estado != 'ANULADA' AND v.criadaEm >= :inicio AND v.criadaEm < :fim", Long.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getSingleResult();

            BigDecimal totalLucro = vendaDAO.lucroEstimadoPeriodo(in, fi, usuarioId);

            BigDecimal ticketMedio = (contagem != null && contagem > 0)
                ? totalVendas.divide(BigDecimal.valueOf(contagem), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("inicio", in.toString());
            res.put("fim", fi.toString());
            res.put("totalVendas", totalVendas.setScale(2, RoundingMode.HALF_UP).toString());
            res.put("quantidadeVendas", contagem);
            res.put("totalLucro", totalLucro != null ? totalLucro.toString() : "0.00");
            res.put("ticketMedio", ticketMedio.toString());
            return res;
        });
    }

    public Map<String, Object> lucroPorProduto(Long usuarioId, Map<String, Object> params) {
        LocalDate hoje = Fuso.hoje();
        LocalDate inicio = parseData(params.get("inicio"), hoje.minusDays(30));
        LocalDate fim = parseData(params.get("fim"), hoje);
        int limite = parseLimite(params.get("limite"), 5, 20);

        return JPAUtil.emTransacao(usuarioId, em -> {
            LocalDateTime inicioTs = inicio.atStartOfDay();
            LocalDateTime fimTs = fim.plusDays(1).atStartOfDay();

            List<Object[]> rows = em.createQuery(
                "SELECT p.nome, " +
                "COALESCE(SUM(iv.quantidade), 0), " +
                "COALESCE(SUM(iv.total), 0), " +
                "COALESCE(SUM(iv.quantidade * iv.custoUnitario), 0) " +
                "FROM ItemVenda iv JOIN iv.produto p " +
                "WHERE iv.usuarioId = :uid AND iv.venda.estado != 'ANULADA' " +
                "AND iv.venda.criadaEm >= :inicio AND iv.venda.criadaEm < :fim " +
                "GROUP BY p.nome " +
                "ORDER BY (SUM(iv.total) - SUM(iv.quantidade * iv.custoUnitario)) DESC", Object[].class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .setMaxResults(limite)
                .getResultList();

            List<Map<String, Object>> lista = new ArrayList<>();
            for (Object[] r : rows) {
                String nome = (String) r[0];
                BigDecimal qtd = (BigDecimal) r[1];
                BigDecimal total = (BigDecimal) r[2];
                BigDecimal custo = (BigDecimal) r[3];
                BigDecimal lucro = total.subtract(custo).setScale(2, RoundingMode.HALF_UP);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("produto", nome);
                item.put("quantidadeVendida", qtd.setScale(2, RoundingMode.HALF_UP).toString());
                item.put("faturamento", total.setScale(2, RoundingMode.HALF_UP).toString());
                item.put("lucro", lucro.toString());
                lista.add(item);
            }

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("inicio", inicio.toString());
            res.put("fim", fim.toString());
            res.put("produtos", lista);
            return res;
        });
    }

    public Map<String, Object> stockBaixo(Long usuarioId, Map<String, Object> params) {
        int limite = parseLimite(params.get("limite"), 10, 50);

        List<Produto> produtos = produtoDAO.buscarStockBaixo(usuarioId);
        List<Map<String, Object>> lista = new ArrayList<>();

        for (int i = 0; i < Math.min(produtos.size(), limite); i++) {
            Produto p = produtos.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("produto", p.getNome());
            item.put("stockAtual", p.getQuantidadeStock() != null ? p.getQuantidadeStock().toString() : "0");
            item.put("stockMinimo", p.getStockMinimo() != null ? p.getStockMinimo().toString() : "0");
            item.put("precoVenda", p.getPreco() != null ? p.getPreco().toString() : "0.00");
            lista.add(item);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalProdutosComStockBaixo", produtos.size());
        res.put("itens", lista);
        return res;
    }

    public Map<String, Object> devedoresMaiores(Long usuarioId, Map<String, Object> params) {
        int limite = parseLimite(params.get("limite"), 5, 20);

        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Devedor> devedores = em.createQuery(
                "SELECT d FROM Devedor d WHERE d.usuarioId = :uid AND d.divida > 0 ORDER BY d.divida DESC", Devedor.class)
                .setParameter("uid", usuarioId)
                .setMaxResults(limite)
                .getResultList();

            LocalDate hoje = Fuso.hoje();
            List<Map<String, Object>> lista = new ArrayList<>();
            BigDecimal totalGeralDividas = BigDecimal.ZERO;

            for (Devedor d : devedores) {
                totalGeralDividas = totalGeralDividas.add(d.getDivida());
                long dias = 0;
                if (d.getData() != null) {
                    dias = Math.max(0, hoje.toEpochDay() - d.getData().toEpochDay());
                }

                String primeiroNome = d.getNome() != null ? d.getNome().trim().split("\\s+")[0] : "Cliente";

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("primeiroNome", primeiroNome);
                item.put("saldoDevedor", d.getDivida().setScale(2, RoundingMode.HALF_UP).toString());
                item.put("diasAtraso", dias);
                lista.add(item);
            }

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("quantidadeDevedoresListados", lista.size());
            res.put("totalSomaListados", totalGeralDividas.setScale(2, RoundingMode.HALF_UP).toString());
            res.put("devedores", lista);
            return res;
        });
    }

    public Map<String, Object> compararPeriodos(Long usuarioId, Map<String, Object> params) {
        LocalDate p1Inicio = parseData(params.get("p1Inicio"), Fuso.hoje().minusDays(6));
        LocalDate p1Fim = parseData(params.get("p1Fim"), Fuso.hoje());
        LocalDate p2Inicio = parseData(params.get("p2Inicio"), p1Inicio.minusDays(7));
        LocalDate p2Fim = parseData(params.get("p2Fim"), p1Inicio.minusDays(1));

        Map<String, Object> v1 = vendasPorPeriodo(usuarioId, Map.of("inicio", p1Inicio, "fim", p1Fim));
        Map<String, Object> v2 = vendasPorPeriodo(usuarioId, Map.of("inicio", p2Inicio, "fim", p2Fim));

        BigDecimal t1 = new BigDecimal((String) v1.get("totalVendas"));
        BigDecimal t2 = new BigDecimal((String) v2.get("totalVendas"));

        BigDecimal variacaoPct = BigDecimal.ZERO;
        if (t2.compareTo(BigDecimal.ZERO) > 0) {
            variacaoPct = t1.subtract(t2).multiply(BigDecimal.valueOf(100)).divide(t2, 2, RoundingMode.HALF_UP);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("periodoAtual", v1);
        res.put("periodoAnterior", v2);
        res.put("variacaoPercentualVendas", variacaoPct.toString() + "%");
        return res;
    }

    public Map<String, Object> despesasPorPeriodo(Long usuarioId, Map<String, Object> params) {
        LocalDate hoje = Fuso.hoje();
        LocalDate inicio = parseData(params.get("inicio"), hoje.minusDays(7));
        LocalDate fim = parseData(params.get("fim"), hoje);

        return JPAUtil.emTransacao(usuarioId, em -> {
            LocalDateTime inicioTs = inicio.atStartOfDay();
            LocalDateTime fimTs = fim.plusDays(1).atStartOfDay();

            BigDecimal total = em.createQuery(
                "SELECT COALESCE(SUM(d.valor), 0) FROM DespesaCaixa d WHERE d.usuarioId = :uid " +
                "AND d.criadaEm >= :inicio AND d.criadaEm < :fim", BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getSingleResult();

            Long contagem = em.createQuery(
                "SELECT COUNT(d) FROM DespesaCaixa d WHERE d.usuarioId = :uid " +
                "AND d.criadaEm >= :inicio AND d.criadaEm < :fim", Long.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getSingleResult();

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("inicio", inicio.toString());
            res.put("fim", fim.toString());
            res.put("totalDespesas", total.setScale(2, RoundingMode.HALF_UP).toString());
            res.put("quantidadeDespesas", contagem);
            return res;
        });
    }
}
