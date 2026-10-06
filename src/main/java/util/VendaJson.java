package util;

import model.Venda;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VendaJson {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private VendaJson() {}

    public static Map<String, Object> venda(Venda venda) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", venda.getId());
        json.put("produto", venda.getProduto().getNome());
        json.put("produtoId", venda.getProduto().getId());
        json.put("quantidade", venda.getQuantidade().setScale(3).toPlainString());
        json.put("precoUnitario", venda.getPrecoUnitario().setScale(2).toPlainString());
        json.put("total", venda.getTotal().setScale(2).toPlainString());
        json.put("data", venda.getDataVenda().format(DATA));
        return json;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lote(Map<String, Object> resultado) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("itens", resultado.get("itens"));
        json.put("total", ((BigDecimal) resultado.get("total")).setScale(2).toPlainString());
        json.put("vendas", ((List<Venda>) resultado.get("vendas")).stream().map(VendaJson::venda).collect(Collectors.toList()));
        return json;
    }
}
