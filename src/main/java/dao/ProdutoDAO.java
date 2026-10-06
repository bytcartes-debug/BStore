package dao;

import model.Produto;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.util.List;

public class ProdutoDAO extends GenericDAO<Produto> {

    public ProdutoDAO() {
        super(Produto.class);
    }

    public List<Produto> listarOrdenado(Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria WHERE p.usuarioId = :usuarioId ORDER BY p.nome",
                Produto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public Pagina<Produto> listarPagina(Long usuarioId, int page, int pageSize, String pesquisa,
                                        Long categoriaId, String stock, String ordem) {
        int offset = Pagina.offset(page, pageSize);
        String termo = Pagina.pesquisa(pesquisa);
        if (!"all".equals(stock) && !"low".equals(stock)) throw new IllegalArgumentException("Filtro de stock inválido.");
        String ordenacao;
        switch (ordem) {
            case "name": ordenacao = "LOWER(p.nome), p.id"; break;
            case "price": ordenacao = "p.preco, p.id"; break;
            case "stock": ordenacao = "p.quantidadeStock, p.id"; break;
            default: throw new IllegalArgumentException("Ordenação inválida.");
        }
        String where = " WHERE p.usuarioId = :uid"
            + (termo.isEmpty() ? "" : " AND (LOWER(p.nome) LIKE :q ESCAPE '!' OR LOWER(p.codigoBarras) LIKE :q ESCAPE '!')")
            + (categoriaId == null ? "" : " AND p.categoria.id = :categoria")
            + ("low".equals(stock) ? " AND p.quantidadeStock <= p.stockMinimo" : "");
        return util.JPAUtil.emTransacao(usuarioId, em -> {
            javax.persistence.TypedQuery<Long> count = em.createQuery("SELECT COUNT(p) FROM Produto p" + where, Long.class);
            javax.persistence.TypedQuery<Produto> query = em.createQuery(
                "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria" + where + " ORDER BY " + ordenacao, Produto.class);
            for (javax.persistence.Query statement : List.of(count, query)) {
                statement.setParameter("uid", usuarioId);
                if (!termo.isEmpty()) statement.setParameter("q", "%" + termo + "%");
                if (categoriaId != null) statement.setParameter("categoria", categoriaId);
            }
            long total = count.getSingleResult();
            List<Produto> items = query.setFirstResult(offset).setMaxResults(pageSize).getResultList();
            return new Pagina<>(items, total, page, pageSize);
        });
    }

    public Produto buscarPorId(Long id, Long usuarioId) {
        EntityManager em = getEM();
        try {
            return buscarPorId(em, id, usuarioId, null);
        } finally {
            em.close();
        }
    }

    public Produto buscarPorIdParaVenda(EntityManager em, Long id, Long usuarioId) {
        List<Produto> produtos = em.createQuery(
            "SELECT p FROM Produto p WHERE p.id = :id AND p.usuarioId = :uid", Produto.class)
            .setParameter("id", id).setParameter("uid", usuarioId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        return produtos.isEmpty() ? null : produtos.get(0);
    }

    private Produto buscarPorId(EntityManager em, Long id, Long usuarioId, LockModeType lockMode) {
        javax.persistence.TypedQuery<Produto> query = em.createQuery(
            "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria WHERE p.id = :id AND p.usuarioId = :usuarioId",
            Produto.class)
            .setParameter("id", id)
            .setParameter("usuarioId", usuarioId);
        if (lockMode != null) {
            query.setLockMode(lockMode);
        }
        List<Produto> produtos = query.getResultList();
        return produtos.isEmpty() ? null : produtos.get(0);
    }

    public List<Produto> buscarPorNome(String nome, Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p WHERE LOWER(p.nome) LIKE :nome AND p.usuarioId = :usuarioId ORDER BY p.nome",
                Produto.class)
                .setParameter("nome", "%" + nome.toLowerCase() + "%")
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Produto> buscarPorCategoria(Long categoriaId, Long usuarioId) {
        EntityManager em = getEM();
        try {
            return em.createQuery(
                "SELECT p FROM Produto p WHERE p.categoria.id = :categoriaId AND p.usuarioId = :usuarioId ORDER BY p.nome",
                Produto.class)
                .setParameter("categoriaId", categoriaId)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public List<Produto> buscarStockBaixo(Long usuarioId) {
        return util.JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria WHERE p.quantidadeStock <= p.stockMinimo AND p.usuarioId = :uid ORDER BY p.quantidadeStock", Produto.class)
            .setParameter("uid", usuarioId).getResultList());
    }

    public long contarTodos(Long usuarioId) {
        return util.JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(p) FROM Produto p WHERE p.usuarioId = :uid", Long.class)
            .setParameter("uid", usuarioId).getSingleResult());
    }

    public Produto buscarPorCodigoBarras(String codigo, Long usuarioId) {
        EntityManager em = getEM();
        try {
            List<Produto> result = em.createQuery(
                "SELECT p FROM Produto p LEFT JOIN FETCH p.categoria WHERE p.codigoBarras = :codigo AND p.usuarioId = :usuarioId",
                Produto.class)
                .setParameter("codigo", codigo)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
            return result.isEmpty() ? null : result.get(0);
        } finally {
            em.close();
        }
    }

    public boolean codigoBarrasEmUso(String codigo, Long usuarioId, Long ignorarId) {
        if (codigo == null || codigo.isBlank()) {
            return false;
        }
        EntityManager em = getEM();
        try {
            Long total = em.createQuery(
                "SELECT COUNT(p) FROM Produto p WHERE p.codigoBarras = :codigo AND p.usuarioId = :usuarioId AND (:ignorarId IS NULL OR p.id <> :ignorarId)",
                Long.class)
                .setParameter("codigo", codigo)
                .setParameter("usuarioId", usuarioId)
                .setParameter("ignorarId", ignorarId)
                .getSingleResult();
            return total > 0;
        } finally {
            em.close();
        }
    }
}
