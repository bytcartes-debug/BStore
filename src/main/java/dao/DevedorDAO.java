package dao;

import model.Devedor;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;

public class DevedorDAO {

    private final PagamentoDividaDAO pagamentoDividaDAO = new PagamentoDividaDAO();

    public List<Devedor> listarTodos(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT d FROM Devedor d WHERE d.usuarioId = :usuarioId ORDER BY d.data DESC, d.id DESC",
            Devedor.class)
            .setParameter("usuarioId", usuarioId)
            .getResultList());
    }

    public long contarTodos(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(d) FROM Devedor d WHERE d.usuarioId = :uid", Long.class)
            .setParameter("uid", usuarioId).getSingleResult());
    }

    public Devedor buscarPorId(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Devedor> devedores = em.createQuery(
                "SELECT d FROM Devedor d WHERE d.id = :id AND d.usuarioId = :usuarioId", Devedor.class)
                .setParameter("id", id)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
            return devedores.isEmpty() ? null : devedores.get(0);
        });
    }

    public Devedor buscarPorIdParaBloqueio(EntityManager em, Long id, Long usuarioId) {
        List<Devedor> devedores = em.createQuery(
            "SELECT d FROM Devedor d WHERE d.id = :id AND d.usuarioId = :usuarioId", Devedor.class)
            .setParameter("id", id)
            .setParameter("usuarioId", usuarioId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
        return devedores.isEmpty() ? null : devedores.get(0);
    }

    public Devedor buscarPorVendaId(Long vendaId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Devedor> list = em.createQuery(
                "SELECT d FROM Devedor d WHERE d.vendaId = :vid AND d.usuarioId = :uid", Devedor.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();
            return list.isEmpty() ? null : list.get(0);
        });
    }

    public BigDecimal calcularSaldo(Long devedorId, Long usuarioId) {
        Devedor devedor = buscarPorId(devedorId, usuarioId);
        if (devedor == null) return BigDecimal.ZERO;
        BigDecimal pagos = pagamentoDividaDAO.totalPagoPorDevedor(usuarioId, devedorId);
        BigDecimal saldo = devedor.getDivida().subtract(pagos);
        return saldo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : saldo;
    }

    public BigDecimal totalDividasPendentes(Long usuarioId) {
        List<Devedor> todos = listarTodos(usuarioId);
        BigDecimal total = BigDecimal.ZERO;
        for (Devedor d : todos) {
            BigDecimal pagos = pagamentoDividaDAO.totalPagoPorDevedor(usuarioId, d.getId());
            BigDecimal saldo = d.getDivida().subtract(pagos);
            if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(saldo);
            }
        }
        return total;
    }

    public Devedor salvar(Devedor devedor) {
        return JPAUtil.emTransacao(devedor.getUsuarioId(), em -> {
            if (devedor.getId() == null) {
                em.persist(devedor);
                return devedor;
            } else {
                return em.merge(devedor);
            }
        });
    }

    public boolean deletar(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            Devedor devedor = em.createQuery(
                "SELECT d FROM Devedor d WHERE d.id = :id AND d.usuarioId = :usuarioId", Devedor.class)
                .setParameter("id", id)
                .setParameter("usuarioId", usuarioId)
                .getResultStream()
                .findFirst()
                .orElse(null);
            if (devedor == null) {
                return false;
            }
            if (pagamentoDividaDAO.temPagamentos(usuarioId, id)) {
                throw new IllegalStateException("Não é possível apagar uma dívida que já possui pagamentos registados.");
            }
            em.remove(devedor);
            return true;
        });
    }
}
