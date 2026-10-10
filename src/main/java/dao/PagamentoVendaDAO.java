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
        LocalDate d = data != null ? data : LocalDate.now();
        LocalDateTime inicio = d.atStartOfDay();
        LocalDateTime fimExclusive = d.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Object[]> rows = em.createQuery(
                "SELECT p.metodo, SUM(p.valor - p.troco) FROM PagamentoVenda p JOIN p.venda v " +
                "WHERE p.usuarioId = :uid AND v.estado != 'ANULADA' AND v.criadaEm >= :inicio AND v.criadaEm < :fimExclusive " +
                "GROUP BY p.metodo", Object[].class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicio)
                .setParameter("fimExclusive", fimExclusive)
                .getResultList();

            Map<String, BigDecimal> mapa = new LinkedHashMap<>();
            for (Object[] r : rows) {
                if (r[0] != null) {
                    BigDecimal val = r[1] instanceof BigDecimal ? (BigDecimal) r[1]
                        : (r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO);
                    mapa.put(r[0].toString(), val);
                }
            }

            // Subtrair devoluções ocorridas nesta data no método da venda original
            List<model.Devolucao> devolucoes = em.createQuery(
                "SELECT d FROM Devolucao d WHERE d.usuarioId = :uid AND d.criadaEm >= :inicio AND d.criadaEm < :fimExclusive",
                model.Devolucao.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicio)
                .setParameter("fimExclusive", fimExclusive)
                .getResultList();

            if (!devolucoes.isEmpty()) {
                List<Long> vendaIds = new java.util.ArrayList<>();
                for (model.Devolucao devItem : devolucoes) {
                    if (devItem.getVenda() != null && devItem.getVenda().getId() != null) {
                        vendaIds.add(devItem.getVenda().getId());
                    }
                }

                Map<Long, List<PagamentoVenda>> pagsPorVenda = new java.util.HashMap<>();
                if (!vendaIds.isEmpty()) {
                    List<PagamentoVenda> todosPags = em.createQuery(
                        "SELECT p FROM PagamentoVenda p WHERE p.venda.id IN (:vids) AND p.usuarioId = :uid ORDER BY p.id ASC",
                        PagamentoVenda.class)
                        .setParameter("vids", vendaIds)
                        .setParameter("uid", usuarioId)
                        .getResultList();

                    for (PagamentoVenda p : todosPags) {
                        pagsPorVenda.computeIfAbsent(p.getVenda().getId(), k -> new java.util.ArrayList<>()).add(p);
                    }
                }

                for (model.Devolucao dev : devolucoes) {
                    List<PagamentoVenda> pags = dev.getVenda() != null
                        ? pagsPorVenda.getOrDefault(dev.getVenda().getId(), java.util.Collections.emptyList())
                        : java.util.Collections.emptyList();

                    if (pags.isEmpty()) continue;

                    if (pags.size() == 1) {
                        String met = pags.get(0).getMetodo();
                        mapa.merge(met, dev.getTotal().negate(), BigDecimal::add);
                    } else {
                        BigDecimal totalNet = pags.stream()
                            .map(p -> p.getValor().subtract(p.getTroco() != null ? p.getTroco() : BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                        if (totalNet.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal restanteDev = dev.getTotal();
                            for (int i = 0; i < pags.size(); i++) {
                                PagamentoVenda p = pags.get(i);
                                BigDecimal pagNet = p.getValor().subtract(p.getTroco() != null ? p.getTroco() : BigDecimal.ZERO);
                                BigDecimal quota;
                                if (i == pags.size() - 1) {
                                    quota = restanteDev;
                                } else {
                                    quota = dev.getTotal().multiply(pagNet).divide(totalNet, 2, java.math.RoundingMode.HALF_UP);
                                    restanteDev = restanteDev.subtract(quota);
                                }
                                mapa.merge(p.getMetodo(), quota.negate(), BigDecimal::add);
                            }
                        } else {
                            mapa.merge(pags.get(0).getMetodo(), dev.getTotal().negate(), BigDecimal::add);
                        }
                    }
                }
            }

            return mapa;
        });
    }
}
