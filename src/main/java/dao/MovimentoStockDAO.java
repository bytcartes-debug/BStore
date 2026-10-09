package dao;

import model.MovimentoStock;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class MovimentoStockDAO extends GenericDAO<MovimentoStock> {

    public MovimentoStockDAO() {
        super(MovimentoStock.class);
    }

    public void salvar(EntityManager em, MovimentoStock movimento) {
        em.persist(movimento);
    }

    public Pagina<MovimentoStock> listarPorProduto(Long usuarioId, Long produtoId, int page, int pageSize) {
        int offset = Pagina.offset(page, pageSize);
        return JPAUtil.emTransacao(usuarioId, em -> {
            String where = " WHERE m.usuarioId = :uid AND m.produto.id = :pid";
            Long total = em.createQuery("SELECT COUNT(m) FROM MovimentoStock m" + where, Long.class)
                    .setParameter("uid", usuarioId)
                    .setParameter("pid", produtoId)
                    .getSingleResult();
            List<MovimentoStock> items = em.createQuery(
                    "SELECT m FROM MovimentoStock m LEFT JOIN FETCH m.produto" + where + " ORDER BY m.criadoEm DESC, m.id DESC",
                    MovimentoStock.class)
                    .setParameter("uid", usuarioId)
                    .setParameter("pid", produtoId)
                    .setFirstResult(offset)
                    .setMaxResults(pageSize)
                    .getResultList();
            return new Pagina<>(items, total, page, pageSize);
        });
    }

    public Pagina<MovimentoStock> listarMovimentos(Long usuarioId, int page, int pageSize, String tipo,
                                                  LocalDateTime inicio, LocalDateTime fim) {
        int offset = Pagina.offset(page, pageSize);
        return JPAUtil.emTransacao(usuarioId, em -> {
            StringBuilder where = new StringBuilder(" WHERE m.usuarioId = :uid");
            if (tipo != null && !tipo.isBlank() && !"all".equalsIgnoreCase(tipo)) {
                where.append(" AND m.tipo = :tipo");
            }
            if (inicio != null) {
                where.append(" AND m.criadoEm >= :inicio");
            }
            if (fim != null) {
                where.append(" AND m.criadoEm <= :fim");
            }

            TypedQuery<Long> countQ = em.createQuery("SELECT COUNT(m) FROM MovimentoStock m" + where, Long.class);
            TypedQuery<MovimentoStock> queryQ = em.createQuery(
                    "SELECT m FROM MovimentoStock m LEFT JOIN FETCH m.produto" + where + " ORDER BY m.criadoEm DESC, m.id DESC",
                    MovimentoStock.class);

            for (TypedQuery<?> q : List.of(countQ, queryQ)) {
                q.setParameter("uid", usuarioId);
                if (tipo != null && !tipo.isBlank() && !"all".equalsIgnoreCase(tipo)) {
                    q.setParameter("tipo", tipo.toUpperCase());
                }
                if (inicio != null) {
                    q.setParameter("inicio", inicio);
                }
                if (fim != null) {
                    q.setParameter("fim", fim);
                }
            }

            Long total = countQ.getSingleResult();
            List<MovimentoStock> items = queryQ.setFirstResult(offset).setMaxResults(pageSize).getResultList();
            return new Pagina<>(items, total, page, pageSize);
        });
    }

    public BigDecimal somarQuantidadePorProduto(EntityManager em, Long usuarioId, Long produtoId) {
        BigDecimal sum = em.createQuery(
                "SELECT COALESCE(SUM(m.quantidade), 0) FROM MovimentoStock m WHERE m.usuarioId = :uid AND m.produto.id = :pid",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("pid", produtoId)
                .getSingleResult();
        return sum != null ? sum : BigDecimal.ZERO;
    }

    public BigDecimal calcularValorTotalStockCusto(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal total = em.createQuery(
                    "SELECT COALESCE(SUM(p.quantidadeStock * p.custo), 0) FROM Produto p WHERE p.usuarioId = :uid AND p.ativo = true AND p.quantidadeStock > 0",
                    BigDecimal.class)
                    .setParameter("uid", usuarioId)
                    .getSingleResult();
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    public BigDecimal calcularCustoVendasPeriodo(Long usuarioId, LocalDateTime inicio, LocalDateTime fim) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal total = em.createQuery(
                    "SELECT COALESCE(SUM(ABS(m.quantidade) * m.custoUnitario), 0) FROM MovimentoStock m WHERE m.usuarioId = :uid AND m.tipo = 'VENDA' AND m.criadoEm >= :inicio AND m.criadoEm <= :fim",
                    BigDecimal.class)
                    .setParameter("uid", usuarioId)
                    .setParameter("inicio", inicio)
                    .setParameter("fim", fim)
                    .getSingleResult();
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    public boolean temMovimentosParaProduto(Long usuarioId, Long produtoId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            Long count = em.createQuery(
                    "SELECT COUNT(m) FROM MovimentoStock m WHERE m.usuarioId = :uid AND m.produto.id = :pid",
                    Long.class)
                    .setParameter("uid", usuarioId)
                    .setParameter("pid", produtoId)
                    .getSingleResult();
            return count != null && count > 0;
        });
    }
}
