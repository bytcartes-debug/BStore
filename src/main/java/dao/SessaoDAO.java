package dao;

import model.Sessao;
import model.Usuario;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

public class SessaoDAO {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long DURACAO_DIAS = 14;

    public Sessao criar(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expira = agora.plus(DURACAO_DIAS, ChronoUnit.DAYS);
        String token = novoId();
        Sessao sessao = new Sessao(token, usuario, agora, expira);
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            if (util.JPAUtil.isPostgres()) {
                try {
                    em.createNativeQuery(
                        "SELECT bstore_criar_sessao(:token, :uid, :criada, :expira)")
                        .setParameter("token", token)
                        .setParameter("uid", usuario.getId())
                        .setParameter("criada", java.sql.Timestamp.from(agora))
                        .setParameter("expira", java.sql.Timestamp.from(expira))
                        .getResultList();
                    em.getTransaction().commit();
                    return sessao;
                } catch (Exception e) {
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    em.getTransaction().begin();
                }
            }
            sessao = em.merge(sessao);
            em.getTransaction().commit();
            return sessao;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public Sessao buscarActiva(String id) {
        if (id == null || id.length() > 64) {
            return null;
        }
        EntityManager em = JPAUtil.getEntityManager();
        try {
            if (util.JPAUtil.isPostgres()) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = em.createNativeQuery(
                        "SELECT sessao_id, criada_em, expira_em, revogada_em, usuario_id, usuario_nome, usuario_email, usuario_role, usuario_expiracao, usuario_dias FROM bstore_buscar_sessao_activa(:token)")
                        .setParameter("token", id)
                        .getResultList();
                    if (!rows.isEmpty()) {
                        Object[] row = rows.get(0);
                        Usuario u = new Usuario();
                        u.setId(((Number) row[4]).longValue());
                        u.setNome((String) row[5]);
                        u.setEmail((String) row[6]);
                        u.setRole((String) row[7]);
                        if (row[8] != null) {
                            u.setDataExpiracao(row[8] instanceof java.sql.Date ? ((java.sql.Date) row[8]).toLocalDate() : java.time.LocalDate.parse(row[8].toString()));
                        }
                        if (row[9] != null) {
                            u.setDiasAcesso(((Number) row[9]).intValue());
                        }
                        Instant criada = row[1] instanceof java.sql.Timestamp ? ((java.sql.Timestamp) row[1]).toInstant() : Instant.parse(row[1].toString());
                        Instant expira = row[2] instanceof java.sql.Timestamp ? ((java.sql.Timestamp) row[2]).toInstant() : Instant.parse(row[2].toString());
                        return new Sessao((String) row[0], u, criada, expira);
                    }
                    return null;
                } catch (Exception ignored) {
                    // Fallback para query JPA se a função não estiver disponível
                }
            }
            List<Sessao> sessoes = em.createQuery(
                "SELECT s FROM Sessao s JOIN FETCH s.usuario WHERE s.id = :id", Sessao.class)
                .setParameter("id", id)
                .getResultList();
            if (sessoes.isEmpty()) {
                return null;
            }
            Sessao sessao = sessoes.get(0);
            return sessao.estaActiva(Instant.now()) ? sessao : null;
        } finally {
            em.close();
        }
    }

    public void revogar(String id) {
        if (id == null) {
            return;
        }
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            if (util.JPAUtil.isPostgres()) {
                try {
                    em.createNativeQuery("SELECT bstore_revogar_sessao(:token)")
                        .setParameter("token", id)
                        .getResultList();
                    em.getTransaction().commit();
                    return;
                } catch (Exception e) {
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                    em.getTransaction().begin();
                }
            }
            Sessao sessao = em.find(Sessao.class, id);
            if (sessao != null && sessao.getRevogadaEm() == null) {
                sessao.revogar(Instant.now());
            }
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public void revogarDoUtilizador(Long usuarioId) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            JPAUtil.aplicarContextoTransacao(em, usuarioId);
            em.createQuery("UPDATE Sessao s SET s.revogadaEm = :agora WHERE s.usuario.id = :usuarioId AND s.revogadaEm IS NULL")
                .setParameter("agora", Instant.now())
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    private String novoId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
