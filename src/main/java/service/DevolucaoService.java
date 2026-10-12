package service;

import dao.DevedorDAO;
import dao.DevolucaoDAO;
import dao.VendaDAO;
import model.Devedor;
import model.Devolucao;
import model.ItemDevolucao;
import model.ItemVenda;
import model.PagamentoVenda;
import model.Venda;
import util.JPAUtil;

import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static service.ServiceValidation.*;

public class DevolucaoService {

    private final DevolucaoDAO devolucaoDAO = new DevolucaoDAO();
    private final VendaDAO vendaDAO = new VendaDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();

    public Map<String, Object> processarDevolucao(Long vendaId, List<BarracaService.DevolucaoItemParam> itensParam, String motivo, String uuidCliente, Long operadorId, Long usuarioId) {
        if (itensParam == null || itensParam.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item informado para devolução.");
        }
        String motivoNorm = (motivo != null && !motivo.trim().isEmpty()) ? motivo.trim() : "Devolução de produtos";

        return JPAUtil.emTransacao(usuarioId, em -> {
            if (uuidCliente != null && !uuidCliente.trim().isEmpty()) {
                Devolucao existente = devolucaoDAO.buscarPorUuidCliente(em, uuidCliente.trim(), usuarioId);
                if (existente != null) {
                    return formatarDevolucao(existente);
                }
            }

            Venda venda = em.find(Venda.class, vendaId, LockModeType.PESSIMISTIC_WRITE);
            if (venda == null || !usuarioId.equals(venda.getUsuarioId())) {
                throw new BarracaService.RecursoNaoEncontradoException("Venda não encontrada.");
            }
            if ("ANULADA".equals(venda.getEstado())) {
                throw new IllegalStateException("Esta venda está anulada e não pode receber devoluções.");
            }

            List<ItemVenda> itensVenda = em.createQuery(
                "SELECT iv FROM ItemVenda iv WHERE iv.venda.id = :vid AND iv.usuarioId = :uid", ItemVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultList();

            Map<Long, ItemVenda> itensMap = new HashMap<>();
            BigDecimal totalQtdVendida = BigDecimal.ZERO;
            for (ItemVenda iv : itensVenda) {
                itensMap.put(iv.getId(), iv);
                totalQtdVendida = totalQtdVendida.add(iv.getQuantidade());
            }

            BigDecimal totalQtdAnteriorDevolvida = BigDecimal.ZERO;
            for (ItemVenda iv : itensVenda) {
                BigDecimal devAnterior = devolucaoDAO.obterTotalDevolvidoPorItem(em, iv.getId());
                totalQtdAnteriorDevolvida = totalQtdAnteriorDevolvida.add(devAnterior);
            }

            BigDecimal totalDevolver = BigDecimal.ZERO;
            BigDecimal totalQtdDevolvidaAgora = BigDecimal.ZERO;
            List<ItemDevolucao> itensDevolucao = new ArrayList<>();
            StockService stockService = new StockService();

            for (BarracaService.DevolucaoItemParam param : itensParam) {
                if (param == null || param.itemVendaId == null) continue;
                ItemVenda iv = itensMap.get(param.itemVendaId);
                if (iv == null) {
                    throw new BarracaService.RecursoNaoEncontradoException("Item de venda " + param.itemVendaId + " não encontrado na venda #" + venda.getNumero() + ".");
                }
                BigDecimal qtdDevolver = quantidade(param.quantidade);
                if (qtdDevolver.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("A quantidade a devolver deve ser maior que zero.");
                }
                BigDecimal jaDevolvido = devolucaoDAO.obterTotalDevolvidoPorItem(em, iv.getId());
                BigDecimal disponivel = iv.getQuantidade().subtract(jaDevolvido);
                if (qtdDevolver.compareTo(disponivel) > 0) {
                    throw new IllegalArgumentException("Não é possível devolver mais do que o vendido. Disponível: " + disponivel.toPlainString());
                }

                BigDecimal precoUnit = iv.getPrecoFinal() != null ? iv.getPrecoFinal() : iv.getPrecoUnitario();
                BigDecimal valorItem = qtdDevolver.multiply(precoUnit).setScale(2, RoundingMode.HALF_UP);
                totalDevolver = totalDevolver.add(valorItem);
                totalQtdDevolvidaAgora = totalQtdDevolvidaAgora.add(qtdDevolver);

                stockService.registarMovimento(em, usuarioId, iv.getProduto().getId(), "DEVOLUCAO",
                    qtdDevolver, iv.getCustoUnitario(),
                    "Devolução da venda #" + venda.getNumero() + ": " + motivoNorm,
                    "DEVOLUCAO", venda.getId(), operadorId);

                ItemDevolucao idv = new ItemDevolucao(null, iv, qtdDevolver, valorItem);
                itensDevolucao.add(idv);
            }

            if (itensDevolucao.isEmpty()) {
                throw new IllegalArgumentException("Nenhum item válido para devolução.");
            }

            PagamentoVenda fiado = em.createQuery(
                "SELECT p FROM PagamentoVenda p WHERE p.venda.id = :vid AND p.usuarioId = :uid AND UPPER(p.metodo) = 'FIADO'", PagamentoVenda.class)
                .setParameter("vid", vendaId)
                .setParameter("uid", usuarioId)
                .getResultStream().findFirst().orElse(null);

            if (fiado != null && venda.getClienteId() != null) {
                Devedor devedor = devedorDAO.buscarPorIdParaBloqueio(em, venda.getClienteId(), usuarioId);
                if (devedor != null) {
                    BigDecimal abatimento = totalDevolver.min(devedor.getDivida());
                    devedor.setDivida(devedor.getDivida().subtract(abatimento));
                }
            }

            BigDecimal totalQtdFinalDevolvida = totalQtdAnteriorDevolvida.add(totalQtdDevolvidaAgora);
            if (totalQtdFinalDevolvida.compareTo(totalQtdVendida) >= 0) {
                venda.setEstado("DEVOLVIDA");
            } else {
                venda.setEstado("PARCIALMENTE_DEVOLVIDA");
            }
            em.merge(venda);

            Devolucao devolucao = new Devolucao(usuarioId, venda, totalDevolver, motivoNorm, operadorId, uuidCliente);
            em.persist(devolucao);
            for (ItemDevolucao itemDev : itensDevolucao) {
                itemDev.setDevolucao(devolucao);
                em.persist(itemDev);
                devolucao.addItem(itemDev);
            }
            em.flush();

            return formatarDevolucao(devolucao);
        });
    }

    public List<Map<String, Object>> listarDevolucoesVenda(Long vendaId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            Venda venda = vendaDAO.buscarPorId(em, vendaId, usuarioId);
            if (venda == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Venda não encontrada.");
            }
            List<Devolucao> devs = devolucaoDAO.listarPorVenda(em, vendaId, usuarioId);
            List<Map<String, Object>> list = new ArrayList<>();
            for (Devolucao d : devs) {
                list.add(formatarDevolucao(d));
            }
            return list;
        });
    }

    public Map<String, Object> formatarDevolucao(Devolucao dev) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", dev.getId());
        res.put("vendaId", dev.getVenda().getId());
        res.put("vendaNumero", dev.getVenda().getNumero());
        res.put("total", decimalJson(dev.getTotal(), 2));
        res.put("motivo", dev.getMotivo());
        res.put("criadaEm", dev.getCriadaEm() != null ? dev.getCriadaEm().toString() : "");
        res.put("estadoVenda", dev.getVenda().getEstado());
        List<Map<String, Object>> itens = new ArrayList<>();
        if (dev.getItens() != null) {
            for (ItemDevolucao idv : dev.getItens()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", idv.getId());
                m.put("itemVendaId", idv.getItemVenda().getId());
                m.put("produtoNome", idv.getItemVenda().getProduto().getNome());
                m.put("quantidade", decimalJson(idv.getQuantidade(), 3));
                m.put("valor", decimalJson(idv.getValor(), 2));
                itens.add(m);
            }
        }
        res.put("itens", itens);
        return res;
    }
}
