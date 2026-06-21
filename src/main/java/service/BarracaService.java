package service;

import dao.CategoriaDAO;
import dao.ProdutoDAO;
import dao.VendaDAO;
import model.Categoria;
import model.Produto;
import model.Venda;

import java.time.LocalDate;
import java.util.List;

public class BarracaService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProdutoDAO produtoDAO     = new ProdutoDAO();
    private final VendaDAO vendaDAO         = new VendaDAO();

    // ---------- CATEGORIAS ----------

    public Categoria criarCategoria(String nome, String descricao) {
        if (nome == null || nome.trim().isEmpty())
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        return categoriaDAO.salvar(new Categoria(nome.trim(), descricao));
    }

    public Categoria actualizarCategoria(Categoria categoria) {
        if (categoria.getNome() == null || categoria.getNome().trim().isEmpty())
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        return categoriaDAO.actualizar(categoria);
    }

    public void eliminarCategoria(Long id) {
        if (categoriaDAO.temProdutos(id))
            throw new IllegalStateException("Não é possível eliminar: esta categoria tem produtos associados.");
        categoriaDAO.eliminar(id);
    }

    public List<Categoria> listarCategorias() {
        return categoriaDAO.listarOrdenado();
    }

    public Categoria buscarCategoria(Long id) {
        return categoriaDAO.buscarPorId(id);
    }

    // ---------- PRODUTOS ----------

    public Produto criarProduto(String nome, Double preco, Integer stock, String unidade,
                                 Integer stockMinimo, Categoria categoria) {
        validarProduto(nome, preco, stock);
        Produto p = new Produto(nome.trim(), preco, stock, unidade, categoria);
        if (stockMinimo != null) p.setStockMinimo(stockMinimo);
        return produtoDAO.salvar(p);
    }

    public Produto actualizarProduto(Produto produto) {
        validarProduto(produto.getNome(), produto.getPreco(), produto.getQuantidadeStock());
        return produtoDAO.actualizar(produto);
    }

    public void eliminarProduto(Long id) {
        produtoDAO.eliminar(id);
    }

    public List<Produto> listarProdutos() {
        return produtoDAO.listarOrdenado();
    }

    public List<Produto> buscarProdutosPorNome(String nome) {
        return produtoDAO.buscarPorNome(nome);
    }

    public List<Produto> produtosComStockBaixo() {
        return produtoDAO.buscarStockBaixo();
    }

    public long totalProdutos() {
        return produtoDAO.contarTodos();
    }

    // ---------- VENDAS ----------

    public Venda registarVenda(Long produtoId, int quantidade, String observacao) {
        Produto produto = produtoDAO.buscarPorId(produtoId);
        if (produto == null)
            throw new IllegalArgumentException("Produto não encontrado.");
        if (quantidade <= 0)
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        if (produto.getQuantidadeStock() < quantidade)
            throw new IllegalStateException("Stock insuficiente. Stock actual: " + produto.getQuantidadeStock());

        Venda venda = new Venda(LocalDate.now(), quantidade, produto, observacao);
        venda = vendaDAO.salvar(venda);

        produtoDAO.actualizarStock(produtoId, quantidade);

        return venda;
    }

    public List<Venda> listarVendas() {
        return vendaDAO.listarOrdenado();
    }

    public List<Venda> vendasDeHoje() {
        return vendaDAO.vendasDeHoje();
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim) {
        return vendaDAO.vendasEntreDatas(inicio, fim);
    }

    public double totalVendasHoje() {
        return vendaDAO.totalVendasHoje();
    }

    public double totalVendasPeriodo(LocalDate inicio, LocalDate fim) {
        return vendaDAO.totalVendasPeriodo(inicio, fim);
    }

    public long vendasHoje() {
        return vendaDAO.contarVendasHoje();
    }

    // ---------- VALIDAÇÕES ----------

    private void validarProduto(String nome, Double preco, Integer stock) {
        if (nome == null || nome.trim().isEmpty())
            throw new IllegalArgumentException("O nome do produto não pode estar vazio.");
        if (preco == null || preco < 0)
            throw new IllegalArgumentException("O preço deve ser um valor positivo.");
        if (stock == null || stock < 0)
            throw new IllegalArgumentException("O stock não pode ser negativo.");
    }
}
