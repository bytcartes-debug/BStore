package dao;

import model.Devedor;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.util.List;

public class DevedorDAO {

    public List<Devedor> listarTodos(Long uid) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.createQuery(
                "SELECT d FROM Devedor d WHERE d.usuarioId = :uid ORDER BY d.data DESC",
                Devedor.class)
                .setParameter("uid", uid)
                .getResultList();
        } finally { em.close(); }
    }

    public Devedor buscarPorId(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try { return em.find(Devedor.class, id); }
        finally { em.close(); }
    }

    public Devedor salvar(Devedor devedor) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            if (devedor.getId() == null) em.persist(devedor);
            else devedor = em.merge(devedor);
            em.getTransaction().commit();
            return devedor;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally { em.close(); }
    }

    public void deletar(Long id) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            Devedor d = em.find(Devedor.class, id);
            if (d != null) em.remove(d);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        } finally { em.close(); }
    }
}
