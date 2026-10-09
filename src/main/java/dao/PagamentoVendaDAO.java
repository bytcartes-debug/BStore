package dao;

import model.PagamentoVenda;
import util.JPAUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PagamentoVendaDAO extends GenericDAO<PagamentoVenda> {

    public PagamentoVendaDAO() {
        super(PagamentoVenda.class);
    }

    public List<PagamentoVenda> listarPorVenda(Long usuarioId, Long vendaId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT p FROM PagamentoVenda p WHERE p.usuarioId = :uid AND p.venda.id = :vid ORDER BY p.id ASC",
            PagamentoVenda.class)
            .setParameter("uid", usuarioId)
            .setParameter("vid", vendaId)
            .getResultList());
    }

    public Map<String, BigDecimal> totaisPorMetodo(Long usuarioId, LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(23, 59, 59);
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Object[]> rows = em.createQuery(
                "SELECT p.metodo, SUM(p.valor) FROM PagamentoVenda p JOIN p.venda v " +
                "WHERE p.usuarioId = :uid AND v.estado = 'CONCLUIDA' AND v.criadaEm BETWEEN :inicio AND :fim " +
                "GROUP BY p.metodo", Object[].class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getResultList();
            Map<String, BigDecimal> mapa = new LinkedHashMap<>();
            for (Object[] r : rows) {
                mapa.put((String) r[0], (BigDecimal) r[1]);
            }
            return mapa;
        });
    }
}
