package dao;

import model.Produto;

import javax.persistence.EntityManager;
import java.util.List;

public class ProdutoDAO extends GenericDAO<Produto> {

    public ProdutoDAO() {
        super(Produto.class);
    }

    public List<Produto> listarOrdenado() {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria ORDER BY p.nome",
                Produto.class).getResultList();
        } finally {
            em.close();
        }
    }

    public List<Produto> buscarPorNome(String nome) {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT p FROM Produto p WHERE LOWER(p.nome) LIKE :nome ORDER BY p.nome";
            return em.createQuery(jpql, Produto.class)
                     .setParameter("nome", "%" + nome.toLowerCase() + "%")
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Produto> buscarPorCategoria(Long categoriaId) {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT p FROM Produto p WHERE p.categoria.id = :catId ORDER BY p.nome";
            return em.createQuery(jpql, Produto.class)
                     .setParameter("catId", categoriaId)
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Produto> buscarStockBaixo() {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT p FROM Produto p WHERE p.quantidadeStock <= p.stockMinimo ORDER BY p.quantidadeStock";
            return em.createQuery(jpql, Produto.class).getResultList();
        } finally {
            em.close();
        }
    }

    public void actualizarStock(Long produtoId, int quantidadeVendida) {
        EntityManager em = getEM();
        try {
            em.getTransaction().begin();
            Produto p = em.find(Produto.class, produtoId);
            if (p != null) {
                p.setQuantidadeStock(p.getQuantidadeStock() - quantidadeVendida);
                em.merge(p);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public long contarTodos() {
        EntityManager em = getEM();
        try {
            return em.createQuery("SELECT COUNT(p) FROM Produto p", Long.class)
                     .getSingleResult();
        } finally {
            em.close();
        }
    }
}
