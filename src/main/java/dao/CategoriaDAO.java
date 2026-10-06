package dao;

import model.Categoria;

import javax.persistence.EntityManager;
import java.util.List;

public class CategoriaDAO extends GenericDAO<Categoria> {

    public CategoriaDAO() {
        super(Categoria.class);
    }

    public Categoria buscarPorId(Long id, Long uid) {
        EntityManager em = getEM();
        try {
            List<Categoria> categorias = em.createQuery(
                "SELECT c FROM Categoria c WHERE c.id = :id AND c.usuarioId = :uid", Categoria.class)
                .setParameter("id", id)
                .setParameter("uid", uid)
                .getResultList();
            return categorias.isEmpty() ? null : categorias.get(0);
        } finally { em.close(); }
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
        return util.JPAUtil.emTransacao(uid, em -> em.createQuery(
            "SELECT c FROM Categoria c WHERE c.usuarioId = :uid ORDER BY c.nome", Categoria.class)
            .setParameter("uid", uid).getResultList());
    }

    public long contarTodos(Long uid) {
        return util.JPAUtil.emTransacao(uid, em -> em.createQuery(
            "SELECT COUNT(c) FROM Categoria c WHERE c.usuarioId = :uid", Long.class)
            .setParameter("uid", uid).getSingleResult());
    }

    public java.util.Map<Long, Long> contarProdutosPorCategoria(Long uid) {
        return util.JPAUtil.emTransacao(uid, em -> {
            List<Object[]> rows = em.createQuery(
                "SELECT p.categoria.id, COUNT(p) FROM Produto p WHERE p.usuarioId = :uid GROUP BY p.categoria.id", Object[].class)
                .setParameter("uid", uid).getResultList();
            java.util.Map<Long, Long> counts = new java.util.HashMap<>();
            for (Object[] row : rows) counts.put((Long) row[0], (Long) row[1]);
            return counts;
        });
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
