package dao;

import model.MetodoPagamento;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.util.List;

public class MetodoPagamentoDAO extends GenericDAO<MetodoPagamento> {

    public MetodoPagamentoDAO() {
        super(MetodoPagamento.class);
    }

    public List<MetodoPagamento> listarTodos(Long uid) {
        return JPAUtil.emTransacao(uid, em -> listarTodos(em, uid));
    }

    public List<MetodoPagamento> listarTodos(EntityManager em, Long uid) {
        garantirMetodosPadrao(em, uid);
        return em.createQuery(
                "SELECT m FROM MetodoPagamento m WHERE m.usuarioId = :uid ORDER BY m.ordem ASC, m.id ASC",
                MetodoPagamento.class)
                .setParameter("uid", uid)
                .getResultList();
    }

    public List<MetodoPagamento> listarAtivos(Long uid) {
        return JPAUtil.emTransacao(uid, em -> listarAtivos(em, uid));
    }

    public List<MetodoPagamento> listarAtivos(EntityManager em, Long uid) {
        garantirMetodosPadrao(em, uid);
        return em.createQuery(
                "SELECT m FROM MetodoPagamento m WHERE m.usuarioId = :uid AND m.ativo = true ORDER BY m.ordem ASC, m.id ASC",
                MetodoPagamento.class)
                .setParameter("uid", uid)
                .getResultList();
    }

    public MetodoPagamento buscarPorId(Long id, Long uid) {
        return JPAUtil.emTransacao(uid, em -> buscarPorId(em, id, uid));
    }

    public MetodoPagamento buscarPorId(EntityManager em, Long id, Long uid) {
        garantirMetodosPadrao(em, uid);
        List<MetodoPagamento> list = em.createQuery(
                "SELECT m FROM MetodoPagamento m WHERE m.id = :id AND m.usuarioId = :uid",
                MetodoPagamento.class)
                .setParameter("id", id)
                .setParameter("uid", uid)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public MetodoPagamento buscarPorNome(EntityManager em, String nome, Long uid) {
        garantirMetodosPadrao(em, uid);
        List<MetodoPagamento> list = em.createQuery(
                "SELECT m FROM MetodoPagamento m WHERE LOWER(m.nome) = LOWER(:nome) AND m.usuarioId = :uid",
                MetodoPagamento.class)
                .setParameter("nome", nome.trim())
                .setParameter("uid", uid)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public void garantirMetodosPadrao(EntityManager em, Long uid) {
        model.DefinicaoLoja def = em.find(model.DefinicaoLoja.class, uid, javax.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (def == null) {
            def = new model.DefinicaoLoja(uid, false, "Loja");
            em.persist(def);
            em.flush();
            def = em.find(model.DefinicaoLoja.class, uid, javax.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
        Long total = em.createQuery("SELECT COUNT(m) FROM MetodoPagamento m WHERE m.usuarioId = :uid", Long.class)
                .setParameter("uid", uid)
                .getSingleResult();
        if (total == null || total == 0) {
            em.persist(new MetodoPagamento(uid, "Dinheiro", "DINHEIRO", true, 1));
            em.persist(new MetodoPagamento(uid, "M-Pesa", "DIGITAL", true, 2));
            em.persist(new MetodoPagamento(uid, "e-Mola", "DIGITAL", true, 3));
            em.persist(new MetodoPagamento(uid, "A fiado", "FIADO", true, 4));
            em.persist(new MetodoPagamento(uid, "mKesh", "DIGITAL", false, 5));
            em.persist(new MetodoPagamento(uid, "Cartão", "DIGITAL", false, 6));
            em.flush();
        }
    }
}
