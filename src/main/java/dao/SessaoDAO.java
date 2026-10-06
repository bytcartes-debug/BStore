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
        Sessao sessao = new Sessao(novoId(), usuario, agora, agora.plus(DURACAO_DIAS, ChronoUnit.DAYS));
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
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
