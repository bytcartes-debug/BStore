package dao;

import model.Usuario;

import javax.persistence.EntityManager;
import java.util.List;

public class UsuarioDAO extends GenericDAO<Usuario> {

    public UsuarioDAO() {
        super(Usuario.class);
    }

    @Override
    public Usuario salvar(Usuario objeto) {
        EntityManager em = getEM();
        try {
            em.getTransaction().begin();
            if (util.JPAUtil.isPostgres()) {
                try {
                    Number novoId = (Number) em.createNativeQuery(
                        "SELECT bstore_criar_usuario(:nome, :email, :hash, :role, :exp, :dias)")
                        .setParameter("nome", objeto.getNome())
                        .setParameter("email", objeto.getEmail())
                        .setParameter("hash", objeto.getPasswordHash())
                        .setParameter("role", objeto.getRole())
                        .setParameter("exp", objeto.getDataExpiracao() != null ? java.sql.Date.valueOf(objeto.getDataExpiracao()) : null)
                        .setParameter("dias", objeto.getDiasAcesso())
                        .getSingleResult();
                    objeto.setId(novoId.longValue());
                    em.getTransaction().commit();
                    return objeto;
                } catch (Exception e) {
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    em.getTransaction().begin();
                }
            }
            em.persist(objeto);
            em.persist(new model.DefinicaoLoja(objeto.getId(), false, objeto.getNome()));
            em.getTransaction().commit();
            return objeto;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
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
        if (email == null || email.isBlank()) {
            return null;
        }
        EntityManager em = getEM();
        try {
            if (util.JPAUtil.isPostgres()) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = em.createNativeQuery(
                        "SELECT id, nome, email, password_hash, role, data_expiracao, dias_acesso FROM bstore_buscar_usuario_login(:email)")
                        .setParameter("email", email.trim())
                        .getResultList();
                    if (!rows.isEmpty()) {
                        Object[] row = rows.get(0);
                        Usuario u = new Usuario();
                        u.setId(((Number) row[0]).longValue());
                        u.setNome((String) row[1]);
                        u.setEmail((String) row[2]);
                        u.setPasswordHash((String) row[3]);
                        u.setRole((String) row[4]);
                        if (row[5] != null) {
                            u.setDataExpiracao(row[5] instanceof java.sql.Date ? ((java.sql.Date) row[5]).toLocalDate() : java.time.LocalDate.parse(row[5].toString()));
                        }
                        if (row[6] != null) {
                            u.setDiasAcesso(((Number) row[6]).intValue());
                        }
                        return u;
                    }
                    return null;
                } catch (Exception ignored) {
                    // Fallback para query JPA se a função não estiver disponível
                }
            }
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

    @Override
    public void eliminar(Long id) {
        if (id == null) return;
        EntityManager em = getEM();
        try {
            em.getTransaction().begin();
            if (util.JPAUtil.isPostgres()) {
                try {
                    em.createNativeQuery("SELECT bstore_eliminar_usuario(:id)")
                      .setParameter("id", id)
                      .getSingleResult();
                    em.getTransaction().commit();
                    return;
                } catch (Exception e) {
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    em.getTransaction().begin();
                }
            }
            Usuario u = em.find(Usuario.class, id);
            if (u != null) {
                em.createQuery("DELETE FROM DefinicaoLoja d WHERE d.usuarioId = :uid").setParameter("uid", id).executeUpdate();
                em.createQuery("DELETE FROM Sessao s WHERE s.usuarioId = :uid").setParameter("uid", id).executeUpdate();
                em.remove(u);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
