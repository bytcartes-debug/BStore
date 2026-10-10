package dao;

import model.DespesaCaixa;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DespesaCaixaDAO extends GenericDAO<DespesaCaixa> {

    public DespesaCaixaDAO() {
        super(DespesaCaixa.class);
    }

    public List<DespesaCaixa> listarPorSessao(EntityManager em, Long sessaoId, Long uid) {
        return em.createQuery(
                "SELECT d FROM DespesaCaixa d WHERE d.usuarioId = :uid AND d.sessaoCaixa.id = :sessaoId ORDER BY d.criadaEm DESC",
                DespesaCaixa.class)
                .setParameter("uid", uid)
                .setParameter("sessaoId", sessaoId)
                .getResultList();
    }

    public List<DespesaCaixa> listarRecentes(EntityManager em, Long uid, int limite) {
        return em.createQuery(
                "SELECT d FROM DespesaCaixa d WHERE d.usuarioId = :uid ORDER BY d.criadaEm DESC",
                DespesaCaixa.class)
                .setParameter("uid", uid)
                .setMaxResults(limite > 0 ? limite : 50)
                .getResultList();
    }

    public BigDecimal totalDespesasPeriodo(EntityManager em, LocalDateTime inicio, LocalDateTime fim, Long uid) {
        BigDecimal total = em.createQuery(
                "SELECT COALESCE(SUM(d.valor), 0) FROM DespesaCaixa d " +
                "WHERE d.usuarioId = :uid AND d.criadaEm >= :inicio AND d.criadaEm < :fim",
                BigDecimal.class)
                .setParameter("uid", uid)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }
}
