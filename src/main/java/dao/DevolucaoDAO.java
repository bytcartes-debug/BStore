package dao;

import model.Devolucao;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;

public class DevolucaoDAO extends GenericDAO<Devolucao> {

    public DevolucaoDAO() {
        super(Devolucao.class);
    }

    public Devolucao buscarPorId(Long id, Long uid) {
        return JPAUtil.emTransacao(uid, em -> buscarPorId(em, id, uid));
    }

    public Devolucao buscarPorId(EntityManager em, Long id, Long uid) {
        List<Devolucao> list = em.createQuery(
                "SELECT d FROM Devolucao d WHERE d.id = :id AND d.usuarioId = :uid",
                Devolucao.class)
                .setParameter("id", id)
                .setParameter("uid", uid)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public Devolucao buscarPorUuidCliente(EntityManager em, String uuidCliente, Long uid) {
        if (uuidCliente == null || uuidCliente.isBlank()) return null;
        List<Devolucao> list = em.createQuery(
                "SELECT d FROM Devolucao d WHERE d.uuidCliente = :uuid AND d.usuarioId = :uid",
                Devolucao.class)
                .setParameter("uuid", uuidCliente.trim())
                .setParameter("uid", uid)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Devolucao> listarPorVenda(Long vendaId, Long uid) {
        return JPAUtil.emTransacao(uid, em -> listarPorVenda(em, vendaId, uid));
    }

    public List<Devolucao> listarPorVenda(EntityManager em, Long vendaId, Long uid) {
        return em.createQuery(
                "SELECT d FROM Devolucao d WHERE d.venda.id = :vendaId AND d.usuarioId = :uid ORDER BY d.criadaEm ASC",
                Devolucao.class)
                .setParameter("vendaId", vendaId)
                .setParameter("uid", uid)
                .getResultList();
    }

    public BigDecimal obterTotalDevolvidoPorItem(EntityManager em, Long itemVendaId) {
        BigDecimal total = em.createQuery(
                "SELECT COALESCE(SUM(i.quantidade), 0) FROM ItemDevolucao i WHERE i.itemVenda.id = :itemId",
                BigDecimal.class)
                .setParameter("itemId", itemVendaId)
                .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }
}
