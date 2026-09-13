package dao;

import model.Categoria;

import javax.persistence.EntityManager;
import java.util.List;

public class CategoriaDAO extends GenericDAO<Categoria> {

    public CategoriaDAO() {
        super(Categoria.class);
    }

    public List<Categoria> buscarPorNome(String nome, Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT c FROM Categoria c WHERE LOWER(c.nome) LIKE :nome AND c.usuarioId = :uid ORDER BY c.nome",
                Categoria.class)
                .setParameter("nome", "%" + nome.toLowerCase() + "%")
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public List<Categoria> listarOrdenado(Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT c FROM Categoria c WHERE c.usuarioId = :uid ORDER BY c.nome",
                Categoria.class)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public boolean temProdutos(Long categoriaId, Long uid) {
        EntityManager em = getEM();
        try {
            Long count = em.createQuery(
                "SELECT COUNT(p) FROM Produto p WHERE p.categoria.id = :id AND p.usuarioId = :uid",
                Long.class)
                .setParameter("id", categoriaId)
                .setParameter("uid", uid)
                .getSingleResult();
            return count > 0;
        } finally { em.close(); }
    }

    public long contarProdutos(Long categoriaId, Long uid) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT COUNT(p) FROM Produto p WHERE p.categoria.id = :id AND p.usuarioId = :uid",
                Long.class)
                .setParameter("id", categoriaId)
                .setParameter("uid", uid)
                .getSingleResult();
        } finally { em.close(); }
    }
}
