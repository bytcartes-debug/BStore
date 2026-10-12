package service;

import dao.DevedorDAO;
import dao.PagamentoDividaDAO;
import model.Devedor;
import model.PagamentoDivida;
import util.JPAUtil;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static service.ServiceValidation.*;

public class DevedorService {

    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final PagamentoDividaDAO pagamentoDividaDAO = new PagamentoDividaDAO();

    public List<Devedor> listarTodos(Long usuarioId) {
        return devedorDAO.listarTodos(usuarioId);
    }

    public Devedor buscarPorId(Long id, Long usuarioId) {
        return devedorDAO.buscarPorId(id, usuarioId);
    }

    public Devedor salvar(Devedor devedor) {
        return devedorDAO.salvar(devedor);
    }

    public boolean deletar(Long id, Long usuarioId) {
        return devedorDAO.deletar(id, usuarioId);
    }

    public BigDecimal calcularSaldo(Long devedorId, Long usuarioId) {
        return devedorDAO.calcularSaldo(devedorId, usuarioId);
    }

    public BigDecimal totalDividasPendentes(Long usuarioId) {
        return devedorDAO.totalDividasPendentes(usuarioId);
    }

    public long contarTodos(Long usuarioId) {
        return devedorDAO.contarTodos(usuarioId);
    }

    public Map<String, Object> registarPagamentoDivida(Long devedorId, BigDecimal valor, String metodo, String observacao, Long usuarioId) {
        if (metodo == null || metodo.trim().isEmpty()) {
            throw new IllegalArgumentException("O método de pagamento é obrigatório.");
        }
        String metNorm = metodo.trim().toUpperCase();
        if (!List.of("DINHEIRO", "MPESA", "EMOLA", "MKESH", "CARTAO").contains(metNorm)) {
            throw new IllegalArgumentException("Método de pagamento de dívida inválido: " + metNorm);
        }
        BigDecimal valorNorm = moeda(decimal(valor, "valor"));
        if (valorNorm.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero.");
        }

        return JPAUtil.emTransacao(usuarioId, em -> {
            Devedor devedor = devedorDAO.buscarPorIdParaBloqueio(em, devedorId, usuarioId);
            if (devedor == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Devedor não encontrado.");
            }
            BigDecimal totalPagoAnterior = pagamentoDividaDAO.totalPagoPorDevedor(usuarioId, devedorId);
            BigDecimal saldoActual = devedor.getDivida().subtract(totalPagoAnterior);
            if (valorNorm.compareTo(saldoActual) > 0) {
                throw new IllegalArgumentException("O valor a pagar (" + valorNorm + ") é superior ao saldo da dívida (" + saldoActual + ").");
            }

            PagamentoDivida pag = new PagamentoDivida(
                usuarioId, devedor, valorNorm, metNorm, util.Fuso.agora(), observacao);
            em.persist(pag);

            BigDecimal novoSaldo = saldoActual.subtract(valorNorm);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("id", pag.getId());
            res.put("devedorId", devedor.getId());
            res.put("devedorNome", devedor.getNome());
            res.put("valor", decimalJson(valorNorm, 2));
            res.put("metodo", metNorm);
            res.put("criadoEm", pag.getCriadoEm().toString());
            res.put("observacao", observacao != null ? observacao : "");
            res.put("saldoRestante", decimalJson(novoSaldo, 2));
            return res;
        });
    }

    public List<PagamentoDivida> listarPagamentosPorDevedor(Long devedorId, Long usuarioId) {
        return pagamentoDividaDAO.listarPorDevedor(usuarioId, devedorId);
    }
}
