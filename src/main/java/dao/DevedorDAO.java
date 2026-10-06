package dao;

import model.Devedor;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.util.List;

public class DevedorDAO {

    public List<Devedor> listarTodos(Long usuarioId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                "SELECT d FROM Devedor d WHERE d.usuarioId = :usuarioId ORDER BY d.data DESC",
                Devedor.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    public long contarTodos(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(d) FROM Devedor d WHERE d.usuarioId = :uid", Long.class)
            .setParameter("uid", usuarioId).getSingleResult());
    }

    public Devedor buscarPorId(Long id, Long usuarioId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            List<Devedor> devedores = em.createQuery(
                "SELECT d FROM Devedor d WHERE d.id = :id AND d.usuarioId = :usuarioId", Devedor.class)
                .setParameter("id", id)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
            return devedores.isEmpty() ? null : devedores.get(0);
        } finally {
            em.close();
        }
    }

    public Devedor salvar(Devedor devedor) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            if (devedor.getId() == null) em.persist(devedor);
            else devedor = em.merge(devedor);
            em.getTransaction().commit();
            return devedor;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public boolean deletar(Long id, Long usuarioId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            Devedor devedor = em.createQuery(
                "SELECT d FROM Devedor d WHERE d.id = :id AND d.usuarioId = :usuarioId", Devedor.class)
                .setParameter("id", id)
                .setParameter("usuarioId", usuarioId)
                .getResultStream()
                .findFirst()
                .orElse(null);
            if (devedor == null) {
                em.getTransaction().rollback();
                return false;
            }
            em.remove(devedor);
            em.getTransaction().commit();
            return true;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
