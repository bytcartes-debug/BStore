package util;

import model.ItemVenda;
import model.PagamentoVenda;
import model.Venda;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VendaJson {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIMESTAMP_ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private VendaJson() {}

    public static Map<String, Object> itemVenda(ItemVenda item) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", item.getId());
        json.put("produto", item.getProduto() != null ? item.getProduto().getNome() : "");
        json.put("produtoId", item.getProduto() != null ? item.getProduto().getId() : null);
        json.put("quantidade", item.getQuantidade().setScale(3).toPlainString());
        json.put("precoUnitario", item.getPrecoUnitario().setScale(2).toPlainString());
        json.put("descontoPercentual", item.getDescontoPercentual() != null ? item.getDescontoPercentual().setScale(2).toPlainString() : "0.00");
        json.put("descontoValor", item.getDescontoValor() != null ? item.getDescontoValor().setScale(2).toPlainString() : "0.00");
        BigDecimal precoFinal = item.getPrecoFinal() != null ? item.getPrecoFinal() : item.getPrecoUnitario();
        json.put("precoFinal", precoFinal != null ? precoFinal.setScale(2).toPlainString() : "0.00");
        json.put("nota", item.getNota() != null ? item.getNota() : "");
        json.put("custoUnitario", item.getCustoUnitario().setScale(2).toPlainString());
        json.put("total", item.getTotal().setScale(2).toPlainString());
        json.put("data", item.getDataVenda() != null ? item.getDataVenda().format(DATA) : "");
        return json;
    }

    public static Map<String, Object> pagamento(PagamentoVenda p) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", p.getId());
        json.put("metodo", p.getMetodo());
        json.put("metodoId", p.getMetodoPagamento() != null ? p.getMetodoPagamento().getId() : null);
        json.put("metodoNome", p.getMetodoPagamento() != null ? p.getMetodoPagamento().getNome() : p.getMetodo());
        json.put("referencia", p.getReferencia() != null ? p.getReferencia() : "");
        json.put("valor", p.getValor().setScale(2).toPlainString());
        json.put("troco", p.getTroco().setScale(2).toPlainString());
        return json;
    }

    public static Map<String, Object> venda(Venda venda) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", venda.getId());
        json.put("numero", venda.getNumero());
        json.put("uuidCliente", venda.getUuidCliente());
        json.put("total", venda.getTotal().setScale(2).toPlainString());
        json.put("totalCusto", venda.getTotalCusto().setScale(2).toPlainString());
        BigDecimal lucro = venda.getTotal().subtract(venda.getTotalCusto());
        json.put("lucro", lucro.setScale(2).toPlainString());
        json.put("estado", venda.getEstado());
        json.put("data", venda.getCriadaEm() != null ? venda.getCriadaEm().format(DATA) : "");
        json.put("hora", venda.getCriadaEm() != null ? venda.getCriadaEm().format(HORA) : "");
        json.put("criadaEm", venda.getCriadaEm() != null ? venda.getCriadaEm().format(TIMESTAMP_ISO) : "");
        json.put("clienteId", venda.getClienteId());
        json.put("observacao", venda.getObservacao() != null ? venda.getObservacao() : "");
        json.put("anuladaEm", venda.getAnuladaEm() != null ? venda.getAnuladaEm().format(TIMESTAMP_ISO) : null);
        json.put("motivoAnulacao", venda.getMotivoAnulacao());
        json.put("sessaoId", venda.getSessaoCaixa() != null ? venda.getSessaoCaixa().getId() : null);

        List<Map<String, Object>> itensJson = new ArrayList<>();
        if (venda.getItens() != null && org.hibernate.Hibernate.isInitialized(venda.getItens())) {
            itensJson = venda.getItens().stream().map(VendaJson::itemVenda).collect(Collectors.toList());
        }
        json.put("itens", itensJson);

        List<Map<String, Object>> pagamentosJson = new ArrayList<>();
        if (venda.getPagamentos() != null && org.hibernate.Hibernate.isInitialized(venda.getPagamentos())) {
            pagamentosJson = venda.getPagamentos().stream().map(VendaJson::pagamento).collect(Collectors.toList());
        }
        json.put("pagamentos", pagamentosJson);

        // Campos de compatibilidade com app e testes legados
        if (!itensJson.isEmpty()) {
            json.put("produto", itensJson.get(0).get("produto"));
            json.put("produtoId", itensJson.get(0).get("produtoId"));
            json.put("quantidade", itensJson.get(0).get("quantidade"));
            json.put("precoUnitario", itensJson.get(0).get("precoUnitario"));
        } else {
            json.put("produto", "Venda #" + venda.getNumero());
        }

        BigDecimal troco = BigDecimal.ZERO;
        if (venda.getPagamentos() != null && org.hibernate.Hibernate.isInitialized(venda.getPagamentos())) {
            for (PagamentoVenda pv : venda.getPagamentos()) {
                if (pv.getTroco() != null && pv.getTroco().compareTo(BigDecimal.ZERO) > 0) {
                    troco = pv.getTroco();
                    break;
                }
            }
        }
        json.put("troco", troco.setScale(2).toPlainString());

        return json;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lote(Map<String, Object> resultado) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", resultado.get("id"));
        json.put("numero", resultado.get("numero"));
        json.put("itens", resultado.get("itens"));
        json.put("total", resultado.get("total") instanceof BigDecimal
            ? ((BigDecimal) resultado.get("total")).setScale(2).toPlainString()
            : resultado.get("total"));
        json.put("totalCusto", resultado.get("totalCusto") instanceof BigDecimal
            ? ((BigDecimal) resultado.get("totalCusto")).setScale(2).toPlainString()
            : resultado.get("totalCusto"));
        json.put("troco", resultado.get("troco") instanceof BigDecimal
            ? ((BigDecimal) resultado.get("troco")).setScale(2).toPlainString()
            : resultado.get("troco"));
        json.put("estado", resultado.get("estado"));

        if (resultado.get("venda") instanceof Venda) {
            json.put("venda", venda((Venda) resultado.get("venda")));
        }
        if (resultado.containsKey("vendas")) {
            json.put("vendas", resultado.get("vendas"));
        }
        return json;
    }
}
