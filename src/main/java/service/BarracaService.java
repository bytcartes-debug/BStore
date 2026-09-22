package service;

import dao.CategoriaDAO;
import dao.ProdutoDAO;
import dao.VendaDAO;
import model.Categoria;
import model.Produto;
import model.Venda;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class BarracaService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProdutoDAO   produtoDAO   = new ProdutoDAO();
    private final VendaDAO     vendaDAO     = new VendaDAO();

    // ---------- CATEGORIAS ----------

    public Categoria criarCategoria(String nome, String descricao, Long userId) {
        if (nome == null || nome.trim().isEmpty())
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        Categoria c = new Categoria(nome.trim(), descricao);
        c.setUsuarioId(userId);
        return categoriaDAO.salvar(c);
    }

    public Categoria actualizarCategoria(Categoria categoria) {
        if (categoria.getNome() == null || categoria.getNome().trim().isEmpty())
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        return categoriaDAO.actualizar(categoria);
    }

    public List<Produto> listarProdutosPorCategoria(Long catId, Long userId) {
        return produtoDAO.buscarPorCategoria(catId, userId);
    }

    public void eliminarCategoria(Long id, Long userId) {
        // Remove todos os produtos da categoria primeiro (e as suas vendas por cascade)
        List<Produto> prods = produtoDAO.buscarPorCategoria(id, userId);
        for (Produto p : prods) {
            produtoDAO.eliminar(p.getId());
        }
        categoriaDAO.eliminar(id);
    }

    public List<Categoria> listarCategorias(Long userId) {
        return categoriaDAO.listarOrdenado(userId);
    }

    public Categoria buscarCategoria(Long id) {
        return categoriaDAO.buscarPorId(id);
    }

    // ---------- PRODUTOS ----------

    public Produto criarProduto(String nome, Double preco, Double stock, String unidade,
                                Double stockMinimo, Categoria categoria, Long userId) {
        validarProduto(nome, preco, stock);
        Produto p = new Produto(nome.trim(), preco, stock, unidade, categoria);
        if (stockMinimo != null) p.setStockMinimo(stockMinimo);
        p.setUsuarioId(userId);
        return produtoDAO.salvar(p);
    }

    public Produto actualizarProduto(Produto produto) {
        validarProduto(produto.getNome(), produto.getPreco(), produto.getQuantidadeStock());
        return produtoDAO.actualizar(produto);
    }

    public void eliminarProduto(Long id) {
        produtoDAO.eliminar(id);
    }

    public List<Produto> listarProdutos(Long userId) {
        return produtoDAO.listarOrdenado(userId);
    }

    public List<Produto> buscarProdutosPorNome(String nome, Long userId) {
        return produtoDAO.buscarPorNome(nome, userId);
    }

    public List<Produto> produtosComStockBaixo(Long userId) {
        return produtoDAO.buscarStockBaixo(userId);
    }

    public long totalProdutos(Long userId) {
        return produtoDAO.contarTodos(userId);
    }

    // ---------- VENDAS ----------

    public Venda registarVenda(Long produtoId, double quantidade, String observacao, Long userId) {
        Produto produto = produtoDAO.buscarPorId(produtoId);
        if (produto == null)
            throw new IllegalArgumentException("Produto não encontrado.");
        if (quantidade <= 0)
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        if (produto.getQuantidadeStock() < quantidade)
            throw new IllegalStateException("Stock insuficiente. Stock actual: " + produto.getQuantidadeStock() + " " + produto.getUnidade());

        Venda venda = new Venda(LocalDate.now(), quantidade, produto, observacao);
        venda.setUsuarioId(userId);
        venda = vendaDAO.salvar(venda);

        produtoDAO.actualizarStock(produtoId, quantidade);
        return venda;
    }

    /** Regista múltiplos itens de uma só vez (carrinho de compras). */
    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens, Long userId) {
        double totalGeral = 0;
        int totalItens = 0;
        for (Map<String, Object> item : itens) {
            Long   produtoId = ((Number) item.get("produtoId")).longValue();
            double quantidade = ((Number) item.get("quantidade")).doubleValue();
            Venda v = registarVenda(produtoId, quantidade, null, userId);
            totalGeral += v.getTotal();
            totalItens++;
        }
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("itens", totalItens);
        r.put("total", totalGeral);
        return r;
    }

    public List<Venda> listarVendas(Long userId) {
        return vendaDAO.listarOrdenado(userId);
    }

    public List<Venda> vendasDeHoje(Long userId) {
        return vendaDAO.vendasDeHoje(userId);
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long userId) {
        return vendaDAO.vendasEntreDatas(inicio, fim, userId);
    }

    public double totalVendasHoje(Long userId) {
        return vendaDAO.totalVendasHoje(userId);
    }

    public double totalVendasPeriodo(LocalDate inicio, LocalDate fim, Long userId) {
        return vendaDAO.totalVendasPeriodo(inicio, fim, userId);
    }

    public long vendasHoje(Long userId) {
        return vendaDAO.contarVendasHoje(userId);
    }

    // ---------- VALIDAÇÕES ----------

    private void validarProduto(String nome, Double preco, Double stock) {
        if (nome == null || nome.trim().isEmpty())
            throw new IllegalArgumentException("O nome do produto não pode estar vazio.");
        if (preco == null || preco < 0)
            throw new IllegalArgumentException("O preço deve ser um valor positivo.");
        if (stock == null || stock < 0)
            throw new IllegalArgumentException("O stock não pode ser negativo.");
    }
}
