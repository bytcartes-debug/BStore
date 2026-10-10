package dao;

import model.Devolucao;
import model.PagamentoVenda;
import model.SessaoCaixa;
import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SessaoCaixaDAO extends GenericDAO<SessaoCaixa> {

    public SessaoCaixaDAO() {
        super(SessaoCaixa.class);
    }

    public SessaoCaixa buscarAberta(Long uid) {
        return JPAUtil.emTransacao(uid, em -> buscarAberta(em, uid));
    }

    public SessaoCaixa buscarAberta(EntityManager em, Long uid) {
        List<SessaoCaixa> list = em.createQuery(
                "SELECT s FROM SessaoCaixa s WHERE s.usuarioId = :uid AND s.estado = 'ABERTA' ORDER BY s.abertaEm DESC",
                SessaoCaixa.class)
                .setParameter("uid", uid)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public SessaoCaixa buscarPorId(Long id, Long uid) {
        return JPAUtil.emTransacao(uid, em -> buscarPorId(em, id, uid));
    }

    public SessaoCaixa buscarPorId(EntityManager em, Long id, Long uid) {
        List<SessaoCaixa> list = em.createQuery(
                "SELECT s FROM SessaoCaixa s WHERE s.id = :id AND s.usuarioId = :uid",
                SessaoCaixa.class)
                .setParameter("id", id)
                .setParameter("uid", uid)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SessaoCaixa> listarHistorico(Long uid, int limite) {
        return JPAUtil.emTransacao(uid, em -> em.createQuery(
                "SELECT s FROM SessaoCaixa s WHERE s.usuarioId = :uid ORDER BY s.abertaEm DESC",
                SessaoCaixa.class)
                .setParameter("uid", uid)
                .setMaxResults(limite > 0 ? limite : 50)
                .getResultList());
    }

    public BigDecimal calcularVendasDinheiro(EntityManager em, Long sessaoId, Long uid) {
        BigDecimal total = em.createQuery(
                "SELECT COALESCE(SUM(p.valor - COALESCE(p.troco, 0)), 0) FROM PagamentoVenda p " +
                "WHERE p.venda.sessaoCaixa.id = :sessaoId " +
                "AND p.venda.usuarioId = :uid " +
                "AND p.venda.estado != 'ANULADA' " +
                "AND (p.metodo = 'DINHEIRO' OR (p.metodoPagamento IS NOT NULL AND p.metodoPagamento.tipo = 'DINHEIRO'))",
                BigDecimal.class)
                .setParameter("sessaoId", sessaoId)
                .setParameter("uid", uid)
                .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal calcularDevolucoesDinheiro(EntityManager em, Long sessaoId, Long uid) {
        BigDecimal total = em.createQuery(
                "SELECT COALESCE(SUM(d.total), 0) FROM Devolucao d " +
                "WHERE d.usuarioId = :uid " +
                "AND d.venda.sessaoCaixa.id = :sessaoId " +
                "AND EXISTS (" +
                "   SELECT 1 FROM PagamentoVenda p WHERE p.venda.id = d.venda.id " +
                "   AND (p.metodo = 'DINHEIRO' OR (p.metodoPagamento IS NOT NULL AND p.metodoPagamento.tipo = 'DINHEIRO'))" +
                ")",
                BigDecimal.class)
                .setParameter("sessaoId", sessaoId)
                .setParameter("uid", uid)
                .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal calcularDespesasDinheiro(EntityManager em, Long sessaoId, Long uid) {
        BigDecimal total = em.createQuery(
                "SELECT COALESCE(SUM(d.valor), 0) FROM DespesaCaixa d " +
                "WHERE d.usuarioId = :uid " +
                "AND d.sessaoCaixa.id = :sessaoId",
                BigDecimal.class)
                .setParameter("sessaoId", sessaoId)
                .setParameter("uid", uid)
                .getSingleResult();
        return total != null ? total : BigDecimal.ZERO;
    }
}
