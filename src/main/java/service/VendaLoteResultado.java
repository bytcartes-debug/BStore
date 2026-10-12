package service;

import model.ItemVenda;
import model.MetodoPagamento;
import model.PagamentoVenda;
import model.Produto;
import model.Venda;
import util.VendaJson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static service.ServiceValidation.moeda;

public class VendaLoteResultado {

    public static Map<String, Object> montarResultadoExistente(Venda existente) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", existente.getId());
        resultado.put("numero", existente.getNumero());
        resultado.put("uuidCliente", existente.getUuidCliente());
        resultado.put("itens", existente.getItens() != null ? existente.getItens().size() : 0);
        resultado.put("total", moeda(existente.getTotal()));
        resultado.put("totalCusto", moeda(existente.getTotalCusto()));
        BigDecimal trocoExistente = BigDecimal.ZERO;
        if (existente.getPagamentos() != null) {
            for (PagamentoVenda pv : existente.getPagamentos()) {
                if (pv.getTroco() != null && pv.getTroco().compareTo(BigDecimal.ZERO) > 0) {
                    trocoExistente = pv.getTroco();
                    break;
                }
            }
        }
        resultado.put("troco", moeda(trocoExistente));
        resultado.put("estado", existente.getEstado());
        resultado.put("venda", existente);

        List<Map<String, Object>> itensLegados = new ArrayList<>();
        if (existente.getItens() != null) {
            for (ItemVenda iv : existente.getItens()) {
                itensLegados.add(VendaJson.itemVenda(iv));
            }
        }
        resultado.put("vendas", itensLegados);
        return resultado;
    }

    public static Map<String, Object> montarResultadoSucesso(Venda venda, BigDecimal total, BigDecimal totalCusto, BigDecimal troco, List<ItemVenda> itensCriados) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("id", venda.getId());
        resultado.put("numero", venda.getNumero());
        resultado.put("uuidCliente", venda.getUuidCliente());
        resultado.put("itens", itensCriados.size());
        resultado.put("total", moeda(total));
        resultado.put("totalCusto", moeda(totalCusto));
        resultado.put("troco", moeda(troco));
        resultado.put("estado", venda.getEstado());
        resultado.put("venda", venda);

        List<Map<String, Object>> itensLegados = new ArrayList<>();
        for (ItemVenda iv : itensCriados) {
            itensLegados.add(VendaJson.itemVenda(iv));
        }
        resultado.put("vendas", itensLegados);
        return resultado;
    }

    public static String converterMetodoCodigo(String nome, String tipo) {
        if ("Dinheiro".equalsIgnoreCase(nome) || "DINHEIRO".equalsIgnoreCase(nome)) return "DINHEIRO";
        if ("M-Pesa".equalsIgnoreCase(nome) || "MPESA".equalsIgnoreCase(nome)) return "MPESA";
        if ("e-Mola".equalsIgnoreCase(nome) || "EMOLA".equalsIgnoreCase(nome)) return "EMOLA";
        if ("mKesh".equalsIgnoreCase(nome) || "MKESH".equalsIgnoreCase(nome)) return "MKESH";
        if ("Cartão".equalsIgnoreCase(nome) || "Cartao".equalsIgnoreCase(nome) || "CARTAO".equalsIgnoreCase(nome)) return "CARTAO";
        if ("A fiado".equalsIgnoreCase(nome) || "FIADO".equalsIgnoreCase(nome)) return "FIADO";
        return tipo != null ? tipo.toUpperCase() : "DIGITAL";
    }

    public static class LinhaCalculada {
        public Produto produto;
        public BigDecimal quantidade;
        public BigDecimal precoUnitario;
        public BigDecimal descontoPercentual;
        public BigDecimal descontoValor;
        public BigDecimal precoFinal;
        public BigDecimal subtotal;
        public BigDecimal subtotalCusto;
        public String nota;
    }

    public static class PagamentoCalc {
        public MetodoPagamento metodoPagamento;
        public String metodoNome;
        public String metodoTipo;
        public BigDecimal valor;
        public String referencia;
    }

    public static class PrePagamento {
        public Map<String, Object> raw;
        public MetodoPagamento mp;
        public String metodoNome;
        public String metodoTipo;
        public boolean isFiado;
    }
}
