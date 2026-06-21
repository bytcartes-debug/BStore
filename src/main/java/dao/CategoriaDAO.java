package dao;

import model.Categoria;

import javax.persistence.EntityManager;
import java.util.List;

public class CategoriaDAO extends GenericDAO<Categoria> {

    public CategoriaDAO() {
        super(Categoria.class);
    }

    public List<Categoria> buscarPorNome(String nome) {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT c FROM Categoria c WHERE LOWER(c.nome) LIKE :nome ORDER BY c.nome";
            return em.createQuery(jpql, Categoria.class)
                     .setParameter("nome", "%" + nome.toLowerCase() + "%")
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Categoria> listarOrdenado() {
        EntityManager em = getEM();
        try {
            return em.createQuery("SELECT c FROM Categoria c ORDER BY c.nome", Categoria.class)
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public boolean temProdutos(Long categoriaId) {
        EntityManager em = getEM();
        try {
            Long count = em.createQuery(
                "SELECT COUNT(p) FROM Produto p WHERE p.categoria.id = :id", Long.class)
                .setParameter("id", categoriaId)
                .getSingleResult();
            return count > 0;
        } finally {
            em.close();
        }
    }
}
