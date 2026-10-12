package service;

import dao.DevedorDAO;
import dao.ProdutoDAO;
import dao.SessaoCaixaDAO;
import dao.VendaDAO;
import model.Devedor;
import model.ItemVenda;
import model.PagamentoVenda;
import model.Produto;
import model.SessaoCaixa;
import model.Venda;
import util.CsvUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelatorioService {

    private final VendaDAO vendaDAO = new VendaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final DevedorDAO devedorDAO = new DevedorDAO();
    private final SessaoCaixaDAO sessaoCaixaDAO = new SessaoCaixaDAO();
    private final CaixaService caixaService = new CaixaService();

    public byte[] exportarVendasCsv(LocalDate inicio, LocalDate fim, String metodo, String estado, String q, Long usuarioId) {
        List<Venda> vendas = vendaDAO.listarParaExportacao(usuarioId, inicio, fim, metodo, estado, q);
        Map<Long, String> clientesMap = new HashMap<>();
        try {
            for (Devedor d : devedorDAO.listarTodos(usuarioId)) {
                clientesMap.put(d.getId(), d.getNome());
            }
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.UTF8_BOM);
        sb.append("Número;Data / Hora;Estado;Total (MZN);Custo Total (MZN);Lucro Estimado (MZN);Métodos de Pagamento;Cliente;Observação;Itens Vendidos\r\n");

        for (Venda v : vendas) {
            String numero = v.getNumero() != null ? String.valueOf(v.getNumero()) : String.valueOf(v.getId());
            String dataHora = CsvUtil.formatarDataHora(v.getCriadaEm());
            String est = v.getEstado() != null ? v.getEstado() : "CONCLUIDA";
            String total = CsvUtil.formatarMoeda(v.getTotal());
            String custo = CsvUtil.formatarMoeda(v.getTotalCusto());
            BigDecimal lucroCalc = "CONCLUIDA".equalsIgnoreCase(est)
                ? (v.getTotal() != null && v.getTotalCusto() != null ? v.getTotal().subtract(v.getTotalCusto()) : BigDecimal.ZERO)
                : BigDecimal.ZERO;
            String lucro = CsvUtil.formatarMoeda(lucroCalc);

            StringBuilder pgs = new StringBuilder();
            if (v.getPagamentos() != null) {
                for (PagamentoVenda pv : v.getPagamentos()) {
                    if (pgs.length() > 0) pgs.append(" | ");
                    String mNome = pv.getMetodoPagamento() != null ? pv.getMetodoPagamento().getNome() : pv.getMetodo();
                    pgs.append(mNome).append(": ").append(CsvUtil.formatarMoeda(pv.getValor())).append(" MZN");
                }
            }

            String cliente = "Consumidor Final";
            if (v.getClienteId() != null && clientesMap.containsKey(v.getClienteId())) {
                cliente = clientesMap.get(v.getClienteId());
            }

            String obs = v.getObservacao() != null ? v.getObservacao() : "";

            StringBuilder its = new StringBuilder();
            if (v.getItens() != null) {
                for (ItemVenda iv : v.getItens()) {
                    if (its.length() > 0) its.append(" | ");
                    String pNome = iv.getProduto() != null ? iv.getProduto().getNome() : "Item";
                    its.append(CsvUtil.formatarQuantidade(iv.getQuantidade()))
                       .append("x ")
                       .append(pNome)
                       .append(" (")
                       .append(CsvUtil.formatarMoeda(iv.getTotal()))
                       .append(" MZN)");
                }
            }

            sb.append(CsvUtil.escape(numero)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(dataHora)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(est)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(total)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(custo)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(lucro)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(pgs.toString())).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(cliente)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(obs)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(its.toString())).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportarStockCsv(Long usuarioId) {
        List<Produto> produtos = produtoDAO.listarOrdenado(usuarioId, true);

        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.UTF8_BOM);
        sb.append("ID;Código de Barras;Nome do Produto;Categoria;Unidade;Preço de Venda (MZN);Preço de Custo (MZN);Stock Atual;Stock Mínimo;Stock Máximo;Valor Total a Custo (MZN);Valor Total a Venda (MZN);Margem Unitária (MZN);Margem Unitária (%);Estado de Reposição;Ativo\r\n");

        for (Produto p : produtos) {
            String id = String.valueOf(p.getId());
            String codigoBarras = p.getCodigoBarras() != null ? p.getCodigoBarras() : "";
            String nome = p.getNome() != null ? p.getNome() : "";
            String categoria = p.getCategoria() != null ? p.getCategoria().getNome() : "Geral";
            String unidade = p.getUnidade() != null ? p.getUnidade() : "un";
            BigDecimal preco = p.getPreco() != null ? p.getPreco() : BigDecimal.ZERO;
            BigDecimal custo = p.getCusto() != null ? p.getCusto() : BigDecimal.ZERO;
            BigDecimal stock = p.getQuantidadeStock() != null ? p.getQuantidadeStock() : BigDecimal.ZERO;
            BigDecimal min = p.getStockMinimo() != null ? p.getStockMinimo() : BigDecimal.ZERO;
            BigDecimal max = p.getStockMaximo();

            BigDecimal totalCusto = stock.multiply(custo);
            BigDecimal totalVenda = stock.multiply(preco);
            BigDecimal margemMzn = preco.subtract(custo);
            BigDecimal margemPct = preco.compareTo(BigDecimal.ZERO) > 0
                ? margemMzn.multiply(new BigDecimal("100")).divide(preco, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            String reposicao;
            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                reposicao = "Esgotado";
            } else if (stock.compareTo(min) <= 0) {
                reposicao = "Repor Urgente";
            } else if (max != null && stock.compareTo(max) > 0) {
                reposicao = "Excesso de Stock";
            } else {
                reposicao = "Normal";
            }

            String ativo = Boolean.TRUE.equals(p.getAtivo()) ? "Sim" : "Arquivado";

            sb.append(CsvUtil.escape(id)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(codigoBarras)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(nome)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(categoria)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(unidade)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarMoeda(preco))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarMoeda(custo))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarQuantidade(stock))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarQuantidade(min))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(max != null ? CsvUtil.formatarQuantidade(max) : "")).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarMoeda(totalCusto))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarMoeda(totalVenda))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarMoeda(margemMzn))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(CsvUtil.formatarPercentual(margemPct))).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(reposicao)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(ativo)).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportarCaixaCsv(Long usuarioId, int limite) {
        return exportarCaixaCsv(usuarioId, null, null, limite);
    }

    public byte[] exportarCaixaCsv(Long usuarioId, LocalDate inicio, LocalDate fim, int limite) {
        List<SessaoCaixa> sessoes = sessaoCaixaDAO.listarHistorico(usuarioId, inicio, fim, limite > 0 ? limite : 200);

        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.UTF8_BOM);
        sb.append("ID Sessão;Estado;Data Abertura;Data Fecho;Fundo Inicial (MZN);Valor Esperado (MZN);Valor Contado (MZN);Diferença (MZN);Nota Abertura;Nota Fecho\r\n");

        for (SessaoCaixa s : sessoes) {
            String id = String.valueOf(s.getId());
            String estado = s.getEstado() != null ? s.getEstado() : "FECHADA";
            String abertaEm = CsvUtil.formatarDataHora(s.getAbertaEm());
            String fechadaEm = s.getFechadaEm() != null ? CsvUtil.formatarDataHora(s.getFechadaEm()) : "Em aberto";
            String inicial = CsvUtil.formatarMoeda(s.getValorInicial());
            String esperado = s.getValorEsperado() != null ? CsvUtil.formatarMoeda(s.getValorEsperado()) : "";
            String contado = s.getValorContado() != null ? CsvUtil.formatarMoeda(s.getValorContado()) : "";
            String diferenca = s.getDiferenca() != null ? CsvUtil.formatarMoeda(s.getDiferenca()) : "";
            String notaAb = s.getNotaAbertura() != null ? s.getNotaAbertura() : "";
            String notaFe = s.getNotaFecho() != null ? s.getNotaFecho() : "";

            sb.append(CsvUtil.escape(id)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(estado)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(abertaEm)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(fechadaEm)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(inicial)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(esperado)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(contado)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(diferenca)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(notaAb)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(notaFe)).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportarDespesasCaixaCsv(Long sessaoId, Long usuarioId) {
        List<Map<String, Object>> despesas = caixaService.listarDespesasCaixa(sessaoId, usuarioId);

        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.UTF8_BOM);
        sb.append("ID;ID Sessão;Data / Hora;Categoria;Descrição;Valor (MZN)\r\n");

        for (Map<String, Object> d : despesas) {
            String id = String.valueOf(d.get("id"));
            String sId = d.get("sessaoId") != null ? String.valueOf(d.get("sessaoId")) : "-";
            String dataHora = d.get("criadaEm") != null ? String.valueOf(d.get("criadaEm")).replace('T', ' ').substring(0, Math.min(19, String.valueOf(d.get("criadaEm")).length())) : "";
            String cat = d.get("categoria") != null ? String.valueOf(d.get("categoria")) : "OUTRO";
            String desc = d.get("descricao") != null ? String.valueOf(d.get("descricao")) : "";
            String val = "0,00";
            if (d.get("valor") != null) {
                try {
                    val = CsvUtil.formatarMoeda(new BigDecimal(d.get("valor").toString()));
                } catch (Exception ignored) {}
            }

            sb.append(CsvUtil.escape(id)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(sId)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(dataHora)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(cat)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(desc)).append(CsvUtil.DELIMITER)
              .append(CsvUtil.escape(val)).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
