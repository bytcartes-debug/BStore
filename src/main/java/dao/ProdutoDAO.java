package dao;

import model.Produto;

import javax.persistence.EntityManager;
import java.util.List;

public class ProdutoDAO extends GenericDAO<Produto> {

    public ProdutoDAO() {
        super(Produto.class);
    }

    public List<Produto> listarOrdenado(Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria WHERE p.usuarioId = :uid ORDER BY p.nome",
                Produto.class)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public List<Produto> buscarPorNome(String nome, Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p WHERE LOWER(p.nome) LIKE :nome AND p.usuarioId = :uid ORDER BY p.nome",
                Produto.class)
                .setParameter("nome", "%" + nome.toLowerCase() + "%")
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public List<Produto> buscarPorCategoria(Long catId, Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p WHERE p.categoria.id = :catId AND p.usuarioId = :uid ORDER BY p.nome",
                Produto.class)
                .setParameter("catId", catId)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public List<Produto> buscarStockBaixo(Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p WHERE p.quantidadeStock <= p.stockMinimo AND p.usuarioId = :uid ORDER BY p.quantidadeStock",
                Produto.class)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public void actualizarStock(Long produtoId, double quantidadeVendida) {
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
        } finally { em.close(); }
    }

    public long contarTodos(Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT COUNT(p) FROM Produto p WHERE p.usuarioId = :uid", Long.class)
                .setParameter("uid", uid)
                .getSingleResult();
        } finally { em.close(); }
    }

    public Produto buscarPorCodigoBarras(String codigo, Long uid) {
        EntityManager em = getEM();
        try {
            List<Produto> result = em.createQuery(
                "SELECT p FROM Produto p WHERE p.codigoBarras = :codigo AND p.usuarioId = :uid",
                Produto.class)
                .setParameter("codigo", codigo)
                .setParameter("uid", uid)
                .getResultList();
            return result.isEmpty() ? null : result.get(0);
        } finally { em.close(); }
    }
}
