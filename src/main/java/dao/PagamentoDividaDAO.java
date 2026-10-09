package dao;

import model.PagamentoDivida;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;

public class PagamentoDividaDAO extends GenericDAO<PagamentoDivida> {

    public PagamentoDividaDAO() {
        super(PagamentoDivida.class);
    }

    public List<PagamentoDivida> listarPorDevedor(Long usuarioId, Long devedorId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT p FROM PagamentoDivida p WHERE p.usuarioId = :uid AND p.devedor.id = :did ORDER BY p.criadoEm DESC, p.id DESC",
            PagamentoDivida.class)
            .setParameter("uid", usuarioId)
            .setParameter("did", devedorId)
            .getResultList());
    }

    public BigDecimal totalPagoPorDevedor(Long usuarioId, Long devedorId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal total = em.createQuery(
                "SELECT SUM(p.valor) FROM PagamentoDivida p WHERE p.usuarioId = :uid AND p.devedor.id = :did",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("did", devedorId)
                .getSingleResult();
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    public boolean temPagamentos(Long usuarioId, Long devedorId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(p) FROM PagamentoDivida p WHERE p.usuarioId = :uid AND p.devedor.id = :did",
            Long.class)
            .setParameter("uid", usuarioId)
            .setParameter("did", devedorId)
            .getSingleResult() > 0);
    }
}
