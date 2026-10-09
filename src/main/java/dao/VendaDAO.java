package dao;

import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
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

    public synchronized Long proximoNumero(EntityManager em, Long usuarioId) {
        Long max = em.createQuery(
            "SELECT COALESCE(MAX(v.numero), 0) FROM Venda v WHERE v.usuarioId = :uid", Long.class)
            .setParameter("uid", usuarioId)
            .getSingleResult();
        return max + 1;
    }

    public Venda buscarPorId(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Venda> list = em.createQuery(
                "SELECT v FROM Venda v WHERE v.id = :id AND v.usuarioId = :uid", Venda.class)
                .setParameter("id", id)
                .setParameter("uid", usuarioId)
                .getResultList();
            return list.isEmpty() ? null : list.get(0);
        });
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
        LocalDateTime fimTs = fim != null ? fim.atTime(23, 59, 59) : null;

        StringBuilder where = new StringBuilder(" WHERE v.usuarioId = :uid");
        if (estado != null && !estado.isBlank() && !"todos".equalsIgnoreCase(estado)) {
            where.append(" AND v.estado = :estado");
        }
        if (inicioTs != null) where.append(" AND v.criadaEm >= :inicio");
        if (fimTs != null) where.append(" AND v.criadaEm <= :fim");
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
                if (fimTs != null) stmt.setParameter("fim", fimTs);
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

    public Map<LocalDate, BigDecimal> totaisPorDia(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimTs = fim.atTime(23, 59, 59);
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Object[]> rows = em.createQuery(
                "SELECT v.criadaEm, v.total FROM Venda v " +
                "WHERE v.usuarioId = :uid AND v.estado = 'CONCLUIDA' AND v.criadaEm BETWEEN :inicio AND :fim", Object[].class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getResultList();

            Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
            for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
                totals.put(d, BigDecimal.ZERO);
            }
            for (Object[] row : rows) {
                LocalDateTime ts = (LocalDateTime) row[0];
                BigDecimal tot = (BigDecimal) row[1];
                LocalDate date = ts.toLocalDate();
                totals.merge(date, tot, BigDecimal::add);
            }
            return totals;
        });
    }

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return totalVendasPeriodo(LocalDate.now(), LocalDate.now(), usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimTs = fim.atTime(23, 59, 59);
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal total = em.createQuery(
                "SELECT SUM(v.total) FROM Venda v WHERE v.usuarioId = :uid AND v.estado = 'CONCLUIDA' AND v.criadaEm BETWEEN :inicio AND :fim",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getSingleResult();
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    public BigDecimal totalCustoPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        LocalDateTime inicioTs = inicio.atStartOfDay();
        LocalDateTime fimTs = fim.atTime(23, 59, 59);
        return JPAUtil.emTransacao(usuarioId, em -> {
            BigDecimal total = em.createQuery(
                "SELECT SUM(v.totalCusto) FROM Venda v WHERE v.usuarioId = :uid AND v.estado = 'CONCLUIDA' AND v.criadaEm BETWEEN :inicio AND :fim",
                BigDecimal.class)
                .setParameter("uid", usuarioId)
                .setParameter("inicio", inicioTs)
                .setParameter("fim", fimTs)
                .getSingleResult();
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    public long contarVendasHoje(Long usuarioId) {
        LocalDateTime inicioTs = LocalDate.now().atStartOfDay();
        LocalDateTime fimTs = LocalDate.now().atTime(23, 59, 59);
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(v) FROM Venda v WHERE v.usuarioId = :uid AND v.estado = 'CONCLUIDA' AND v.criadaEm BETWEEN :inicio AND :fim",
            Long.class)
            .setParameter("uid", usuarioId)
            .setParameter("inicio", inicioTs)
            .setParameter("fim", fimTs)
            .getSingleResult());
    }

    public long contarVendasAnuladasHoje(Long usuarioId, LocalDate data) {
        LocalDateTime inicioTs = data.atStartOfDay();
        LocalDateTime fimTs = data.atTime(23, 59, 59);
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(v) FROM Venda v WHERE v.usuarioId = :uid AND v.estado = 'ANULADA' AND v.criadaEm BETWEEN :inicio AND :fim",
            Long.class)
            .setParameter("uid", usuarioId)
            .setParameter("inicio", inicioTs)
            .setParameter("fim", fimTs)
            .getSingleResult());
    }

    public boolean existeParaProduto(Long produtoId, Long usuarioId) {
        return itemVendaDAO.existeParaProduto(produtoId, usuarioId);
    }
}
