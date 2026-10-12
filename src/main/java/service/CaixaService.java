package service;

import dao.DefinicaoLojaDAO;
import dao.DespesaCaixaDAO;
import dao.PagamentoVendaDAO;
import dao.SessaoCaixaDAO;
import dao.VendaDAO;
import model.DefinicaoLoja;
import model.DespesaCaixa;
import model.SessaoCaixa;
import util.JPAUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static service.ServiceValidation.*;

public class CaixaService {

    private final SessaoCaixaDAO sessaoCaixaDAO = new SessaoCaixaDAO();
    private final DespesaCaixaDAO despesaCaixaDAO = new DespesaCaixaDAO();
    private final DefinicaoLojaDAO definicaoLojaDAO = new DefinicaoLojaDAO();
    private final VendaDAO vendaDAO = new VendaDAO();
    private final PagamentoVendaDAO pagamentoVendaDAO = new PagamentoVendaDAO();

    public SessaoCaixa abrirSessaoCaixa(BigDecimal valorInicial, String notaAbertura, Long operadorId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
            if (aberta != null) {
                throw new BarracaService.ConflitoException("Já existe uma sessão de caixa aberta.");
            }
            BigDecimal inicial = valorInicial != null ? moeda(valorInicial) : BigDecimal.ZERO;
            if (inicial.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("O valor inicial não pode ser negativo.");
            }
            SessaoCaixa sessao = new SessaoCaixa(usuarioId, inicial, notaAbertura != null ? notaAbertura.trim() : null, operadorId);
            em.persist(sessao);
            return sessao;
        });
    }

    public SessaoCaixa buscarSessaoAberta(Long usuarioId) {
        return sessaoCaixaDAO.buscarAberta(usuarioId);
    }

    public Map<String, Object> obterResumoSessaoAtual(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
            Map<String, Object> res = new LinkedHashMap<>();
            if (aberta == null) {
                res.put("aberta", false);
                return res;
            }
            BigDecimal vendasDinheiro = sessaoCaixaDAO.calcularVendasDinheiro(em, aberta.getId(), usuarioId);
            BigDecimal devolucoesDinheiro = sessaoCaixaDAO.calcularDevolucoesDinheiro(em, aberta.getId(), usuarioId);
            BigDecimal despesasDinheiro = sessaoCaixaDAO.calcularDespesasDinheiro(em, aberta.getId(), usuarioId);
            BigDecimal esperado = aberta.getValorInicial().add(vendasDinheiro).subtract(devolucoesDinheiro).subtract(despesasDinheiro);

            Long totalVendas = em.createQuery(
                "SELECT COUNT(v) FROM Venda v WHERE v.sessaoCaixa.id = :sid AND v.usuarioId = :uid AND v.estado != 'ANULADA'", Long.class)
                .setParameter("sid", aberta.getId())
                .setParameter("uid", usuarioId)
                .getSingleResult();

            res.put("aberta", true);
            res.put("id", aberta.getId());
            res.put("abertaEm", aberta.getAbertaEm().toString());
            res.put("valorInicial", decimalJson(aberta.getValorInicial(), 2));
            res.put("vendasDinheiro", decimalJson(vendasDinheiro, 2));
            res.put("devolucoesDinheiro", decimalJson(devolucoesDinheiro, 2));
            res.put("despesasDinheiro", decimalJson(despesasDinheiro, 2));
            res.put("valorEsperado", decimalJson(esperado, 2));
            res.put("totalVendas", totalVendas);
            res.put("notaAbertura", aberta.getNotaAbertura() != null ? aberta.getNotaAbertura() : "");
            return res;
        });
    }

    public SessaoCaixa fecharSessaoCaixa(Long sessaoId, BigDecimal valorContado, String notaFecho, Long operadorId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa sessao = sessaoCaixaDAO.buscarPorId(em, sessaoId, usuarioId);
            if (sessao == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Sessão de caixa não encontrada.");
            }
            if (!"ABERTA".equals(sessao.getEstado())) {
                throw new IllegalStateException("Esta sessão de caixa já está fechada.");
            }

            BigDecimal vendasDinheiro = sessaoCaixaDAO.calcularVendasDinheiro(em, sessao.getId(), usuarioId);
            BigDecimal devolucoesDinheiro = sessaoCaixaDAO.calcularDevolucoesDinheiro(em, sessao.getId(), usuarioId);
            BigDecimal despesasDinheiro = sessaoCaixaDAO.calcularDespesasDinheiro(em, sessao.getId(), usuarioId);
            BigDecimal esperado = sessao.getValorInicial().add(vendasDinheiro).subtract(devolucoesDinheiro).subtract(despesasDinheiro);

            BigDecimal contado = valorContado != null ? moeda(valorContado) : BigDecimal.ZERO;
            BigDecimal diferenca = contado.subtract(esperado);

            sessao.setValorContado(contado);
            sessao.setValorEsperado(esperado);
            sessao.setDiferenca(diferenca);
            sessao.setNotaFecho(notaFecho != null ? notaFecho.trim() : null);
            sessao.setFechadaEm(util.Fuso.agora());
            sessao.setFechadaPor(operadorId);
            sessao.setEstado("FECHADA");

            return em.merge(sessao);
        });
    }

    public Map<String, Object> registarDespesaCaixa(BigDecimal valor, String categoria, String descricao, Long operadorId, Long usuarioId) {
        if (descricao == null || descricao.trim().isEmpty()) {
            throw new IllegalArgumentException("A descrição da despesa é obrigatória.");
        }
        BigDecimal valorNorm = moeda(decimal(valor, "valor"));
        if (valorNorm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor da despesa deve ser maior que zero.");
        }

        return JPAUtil.emTransacao(usuarioId, em -> {
            SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
            if (aberta == null) {
                DefinicaoLoja def = definicaoLojaDAO.obter(usuarioId);
                if (def.getControloCaixa()) {
                    throw new IllegalStateException("Não há nenhuma sessão de caixa aberta para registar a saída.");
                }
            }

            DespesaCaixa despesa = new DespesaCaixa(usuarioId, aberta, valorNorm, categoria, descricao.trim(), operadorId);
            em.persist(despesa);
            em.flush();

            return formatarDespesaCaixa(despesa);
        });
    }

    public List<Map<String, Object>> listarDespesasCaixa(Long sessaoId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<DespesaCaixa> lista;
            if (sessaoId != null) {
                lista = despesaCaixaDAO.listarPorSessao(em, sessaoId, usuarioId);
            } else {
                SessaoCaixa aberta = sessaoCaixaDAO.buscarAberta(em, usuarioId);
                if (aberta != null) {
                    lista = despesaCaixaDAO.listarPorSessao(em, aberta.getId(), usuarioId);
                } else {
                    lista = despesaCaixaDAO.listarRecentes(em, usuarioId, 50);
                }
            }
            List<Map<String, Object>> res = new ArrayList<>();
            for (DespesaCaixa d : lista) {
                res.add(formatarDespesaCaixa(d));
            }
            return res;
        });
    }

    public List<SessaoCaixa> listarHistoricoSessoes(Long usuarioId, int limite) {
        return listarHistoricoSessoes(usuarioId, null, null, limite);
    }

    public List<SessaoCaixa> listarHistoricoSessoes(Long usuarioId, LocalDate inicio, LocalDate fim, int limite) {
        return sessaoCaixaDAO.listarHistorico(usuarioId, inicio, fim, limite);
    }

    public Map<String, Object> fechoCaixa(LocalDate data, Long usuarioId) {
        LocalDate dia = data != null ? data : util.Fuso.hoje();
        BigDecimal totalVendido = vendaDAO.totalVendasPeriodo(dia, dia, usuarioId);
        BigDecimal totalCusto = vendaDAO.totalCustoPeriodo(dia, dia, usuarioId);
        BigDecimal lucroEstimado = vendaDAO.lucroEstimadoPeriodo(dia, dia, usuarioId);
        long numeroVendas = vendaDAO.contarVendasDia(dia, usuarioId);
        long vendasAnuladas = vendaDAO.contarVendasAnuladasHoje(usuarioId, dia);
        Map<String, BigDecimal> totaisMetodo = pagamentoVendaDAO.totaisPorMetodo(usuarioId, dia);
        BigDecimal totalFiado = totaisMetodo.getOrDefault("FIADO", BigDecimal.ZERO);

        LocalDateTime inicioDia = dia.atStartOfDay();
        LocalDateTime fimDia = dia.plusDays(1).atStartOfDay();
        BigDecimal totalDespesas = JPAUtil.emTransacao(usuarioId, em -> despesaCaixaDAO.totalDespesasPeriodo(em, inicioDia, fimDia, usuarioId));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("data", dia.toString());
        res.put("totalVendido", decimalJson(totalVendido, 2));
        res.put("totalCusto", decimalJson(totalCusto, 2));
        res.put("lucroEstimado", decimalJson(lucroEstimado, 2));
        res.put("numeroVendas", numeroVendas);
        res.put("vendasAnuladas", vendasAnuladas);
        res.put("totalFiado", decimalJson(totalFiado, 2));
        res.put("totalDespesas", decimalJson(totalDespesas, 2));
        Map<String, String> formatados = new LinkedHashMap<>();
        totaisMetodo.forEach((k, v) -> formatados.put(k, decimalJson(v, 2)));
        res.put("totaisPorMetodo", formatados);
        return res;
    }

    private Map<String, Object> formatarDespesaCaixa(DespesaCaixa d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("sessaoId", d.getSessaoCaixa() != null ? d.getSessaoCaixa().getId() : null);
        m.put("valor", decimalJson(d.getValor(), 2));
        m.put("categoria", d.getCategoria());
        m.put("descricao", d.getDescricao());
        m.put("criadaEm", d.getCriadaEm() != null ? d.getCriadaEm().toString() : "");
        m.put("criadoPor", d.getCriadoPor());
        return m;
    }
}
