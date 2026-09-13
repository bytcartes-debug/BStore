package dao;

import model.Venda;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

public class VendaDAO extends GenericDAO<Venda> {

    public VendaDAO() {
        super(Venda.class);
    }

    public List<Venda> listarOrdenado(Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v LEFT JOIN FETCH v.produto WHERE v.usuarioId = :uid ORDER BY v.dataVenda DESC, v.id DESC",
                Venda.class)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public List<Venda> vendasDeHoje(Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v WHERE v.dataVenda = :hoje AND v.usuarioId = :uid ORDER BY v.id DESC",
                Venda.class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.usuarioId = :uid ORDER BY v.dataVenda DESC",
                Venda.class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public Double totalVendasHoje(Long uid) {
        EntityManager em = getEM();
        try {
            Double r = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.dataVenda = :hoje AND v.usuarioId = :uid",
                Double.class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("uid", uid)
                .getSingleResult();
            return r != null ? r : 0.0;
        } finally { em.close(); }
    }

    public Double totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long uid) {
        EntityManager em = getEM();
        try {
            Double r = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.usuarioId = :uid",
                Double.class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .setParameter("uid", uid)
                .getSingleResult();
            return r != null ? r : 0.0;
        } finally { em.close(); }
    }

    public long contarVendasHoje(Long uid) {
        EntityManager em = getEM();
        try {
            Long r = em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.dataVenda = :hoje AND v.usuarioId = :uid",
                Long.class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("uid", uid)
                .getSingleResult();
            return r != null ? r : 0L;
        } finally { em.close(); }
    }
}
