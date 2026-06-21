package dao;

import model.Venda;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

public class VendaDAO extends GenericDAO<Venda> {

    public VendaDAO() {
        super(Venda.class);
    }

    public List<Venda> listarOrdenado() {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT v FROM Venda v LEFT JOIN FETCH v.produto ORDER BY v.dataVenda DESC, v.id DESC",
                Venda.class).getResultList();
        } finally {
            em.close();
        }
    }

    public List<Venda> vendasDeHoje() {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT v FROM Venda v WHERE v.dataVenda = :hoje ORDER BY v.id DESC";
            return em.createQuery(jpql, Venda.class)
                     .setParameter("hoje", LocalDate.now())
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim) {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT v FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim ORDER BY v.dataVenda DESC";
            return em.createQuery(jpql, Venda.class)
                     .setParameter("inicio", inicio)
                     .setParameter("fim", fim)
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Venda> vendasPorProduto(Long produtoId) {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT v FROM Venda v WHERE v.produto.id = :id ORDER BY v.dataVenda DESC";
            return em.createQuery(jpql, Venda.class)
                     .setParameter("id", produtoId)
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public Double totalVendasHoje() {
        EntityManager em = getEM();
        try {
            Double resultado = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.dataVenda = :hoje", Double.class)
                .setParameter("hoje", LocalDate.now())
                .getSingleResult();
            return resultado != null ? resultado : 0.0;
        } finally {
            em.close();
        }
    }

    public Double totalVendasPeriodo(LocalDate inicio, LocalDate fim) {
        EntityManager em = getEM();
        try {
            Double resultado = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim",
                Double.class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();
            return resultado != null ? resultado : 0.0;
        } finally {
            em.close();
        }
    }

    public long contarVendasHoje() {
        EntityManager em = getEM();
        try {
            Long r = em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.dataVenda = :hoje", Long.class)
                .setParameter("hoje", LocalDate.now())
                .getSingleResult();
            return r != null ? r : 0L;
        } finally {
            em.close();
        }
    }
}
