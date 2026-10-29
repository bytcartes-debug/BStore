package service;

import model.Categoria;
import model.DefinicaoLoja;
import model.MetodoPagamento;
import model.Produto;
import model.SessaoCaixa;
import model.Venda;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class BarracaService {

    private final CategoriaService categoriaService = new CategoriaService();
    private final ProdutoService produtoService = new ProdutoService();
    private final VendaService vendaService = new VendaService();
    private final DevolucaoService devolucaoService = new DevolucaoService();
    private final CaixaService caixaService = new CaixaService();
    private final DevedorService devedorService = new DevedorService();
    private final MetodoPagamentoService metodoPagamentoService = new MetodoPagamentoService();
    private final DashboardService dashboardService = new DashboardService();
    private final RelatorioService relatorioService = new RelatorioService();
    private final ConfiguracaoService configuracaoService = new ConfiguracaoService();

    // ==========================================
    // Categoria
    // ==========================================

    public Categoria criarCategoria(String nome, String descricao, Long usuarioId) {
        return categoriaService.criarCategoria(nome, descricao, usuarioId);
    }

    public Categoria actualizarCategoria(Categoria categoria, Long usuarioId) {
        return categoriaService.actualizarCategoria(categoria, usuarioId);
    }

    public List<Produto> listarProdutosPorCategoria(Long categoriaId, Long usuarioId) {
        return categoriaService.listarProdutosPorCategoria(categoriaId, usuarioId);
    }

    public void eliminarCategoria(Long id, Long usuarioId) {
        categoriaService.eliminarCategoria(id, usuarioId);
    }

    public List<Categoria> listarCategorias(Long usuarioId) {
        return categoriaService.listarCategorias(usuarioId);
    }

    public Categoria buscarCategoria(Long id, Long usuarioId) {
        return categoriaService.buscarCategoria(id, usuarioId);
    }

    // ==========================================
    // Produto
    // ==========================================

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return produtoService.criarProduto(nome, preco, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return produtoService.criarProduto(nome, preco, custo, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, BigDecimal stockMaximo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return produtoService.criarProduto(nome, preco, custo, stock, unidade, stockMinimo, stockMaximo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return produtoService.actualizarProduto(id, nome, preco, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return produtoService.actualizarProduto(id, nome, preco, custo, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, BigDecimal stockMaximo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return produtoService.actualizarProduto(id, nome, preco, custo, stock, unidade, stockMinimo, stockMaximo, categoriaId, codigoBarras, usuarioId);
    }

    public void eliminarProduto(Long id, Long usuarioId) {
        produtoService.eliminarProduto(id, usuarioId);
    }

    public Produto buscarPorCodigoBarras(String codigo, Long usuarioId) {
        return produtoService.buscarPorCodigoBarras(codigo, usuarioId);
    }

    public List<Produto> listarProdutos(Long usuarioId) {
        return produtoService.listarProdutos(usuarioId);
    }

    public List<Produto> listarProdutos(Long usuarioId, boolean incluirArquivados) {
        return produtoService.listarProdutos(usuarioId, incluirArquivados);
    }

    public List<Produto> buscarProdutosPorNome(String nome, Long usuarioId) {
        return produtoService.buscarProdutosPorNome(nome, usuarioId);
    }

    public List<Produto> produtosComStockBaixo(Long usuarioId) {
        return produtoService.produtosComStockBaixo(usuarioId);
    }

    public long totalProdutos(Long usuarioId) {
        return produtoService.totalProdutos(usuarioId);
    }

    public List<Map<String, Object>> obterProdutosFaltaRepor(Long usuarioId) {
        return produtoService.obterProdutosFaltaRepor(usuarioId);
    }

    // ==========================================
    // Venda
    // ==========================================

    public Venda registarVenda(Long produtoId, BigDecimal quantidade, String observacao, Long usuarioId) {
        return vendaService.registarVenda(produtoId, quantidade, observacao, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens, Long usuarioId) {
        return vendaService.registarVendaLote(itens, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        return vendaService.registarVendaLote(itens, pagamentos, clienteId, observacao, usuarioId);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens,
                                                List<Map<String, Object>> pagamentos,
                                                Long clienteId,
                                                String observacao,
                                                String uuidCliente,
                                                Long usuarioId) {
        return vendaService.registarVendaLote(itens, pagamentos, clienteId, observacao, uuidCliente, usuarioId);
    }

    public static Map<Long, BigDecimal> normalizarCarrinho(List<Map<String, Object>> itens) {
        return VendaService.normalizarCarrinho(itens);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                Map<Long, BigDecimal> quantidades,
                                                Long usuarioId) {
        return vendaService.executarVendaLote(em, quantidades, usuarioId);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                Map<Long, BigDecimal> quantidades,
                                                List<Map<String, Object>> pagamentosRaw,
                                                Long clienteId,
                                                String observacao,
                                                Long usuarioId) {
        return vendaService.executarVendaLote(em, quantidades, pagamentosRaw, clienteId, observacao, usuarioId);
    }

    public Map<String, Object> executarVendaLote(EntityManager em,
                                                List<Map<String, Object>> itensRaw,
                                                List<Map<String, Object>> pagamentosRaw,
                                                Long clienteId,
                                                String observacao,
                                                String uuidCliente,
                                                Long usuarioId) {
        return vendaService.executarVendaLote(em, itensRaw, pagamentosRaw, clienteId, observacao, uuidCliente, usuarioId);
    }

    public Venda anularVenda(Long vendaId, String motivo, Long usuarioId) {
        return vendaService.anularVenda(vendaId, motivo, usuarioId);
    }

    public List<Venda> listarVendas(Long usuarioId) {
        return vendaService.listarVendas(usuarioId);
    }

    public Venda buscarVendaPorId(Long id, Long usuarioId) {
        return vendaService.buscarVendaPorId(id, usuarioId);
    }

    public List<Venda> vendasDeHoje(Long usuarioId) {
        return vendaService.vendasDeHoje(usuarioId);
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaService.vendasEntreDatas(inicio, fim, usuarioId);
    }

    public BigDecimal totalVendasHoje(Long usuarioId) {
        return vendaService.totalVendasHoje(usuarioId);
    }

    public BigDecimal totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaService.totalVendasPeriodo(inicio, fim, usuarioId);
    }

    public long vendasHoje(Long usuarioId) {
        return vendaService.vendasHoje(usuarioId);
    }

    // ==========================================
    // Dashboard & Métricas
    // ==========================================

    public BigDecimal calcularPerdasMesACusto(Long usuarioId) {
        return dashboardService.calcularPerdasMesACusto(usuarioId);
    }

    public long totalCategorias(Long usuarioId) {
        return dashboardService.totalCategorias(usuarioId);
    }

    public long totalDevedores(Long usuarioId) {
        return dashboardService.totalDevedores(usuarioId);
    }

    public Map<LocalDate, BigDecimal> totaisPorDia(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return dashboardService.totaisPorDia(inicio, fim, usuarioId);
    }

    public List<Venda> vendasRecentes(Long usuarioId, int limite) {
        return dashboardService.vendasRecentes(usuarioId, limite);
    }

    // ==========================================
    // Devolução
    // ==========================================

    public Map<String, Object> processarDevolucao(Long vendaId, List<DevolucaoItemParam> itensParam, String motivo, String uuidCliente, Long operadorId, Long usuarioId) {
        return devolucaoService.processarDevolucao(vendaId, itensParam, motivo, uuidCliente, operadorId, usuarioId);
    }

    public List<Map<String, Object>> listarDevolucoesVenda(Long vendaId, Long usuarioId) {
        return devolucaoService.listarDevolucoesVenda(vendaId, usuarioId);
    }

    // ==========================================
    // Caixa & Despesas
    // ==========================================

    public SessaoCaixa abrirSessaoCaixa(BigDecimal valorInicial, String notaAbertura, Long operadorId, Long usuarioId) {
        return caixaService.abrirSessaoCaixa(valorInicial, notaAbertura, operadorId, usuarioId);
    }

    public SessaoCaixa buscarSessaoAberta(Long usuarioId) {
        return caixaService.buscarSessaoAberta(usuarioId);
    }

    public Map<String, Object> obterResumoSessaoAtual(Long usuarioId) {
        return caixaService.obterResumoSessaoAtual(usuarioId);
    }

    public SessaoCaixa fecharSessaoCaixa(Long sessaoId, BigDecimal valorContado, String notaFecho, Long operadorId, Long usuarioId) {
        return caixaService.fecharSessaoCaixa(sessaoId, valorContado, notaFecho, operadorId, usuarioId);
    }

    public Map<String, Object> registarDespesaCaixa(BigDecimal valor, String categoria, String descricao, Long operadorId, Long usuarioId) {
        return caixaService.registarDespesaCaixa(valor, categoria, descricao, operadorId, usuarioId);
    }

    public List<Map<String, Object>> listarDespesasCaixa(Long sessaoId, Long usuarioId) {
        return caixaService.listarDespesasCaixa(sessaoId, usuarioId);
    }

    public List<SessaoCaixa> listarHistoricoSessoes(Long usuarioId, int limite) {
        return caixaService.listarHistoricoSessoes(usuarioId, limite);
    }

    public List<SessaoCaixa> listarHistoricoSessoes(Long usuarioId, LocalDate inicio, LocalDate fim, int limite) {
        return caixaService.listarHistoricoSessoes(usuarioId, inicio, fim, limite);
    }

    public Map<String, Object> fechoCaixa(LocalDate data, Long usuarioId) {
        return caixaService.fechoCaixa(data, usuarioId);
    }

    // ==========================================
    // Devedor
    // ==========================================

    public Map<String, Object> registarPagamentoDivida(Long devedorId, BigDecimal valor, String metodo, String observacao, Long usuarioId) {
        return devedorService.registarPagamentoDivida(devedorId, valor, metodo, observacao, usuarioId);
    }

    // ==========================================
    // Métodos de Pagamento
    // ==========================================

    public List<MetodoPagamento> listarMetodosPagamento(Long usuarioId) {
        return metodoPagamentoService.listarMetodosPagamento(usuarioId);
    }

    public List<MetodoPagamento> listarMetodosPagamentoAtivos(Long usuarioId) {
        return metodoPagamentoService.listarMetodosPagamentoAtivos(usuarioId);
    }

    public MetodoPagamento criarMetodoPagamento(String nome, String tipo, Long usuarioId) {
        return metodoPagamentoService.criarMetodoPagamento(nome, tipo, usuarioId);
    }

    public MetodoPagamento alternarMetodoPagamento(Long id, Long usuarioId) {
        return metodoPagamentoService.alternarMetodoPagamento(id, usuarioId);
    }

    public MetodoPagamento actualizarMetodoPagamento(Long id, String nome, Boolean ativo, Integer ordem, Long usuarioId) {
        return metodoPagamentoService.actualizarMetodoPagamento(id, nome, ativo, ordem, usuarioId);
    }

    public void eliminarMetodoPagamento(Long id, Long usuarioId) {
        metodoPagamentoService.eliminarMetodoPagamento(id, usuarioId);
    }

    // ==========================================
    // Definições de Loja
    // ==========================================

    public DefinicaoLoja obterDefinicoesLoja(Long usuarioId) {
        return configuracaoService.obterDefinicoesLoja(usuarioId);
    }

    public DefinicaoLoja atualizarDefinicoesLoja(boolean controloCaixa, String nomeLoja, Long usuarioId) {
        return configuracaoService.atualizarDefinicoesLoja(controloCaixa, nomeLoja, usuarioId);
    }

    public DefinicaoLoja atualizarDefinicoesLoja(Boolean controloCaixa, String nomeLoja,
                                                Boolean whatsappActivo, String whatsappTelefone,
                                                Long usuarioId) {
        return configuracaoService.atualizarDefinicoesLoja(controloCaixa, nomeLoja, whatsappActivo, whatsappTelefone, usuarioId);
    }

    @Deprecated
    public DefinicaoLoja atualizarDefinicoesLoja(Boolean controloCaixa, String nomeLoja,
                                                Boolean whatsappActivo, String whatsappTelefone,
                                                String zapiInstanceId, String zapiToken, String zapiClientToken,
                                                Long usuarioId) {
        return configuracaoService.atualizarDefinicoesLoja(controloCaixa, nomeLoja, whatsappActivo, whatsappTelefone, zapiInstanceId, zapiToken, zapiClientToken, usuarioId);
    }

    // ==========================================
    // Relatórios / Exportações CSV
    // ==========================================

    public byte[] exportarVendasCsv(LocalDate inicio, LocalDate fim, String metodo, String estado, String q, Long usuarioId) {
        return relatorioService.exportarVendasCsv(inicio, fim, metodo, estado, q, usuarioId);
    }

    public byte[] exportarStockCsv(Long usuarioId) {
        return relatorioService.exportarStockCsv(usuarioId);
    }

    public byte[] exportarCaixaCsv(Long usuarioId, int limite) {
        return relatorioService.exportarCaixaCsv(usuarioId, limite);
    }

    public byte[] exportarCaixaCsv(Long usuarioId, LocalDate inicio, LocalDate fim, int limite) {
        return relatorioService.exportarCaixaCsv(usuarioId, inicio, fim, limite);
    }

    public byte[] exportarDespesasCaixaCsv(Long sessaoId, Long usuarioId) {
        return relatorioService.exportarDespesasCaixaCsv(sessaoId, usuarioId);
    }

    // ==========================================
    // Métodos estáticos e utilitários
    // ==========================================

    public static BigDecimal moeda(BigDecimal valor) {
        return ServiceValidation.moeda(valor);
    }

    public static BigDecimal quantidade(BigDecimal valor) {
        return ServiceValidation.quantidade(valor);
    }

    public static BigDecimal decimal(Object value, String campo) {
        return ServiceValidation.decimal(value, campo);
    }

    public static class DevolucaoItemParam {
        public Long itemVendaId;
        public BigDecimal quantidade;

        public DevolucaoItemParam() {}
        public DevolucaoItemParam(Long itemVendaId, BigDecimal quantidade) {
            this.itemVendaId = itemVendaId;
            this.quantidade = quantidade;
        }
    }

    public static final class RecursoNaoEncontradoException extends RuntimeException {
        public RecursoNaoEncontradoException(String message) { super(message); }
    }

    public static class ConflitoException extends RuntimeException {
        public ConflitoException(String message) { super(message); }
    }
}
