package dao;

import model.Usuario;

import javax.persistence.EntityManager;
import java.util.List;

public class UsuarioDAO extends GenericDAO<Usuario> {

    public UsuarioDAO() {
        super(Usuario.class);
    }

    public List<Usuario> listarTodos() {
        EntityManager em = getEM();
        try {
            return em.createQuery("SELECT u FROM Usuario u ORDER BY u.nome", Usuario.class)
                     .getResultList();
        } finally {
            em.close();
        }
    }

    public Usuario buscarPorEmail(String email) {
        EntityManager em = getEM();
        try {
            List<Usuario> result = em.createQuery(
                "SELECT u FROM Usuario u WHERE LOWER(u.email) = :email", Usuario.class)
                .setParameter("email", email.toLowerCase().trim())
                .getResultList();
            return result.isEmpty() ? null : result.get(0);
        } finally {
            em.close();
        }
    }

    public long contarTodos() {
        EntityManager em = getEM();
        try {
            return em.createQuery("SELECT COUNT(u) FROM Usuario u", Long.class)
                     .getSingleResult();
        } finally {
            em.close();
        }
    }
}
