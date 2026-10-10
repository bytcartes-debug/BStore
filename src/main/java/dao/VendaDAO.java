package dao;

import model.DefinicaoLoja;
import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class VendaDAO extends GenericDAO<Venda> {

    private final ItemVendaDAO itemVendaDAO = new ItemVendaDAO();

    public VendaDAO() {
        super(Venda.class);
    }

    public Long proximoNumero(EntityManager em, Long usuarioId) {
        DefinicaoLoja def = em.find(DefinicaoLoja.class, usuarioId, LockModeType.PESSIMISTIC_WRITE);
        if (def == null) {
            Long max = em.createQuery(
                "SELECT COALESCE(MAX(v.numero), 0) FROM Venda v WHERE v.usuarioId = :uid", Long.class)
                .setParameter("uid", usuarioId)
                .getSingleResult();
            def = new DefinicaoLoja(usuarioId, false, "Loja");
            def.setUltimoNumeroVenda(max);
            em.persist(def);
            em.flush();
            def = em.find(DefinicaoLoja.class, usuarioId, LockModeType.PESSIMISTIC_WRITE);
        }
        long current = def.getUltimoNumeroVenda() != null ? def.getUltimoNumeroVenda() : 0L;
        if (current == 0L) {
            Long max = em.createQuery(
                "SELECT COALESCE(MAX(v.numero), 0) FROM Venda v WHERE v.usuarioId = :uid", Long.class)
                .setParameter("uid", usuarioId)
                .getSingleResult();
            if (max != null && max > current) {
                current = max;
            }
        }
        long proximo = current + 1L;
        def.setUltimoNumeroVenda(proximo);
        def.setAtualizadoEm(LocalDateTime.now());
        return proximo;
    }

    public Venda buscarPorId(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> buscarPorId(em, id, usuarioId));
    }

    public Venda buscarPorId(EntityManager em, Long id, Long usuarioId) {
        List<Venda> list = em.createQuery(
            "SELECT v FROM Venda v WHERE v.id = :id AND v.usuarioId = :uid", Venda.class)
            .setParameter("id", id)
            .setParameter("uid", usuarioId)
            .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    public Venda buscarPorUuidCliente(EntityManager em, String uuidCliente, Long usuarioId) {
        if (uuidCliente == null || uuidCliente.trim().isEmpty()) return null;
        List<Venda> list = em.createQuery(
            "SELECT v FROM Venda v WHERE v.uuidCliente = :uuid AND v.usuarioId = :uid", Venda.class)
            .setParameter("uuid", uuidCliente.trim())
            .setParameter("uid", usuarioId)
            .getResultList();
        if (list.isEmpty()) return null;
        Venda v = list.get(0);
        org.hibernate.Hibernate.initialize(v.getItens());
        for (model.ItemVenda iv : v.getItens()) {
            org.hibernate.Hibernate.initialize(iv.getProduto());
        }
        org.hibernate.Hibernate.initialize(v.getPagamentos());
        return v;
    }

    public Venda buscarPorIdComDetalhes(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            Venda v = em.find(Venda.class, id);
            if (v == null || !usuarioId.equals(v.getUsuarioId())) return null;
            org.hibernate.Hibernate.initialize(v.getItens());
            for (model.ItemVenda iv : v.getItens()) {
                org.hibernate.Hibernate.initialize(iv.getProduto());
            }
            org.hibernate.Hibernate.initialize(v.getPagamentos());
            return v;
        });
    }

    public List<Venda> listarOrdenado(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT v FROM Venda v WHERE v.usuarioId = :uid ORDER BY v.criadaEm DESC, v.id DESC", Venda.class)
            .setParameter("uid", usuarioId)
            .getResultList());
    }

    public Pagina<Venda> listarPagina(Long usuarioId, int page, int pageSize, String pesquisa,
                                      LocalDate inicio, LocalDate fim) {
        return listarPagina(usuarioId, page, pageSize, pesquisa, inicio, fim, null, null);
    }

    public Pagina<Venda> listarPagina(Long usuarioId, int page, int pageSize, String pesquisa,
                                      LocalDate inicio, LocalDate fim, String metodo, String estado) {
        int offset = Pagina.offset(page, pageSize);
        String termo = Pagina.pesquisa(pesquisa);
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("O início não pode ser posterior ao fim.");
        }

        LocalDateTime inicioTs = inicio != null ? inicio.atStartOfDay() : null;
        LocalDateTime fimExclusive = fim != null ? fim.plusDays(1).atStartOfDay() : null;

        StringBuilder where = new StringBuilder(" WHERE v.usuarioId = :uid");
        if (estado != null && !estado.isBlank() && !"todos".equalsIgnoreCase(estado)) {
            where.append(" AND v.estado = :estado");
        }
        if (inicioTs != null) where.append(" AND v.criadaEm >= :inicio");
        if (fimExclusive != null) where.append(" AND v.criadaEm < :fimExclusive");
        if (!termo.isEmpty()) {
            where.append(" AND (LOWER(v.observacao) LIKE :q ESCAPE '!'");
            where.append(" OR EXISTS (SELECT 1 FROM ItemVenda iv WHERE iv.venda.id = v.id AND LOWER(iv.produto.nome) LIKE :q ESCAPE '!')");
            try {
                Long num = Long.parseLong(termo);
                where.append(" OR v.numero = ").append(num);
            } catch (NumberFormatException ignored) {}
            where.append(")");
        }
        if (metodo != null && !metodo.isBlank() && !"todos".equalsIgnoreCase(metodo)) {
            where.append(" AND EXISTS (SELECT 1 FROM PagamentoVenda pv WHERE pv.venda.id = v.id AND pv.metodo = :metodo)");
        }

        return JPAUtil.emTransacao(usuarioId, em -> {
            TypedQuery<Long> count = em.createQuery("SELECT COUNT(v) FROM Venda v" + where, Long.class);
            TypedQuery<Venda> query = em.createQuery(
                "SELECT v FROM Venda v" + where + " ORDER BY v.criadaEm DESC, v.id DESC", Venda.class);

            for (TypedQuery<?> stmt : List.of(count, query)) {
                stmt.setParameter("uid", usuarioId);
                if (estado != null && !estado.isBlank() && !"todos".equalsIgnoreCase(estado)) {
                    stmt.setParameter("estado", estado.toUpperCase());
                }
                if (inicioTs != null) stmt.setParameter("inicio", inicioTs);
                if (fimExclusive != null) stmt.setParameter("fimExclusive", fimExclusive);
                if (!termo.isEmpty()) stmt.setParameter("q", "%" + termo + "%");
                if (metodo != null && !metodo.isBlank() && !"todos".equalsIgnoreCase(metodo)) {
                    stmt.setParameter("metodo", metodo.toUpperCase());
                }
            }

            long total = count.getSingleResult();
            List<Venda> items = query.setFirstResult(offset).setMaxResults(pageSize).getResultList();
            return new Pagina<>(items, total, page, pageSize);
        });
    }

    public List<Venda> recentes(Long usuarioId, int limite) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT v FROM Venda v WHERE v.usuarioId = :uid ORDER BY v.criadaEm DESC, v.id DESC", Venda.class)
            .setParameter("uid", usuarioId)
            .setMaxResults(limite)
            .getResultList());
    }

    // Decisão contábil (faça.txt 1.2): O valor e o custo devolvidos são subtraídos na data em que a devolução ocorreu,
    // pois é o momento em que o dinheiro sai fisicamente/digitalmente da loja e o produto retorna ao stock.
    public Map<LocalDate, BigDecimal> totaisPorDia(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimExclusive = fim.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> {
            java.sql.Timestamp tsInicio = java.sql.Timestamp.valueOf(inicioTs);
            java.sql.Timestamp tsFim = java.sql.Timestamp.valueOf(fimExclusive);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                "SELECT v.criada_em, v.total, 1 FROM vendas v " +
                "WHERE v.usuario_id = :uid AND v.estado != 'ANULADA' AND v.criada_em >= CAST(:inicio AS timestamp) AND v.criada_em < CAST(:fimExclusive AS timestamp) " +
                "UNION ALL " +
                "SELECT d.criada_em, d.total, -1 FROM devolucoes d " +
                "WHERE d.usuario_id = :uid AND d.criada_em >= CAST(:inicio AS timestamp) AND d.criada_em < CAST(:fimExclusive AS timestamp)")
                .setParameter("uid", usuarioId)
                .setParameter("inicio", tsInicio)
                .setParameter("fimExclusive", tsFim)
                .getResultList();

            Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
            for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
                totals.put(d, BigDecimal.ZERO);
            }
            for (Object[] row : rows) {
                LocalDate date;
                if (row[0] instanceof java.sql.Timestamp) {
                    date = ((java.sql.Timestamp) row[0]).toLocalDateTime().toLocalDate();
                } else if (row[0] instanceof LocalDateTime) {
                    date = ((LocalDateTime) row[0]).toLocalDate();
                } else {
                    date = LocalDate.parse(row[0].toString().substring(0, 10));
                }
                BigDecimal tot = new BigDecimal(row[1].toString());
                int sinal = ((Number) row[2]).intValue();
                if (sinal > 0) {
                    totals.computeIfPresent(date, (k, v) -> v.add(tot));
                } else {
                    totals.computeIfPresent(date, (k, v) -> v.subtract(tot));
                }
            }
            return totals;
        });
    }

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return totalVendasPeriodo(LocalDate.now(), LocalDate.now(), usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimExclusive = fim.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal totalVendas = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.usuarioId = :uid AND v.estado != 'ANULADA' AND v.criadaEm >= :inicio AND v.criadaEm < :fimExclusive",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fimExclusive", fimExclusive)
                .getSingleResult();
            if (totalVendas == null) totalVendas = BigDecimal.ZERO;

            BigDecimal totalDevolucoes = em.createQuery(
                "SELECT SUM(d.total) FROM Devolucao d WHERE d.usuarioId = :uid AND d.criadaEm >= :inicio AND d.criadaEm < :fimExclusive",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fimExclusive", fimExclusive)
                .getSingleResult();
            if (totalDevolucoes == null) totalDevolucoes = BigDecimal.ZERO;

            return totalVendas.subtract(totalDevolucoes);
        });
    }

    public BigDecimal totalVendasComCustoConhecidoPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimExclusive = fim.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal vendasComCusto = em.createQuery(
                "SELECT SUM(iv.total) FROM ItemVenda iv WHERE iv.usuarioId = :uid AND iv.custoConhecido = TRUE " +
                "AND iv.venda.estado != 'ANULADA' AND iv.venda.criadaEm >= :inicio AND iv.venda.criadaEm < :fimExclusive",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fimExclusive", fimExclusive)
                .getSingleResult();
            if (vendasComCusto == null) vendasComCusto = BigDecimal.ZERO;

            BigDecimal devolucoesComCusto = em.createQuery(
                "SELECT SUM(idv.valor) FROM ItemDevolucao idv WHERE idv.devolucao.usuarioId = :uid " +
                "AND idv.itemVenda.custoConhecido = TRUE AND idv.devolucao.criadaEm >= :inicio AND idv.devolucao.criadaEm < :fimExclusive",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fimExclusive", fimExclusive)
                .getSingleResult();
            if (devolucoesComCusto == null) devolucoesComCusto = BigDecimal.ZERO;

            return vendasComCusto.subtract(devolucoesComCusto);
        });
    }

    public BigDecimal totalCustoPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimExclusive = fim.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal custoVendas = em.createQuery(
                "SELECT SUM(iv.quantidade * iv.custoUnitario) FROM ItemVenda iv WHERE iv.usuarioId = :uid AND iv.custoConhecido = TRUE " +
                "AND iv.venda.estado != 'ANULADA' AND iv.venda.criadaEm >= :inicio AND iv.venda.criadaEm < :fimExclusive",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fimExclusive", fimExclusive)
                .getSingleResult();
            if (custoVendas == null) custoVendas = BigDecimal.ZERO;

            BigDecimal custoDevolucoes = em.createQuery(
                "SELECT SUM(idv.quantidade * idv.itemVenda.custoUnitario) FROM ItemDevolucao idv WHERE idv.devolucao.usuarioId = :uid " +
                "AND idv.itemVenda.custoConhecido = TRUE AND idv.devolucao.criadaEm >= :inicio AND idv.devolucao.criadaEm < :fimExclusive",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fimExclusive", fimExclusive)
                .getSingleResult();
            if (custoDevolucoes == null) custoDevolucoes = BigDecimal.ZERO;

            return custoVendas.subtract(custoDevolucoes);
        });
    }

    public BigDecimal lucroEstimadoPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        BigDecimal vendasComCusto = totalVendasComCustoConhecidoPeriodo(inicio, fim, usuarioId);
        BigDecimal custo = totalCustoPeriodo(inicio, fim, usuarioId);
        return vendasComCusto.subtract(custo).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public Map<String, BigDecimal> lucroDashboard(LocalDate hoje, Long usuarioId) {
        LocalDateTime inicioHoje = hoje.atStartOfDay();
        LocalDateTime inicio7Dias = hoje.minusDays(6).atStartOfDay();
        LocalDateTime fimExclusive = hoje.plusDays(1).atStartOfDay();

        return JPAUtil.emTransacao(usuarioId, em -> {
            java.sql.Timestamp tsHoje = java.sql.Timestamp.valueOf(inicioHoje);
            java.sql.Timestamp ts7Dias = java.sql.Timestamp.valueOf(inicio7Dias);
            java.sql.Timestamp tsFim = java.sql.Timestamp.valueOf(fimExclusive);

            Object resVendasObj = em.createNativeQuery(
                "SELECT " +
                "COALESCE(SUM(CASE WHEN v.criada_em >= CAST(:inicioHoje AS timestamp) THEN iv.total - (iv.quantidade * iv.custo_unitario) ELSE 0.00 END), 0.00), " +
                "COALESCE(SUM(iv.total - (iv.quantidade * iv.custo_unitario)), 0.00) " +
                "FROM venda_itens iv " +
                "JOIN vendas v ON v.id = iv.venda_id " +
                "WHERE iv.usuario_id = :uid AND iv.custo_conhecido = TRUE AND v.estado != 'ANULADA' " +
                "AND v.criada_em >= CAST(:inicio7Dias AS timestamp) AND v.criada_em < CAST(:fimExclusive AS timestamp)")
                .setParameter("uid", usuarioId)
                .setParameter("inicioHoje", tsHoje)
                .setParameter("inicio7Dias", ts7Dias)
                .setParameter("fimExclusive", tsFim)
                .getSingleResult();

            Object[] rowVendas = resVendasObj instanceof Object[] ? (Object[]) resVendasObj : new Object[]{resVendasObj};

            Object resDevsObj = em.createNativeQuery(
                "SELECT " +
                "COALESCE(SUM(CASE WHEN d.criada_em >= CAST(:inicioHoje AS timestamp) THEN idv.valor - (idv.quantidade * iv.custo_unitario) ELSE 0.00 END), 0.00), " +
                "COALESCE(SUM(idv.valor - (idv.quantidade * iv.custo_unitario)), 0.00) " +
                "FROM devolucao_itens idv " +
                "JOIN devolucoes d ON d.id = idv.devolucao_id " +
                "JOIN venda_itens iv ON iv.id = idv.item_venda_id " +
                "WHERE d.usuario_id = :uid AND iv.custo_conhecido = TRUE " +
                "AND d.criada_em >= CAST(:inicio7Dias AS timestamp) AND d.criada_em < CAST(:fimExclusive AS timestamp)")
                .setParameter("uid", usuarioId)
                .setParameter("inicioHoje", tsHoje)
                .setParameter("inicio7Dias", ts7Dias)
                .setParameter("fimExclusive", tsFim)
                .getSingleResult();

            Object[] rowDevs = resDevsObj instanceof Object[] ? (Object[]) resDevsObj : new Object[]{resDevsObj};

            BigDecimal lucroVendaHoje = rowVendas.length > 0 && rowVendas[0] != null ? new BigDecimal(rowVendas[0].toString()) : BigDecimal.ZERO;
            BigDecimal lucroVenda7Dias = rowVendas.length > 1 && rowVendas[1] != null ? new BigDecimal(rowVendas[1].toString()) : BigDecimal.ZERO;

            BigDecimal lucroDevHoje = rowDevs.length > 0 && rowDevs[0] != null ? new BigDecimal(rowDevs[0].toString()) : BigDecimal.ZERO;
            BigDecimal lucroDev7Dias = rowDevs.length > 1 && rowDevs[1] != null ? new BigDecimal(rowDevs[1].toString()) : BigDecimal.ZERO;

            Map<String, BigDecimal> res = new HashMap<>();
            res.put("hoje", lucroVendaHoje.subtract(lucroDevHoje).setScale(2, java.math.RoundingMode.HALF_UP));
            res.put("7dias", lucroVenda7Dias.subtract(lucroDev7Dias).setScale(2, java.math.RoundingMode.HALF_UP));
            return res;
        });
    }

    public long contarVendasHoje(Long usuarioId) {
        return contarVendasDia(LocalDate.now(), usuarioId);
    }

    public long contarVendasDia(LocalDate data, Long usuarioId) {
        LocalDate d = data != null ? data : LocalDate.now();
        LocalDateTime inicioTs = d.atStartOfDay();
        LocalDateTime fimExclusive = d.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(v) FROM Venda v WHERE v.usuarioId = :uid AND v.estado != 'ANULADA' AND v.criadaEm >= :inicio AND v.criadaEm < :fimExclusive",
            Long.class)
            .setParameter("uid", usuarioId)
            .setParameter("inicio", inicioTs)
            .setParameter("fimExclusive", fimExclusive)
            .getSingleResult());
    }

    public long contarVendasAnuladasHoje(Long usuarioId, LocalDate data) {
        LocalDate d = data != null ? data : LocalDate.now();
        LocalDateTime inicioTs = d.atStartOfDay();
        LocalDateTime fimExclusive = d.plusDays(1).atStartOfDay();
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(v) FROM Venda v WHERE v.usuarioId = :uid AND v.estado = 'ANULADA' AND v.criadaEm >= :inicio AND v.criadaEm < :fimExclusive",
            Long.class)
            .setParameter("uid", usuarioId)
            .setParameter("inicio", inicioTs)
            .setParameter("fimExclusive", fimExclusive)
            .getSingleResult());
    }

    public boolean existeParaProduto(Long produtoId, Long usuarioId) {
        return itemVendaDAO.existeParaProduto(produtoId, usuarioId);
    }
}
