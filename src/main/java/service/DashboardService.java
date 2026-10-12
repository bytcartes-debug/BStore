package service;

import dao.CategoriaDAO;
import dao.DevedorDAO;
import dao.ProdutoDAO;
import dao.VendaDAO;
import model.Produto;
import model.Venda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class DashboardService {

    private final VendaDAO vendaDAO = new VendaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return vendaDAO.totalVendasHoje(usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.totalVendasPeriodo(inicio, fim, usuarioId);
    }

    public long vendasHoje(Long usuarioId) {
        return vendaDAO.contarVendasHoje(usuarioId);
    }

    public List<Venda> vendasDeHoje(Long usuarioId) {
        return vendaDAO.recentes(usuarioId, 25);
    }

    public List<Produto> produtosComStockBaixo(Long usuarioId) {
        return produtoDAO.buscarStockBaixo(usuarioId);
    }

    public long totalProdutos(Long usuarioId) {
        return produtoDAO.contarTodos(usuarioId);
    }

    public long totalCategorias(Long usuarioId) {
        return categoriaDAO.contarTodos(usuarioId);
    }

    public long totalDevedores(Long usuarioId) {
        return devedorDAO.contarTodos(usuarioId);
    }

    public Map<LocalDate, BigDecimal> totaisPorDia(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.totaisPorDia(inicio, fim, usuarioId);
    }

    public List<Venda> vendasRecentes(Long usuarioId, int limite) {
        return vendaDAO.recentes(usuarioId, limite);
    }

    public BigDecimal calcularPerdasMesACusto(Long usuarioId) {
        return util.JPAUtil.emTransacao(usuarioId, em -> {
            java.time.LocalDateTime inicioMes = util.Fuso.hoje().withDayOfMonth(1).atStartOfDay();
            BigDecimal perdas = em.createQuery(
                "SELECT COALESCE(SUM(ABS(m.quantidade) * COALESCE(m.custoUnitario, 0)), 0) FROM MovimentoStock m " +
                "WHERE m.usuarioId = :uid AND (m.tipo = 'PERDA' OR (m.tipo = 'AJUSTE' AND m.quantidade < 0)) " +
                "AND m.criadoEm >= :inicioMes", BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicioMes", inicioMes)
                .getSingleResult();
            return perdas != null ? perdas.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        });
    }
}
