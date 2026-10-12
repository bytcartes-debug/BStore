package service;

import dao.DevedorDAO;
import dao.PagamentoDividaDAO;
import dao.VendaDAO;
import model.Devedor;
import model.ItemVenda;
import model.PagamentoVenda;
import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static service.ServiceValidation.*;

public class VendaService {

    private final VendaDAO vendaDAO = new VendaDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final PagamentoDividaDAO pagamentoDividaDAO = new PagamentoDividaDAO();
    private final ProcessadorVendaLote processadorVendaLote = new ProcessadorVendaLote();

    public Venda registarVenda(Long produtoId, BigDecimal quantidade, String observacao, Long usuarioId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("produtoId", produtoId);
        item.put("quantidade", quantidade);
        Map<String, Object> resultado = registarVendaLote(List.of(item), usuarioId);
        return (Venda) resultado.get("venda");
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens, Long usuarioId) {
        return registarVendaLote(itens, null, null, null, null, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        return registarVendaLote(itens, pagamentos, clienteId, observacao, null, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                String uuidCliente,
                                                Long usuarioId) {
        try {
            return JPAUtil.emTransacao(usuarioId, em -> executarVendaLote(em, itens, pagamentos, clienteId, observacao, uuidCliente, usuarioId));
        } catch (PessimisticLockException e) {
            throw new BarracaService.ConflitoException("O stock está a ser actualizado por outra venda. Tente novamente.");
        }
    }

    public static Map<Long, BigDecimal> normalizarCarrinho(List<Map<String, Object>> itens) {
        if (itens == null || itens.isEmpty()) throw new IllegalArgumentException("Carrinho está vazio.");
        Map<Long, BigDecimal> quantidades = new TreeMap<>();
        for (Map<String, Object> item : itens) {
            if (item == null) throw new IllegalArgumentException("Item de venda inválido.");
            Long produtoId = longValue(item.get("produtoId"), "produtoId");
            BigDecimal qtd = quantidade(decimal(item.get("quantidade"), "quantidade"));
            if (produtoId <= 0 || qtd.signum() <= 0) throw new IllegalArgumentException("Produto e quantidade devem ser positivos.");
            quantidades.merge(produtoId, qtd, BigDecimal::add);
        }
        return quantidades;
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                Map<Long, BigDecimal> quantidades,
                                                Long usuarioId) {
        return executarVendaLote(em, quantidades, null, null, null, usuarioId);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                Map<Long, BigDecimal> quantidades,
                                                List<Map<String, Object>> pagamentosRaw,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        List<Map<String, Object>> itens = new ArrayList<>();
        if (quantidades != null) {
            for (Map.Entry<Long, BigDecimal> entry : quantidades.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("produtoId", entry.getKey());
                item.put("quantidade", entry.getValue());
                itens.add(item);
            }
        }
        return executarVendaLote(em, itens, pagamentosRaw, clienteId, observacao, null, usuarioId);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                List<Map<String, Object>> itensRaw,
                                                List<Map<String, Object>> pagamentosRaw,
                                                Long clienteId,
                                                String observacao,
                                                String uuidCliente,
                                                Long usuarioId) {
        return processadorVendaLote.executar(em, itensRaw, pagamentosRaw, clienteId, observacao, uuidCliente, usuarioId);
    }

    public Venda anularVenda(Long vendaId, String motivo, Long usuarioId) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("O motivo da anulação é obrigatório.");
        }
        return JPAUtil.emTransacao(usuarioId, em -> {
            Venda venda = em.find(Venda.class, vendaId, LockModeType.PESSIMISTIC_WRITE);
            if (venda == null || !usuarioId.equals(venda.getUsuarioId())) {
                throw new BarracaService.RecursoNaoEncontradoException("Venda não encontrada.");
            }
            if ("ANULADA".equals(venda.getEstado())) {
                return venda;
            }
            if ("PARCIALMENTE_DEVOLVIDA".equals(venda.getEstado()) || "DEVOLVIDA".equals(venda.getEstado())) {
                throw new BarracaService.ConflitoException("Não é possível anular uma venda com devoluções registadas.");
            }
            if (!"CONCLUIDA".equals(venda.getEstado())) {
                throw new IllegalStateException("Esta venda não pode ser anulada (estado actual: " + venda.getEstado() + ").");
            }

            List<PagamentoVenda> pagamentos = em.createQuery(
                "SELECT p FROM PagamentoVenda p WHERE p.venda.id = :vid AND p.usuarioId = :uid", PagamentoVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();

            PagamentoVenda fiado = pagamentos.stream()
                .filter(p -> "FIADO".equalsIgnoreCase(p.getMetodo()))
                .findFirst()
                .orElse(null);

            if (fiado != null && venda.getClienteId() != null) {
                Devedor devedor = devedorDAO.buscarPorIdParaBloqueio(em, venda.getClienteId(), usuarioId);
                if (devedor != null) {
                    boolean temPagamentos = pagamentoDividaDAO.temPagamentos(usuarioId, devedor.getId());
                    if (temPagamentos) {
                        throw new BarracaService.ConflitoException("Não é possível anular a venda porque o cliente já efectuou pagamentos desta dívida.");
                    }
                    BigDecimal novaDivida = devedor.getDivida().subtract(fiado.getValor());
                    if (novaDivida.compareTo(BigDecimal.ZERO) < 0) novaDivida = BigDecimal.ZERO;
                    devedor.setDivida(novaDivida);
                }
            }

            venda.setEstado("ANULADA");
            venda.setAnuladaEm(util.Fuso.agora());
            venda.setAnuladaPor(usuarioId);
            venda.setMotivoAnulacao(motivo.trim());

            List<ItemVenda> itens = em.createQuery(
                "SELECT i FROM ItemVenda i WHERE i.venda.id = :vid AND i.usuarioId = :uid", ItemVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();

            StockService stockService = new StockService();
            for (ItemVenda iv : itens) {
                stockService.registarMovimento(em, usuarioId, iv.getProduto().getId(), "DEVOLUCAO",
                    iv.getQuantidade(), iv.getCustoUnitario(),
                    "Anulação da venda #" + venda.getNumero() + ": " + motivo.trim(),
                    "VENDA", venda.getId(), usuarioId);
            }

            return venda;
        });
    }

    public List<Venda> listarVendas(Long usuarioId) {
        return vendaDAO.listarOrdenado(usuarioId);
    }

    public Venda buscarVendaPorId(Long id, Long usuarioId) {
        Venda v = vendaDAO.buscarPorIdComDetalhes(id, usuarioId);
        if (v == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Venda não encontrada.");
        }
        return v;
    }

    public List<Venda> vendasDeHoje(Long usuarioId) {
        return vendaDAO.recentes(usuarioId, 25);
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.listarPagina(usuarioId, 1, 100, "", inicio, fim, null, "CONCLUIDA").getItems();
    }

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return vendaDAO.totalVendasHoje(usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.totalVendasPeriodo(inicio, fim, usuarioId);
    }

    public long vendasHoje(Long usuarioId) {
        return vendaDAO.contarVendasHoje(usuarioId);
    }
}
