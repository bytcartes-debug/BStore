package dao;

import model.Venda;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class VendaDAO extends GenericDAO<Venda> {

    public VendaDAO() {
        super(Venda.class);
    }

    public List<Venda> listarOrdenado(Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v LEFT JOIN FETCH v.produto WHERE v.usuarioId = :usuarioId ORDER BY v.dataVenda DESC, v.id DESC",
                Venda.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public Pagina<Venda> listarPagina(Long usuarioId, int page, int pageSize, String pesquisa,
                                      LocalDate inicio, LocalDate fim) {
        int offset = Pagina.offset(page, pageSize);
        String termo = Pagina.pesquisa(pesquisa);
        if (inicio != null && fim != null && inicio.isAfter(fim)) throw new IllegalArgumentException("O início não pode ser posterior ao fim.");
        String where = " WHERE v.usuarioId = :uid"
            + (termo.isEmpty() ? "" : " AND LOWER(v.produto.nome) LIKE :q ESCAPE '!'")
            + (inicio == null ? "" : " AND v.dataVenda >= :inicio")
            + (fim == null ? "" : " AND v.dataVenda <= :fim");
        return util.JPAUtil.emTransacao(usuarioId, em -> {
            javax.persistence.TypedQuery<Long> count = em.createQuery("SELECT COUNT(v) FROM Venda v" + where, Long.class);
            javax.persistence.TypedQuery<Venda> query = em.createQuery(
                "SELECT v FROM Venda v JOIN FETCH v.produto p LEFT JOIN FETCH p.categoria" + where
                    + " ORDER BY v.dataVenda DESC, v.id DESC", Venda.class);
            for (javax.persistence.Query statement : List.of(count, query)) {
                statement.setParameter("uid", usuarioId);
                if (!termo.isEmpty()) statement.setParameter("q", "%" + termo + "%");
                if (inicio != null) statement.setParameter("inicio", inicio);
                if (fim != null) statement.setParameter("fim", fim);
            }
            long total = count.getSingleResult();
            return new Pagina<>(query.setFirstResult(offset).setMaxResults(pageSize).getResultList(), total, page, pageSize);
        });
    }

    public List<Venda> recentes(Long usuarioId, int limite) {
        return util.JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT v FROM Venda v JOIN FETCH v.produto p LEFT JOIN FETCH p.categoria WHERE v.usuarioId = :uid ORDER BY v.dataVenda DESC, v.id DESC", Venda.class)
            .setParameter("uid", usuarioId).setMaxResults(limite).getResultList());
    }

    public java.util.Map<LocalDate, BigDecimal> totaisPorDia(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return util.JPAUtil.emTransacao(usuarioId, em -> {
            List<Object[]> rows = em.createQuery(
                "SELECT v.dataVenda, SUM(v.total) FROM Venda v WHERE v.usuarioId = :uid AND v.dataVenda BETWEEN :inicio AND :fim GROUP BY v.dataVenda", Object[].class)
                .setParameter("uid", usuarioId).setParameter("inicio", inicio).setParameter("fim", fim).getResultList();
            java.util.Map<LocalDate, BigDecimal> totals = new java.util.LinkedHashMap<>();
            for (Object[] row : rows) totals.put((LocalDate) row[0], (BigDecimal) row[1]);
            return totals;
        });
    }

    public List<Venda> vendasDeHoje(Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v WHERE v.dataVenda = :hoje AND v.usuarioId = :usuarioId ORDER BY v.id DESC",
                Venda.class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.usuarioId = :usuarioId ORDER BY v.dataVenda DESC",
                Venda.class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return totalVendasPeriodo(LocalDate.now(), LocalDate.now(), usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        EntityManager em = getEM();
        try {
            BigDecimal total = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.usuarioId = :usuarioId",
                BigDecimal.class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
            return total != null ? total : BigDecimal.ZERO;
        } finally {
            em.close();
        }
    }

    public long contarVendasHoje(Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.dataVenda = :hoje AND v.usuarioId = :usuarioId",
                Long.class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
        } finally {
            em.close();
        }
    }

    public boolean existeParaProduto(Long produtoId, Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.produto.id = :produtoId AND v.usuarioId = :usuarioId",
                Long.class)
                .setParameter("produtoId", produtoId)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult() > 0;
        } finally {
            em.close();
        }
    }
}
