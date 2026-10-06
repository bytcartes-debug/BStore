package service;

import dao.CategoriaDAO;
import dao.ProdutoDAO;
import dao.VendaDAO;
import model.Categoria;
import model.Produto;
import model.Venda;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.PessimisticLockException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BarracaService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final VendaDAO vendaDAO = new VendaDAO();

    public Categoria criarCategoria(String nome, String descricao, Long usuarioId) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        }
        Categoria categoria = new Categoria(nome.trim(), descricao);
        categoria.setUsuarioId(usuarioId);
        return categoriaDAO.salvar(categoria);
    }

    public Categoria actualizarCategoria(Categoria categoria, Long usuarioId) {
        if (categoria == null || !usuarioId.equals(categoria.getUsuarioId())) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        if (categoria.getNome() == null || categoria.getNome().trim().isEmpty()) {
            throw new IllegalArgumentException("O nome da categoria não pode estar vazio.");
        }
        return categoriaDAO.actualizar(categoria);
    }

    public List<Produto> listarProdutosPorCategoria(Long categoriaId, Long usuarioId) {
        return produtoDAO.buscarPorCategoria(categoriaId, usuarioId);
    }

    public void eliminarCategoria(Long id, Long usuarioId) {
        Categoria categoria = categoriaDAO.buscarPorId(id, usuarioId);
        if (categoria == null) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        if (categoriaDAO.temProdutos(id, usuarioId)) {
            throw new IllegalStateException("Não é possível eliminar uma categoria com produtos associados.");
        }
        categoriaDAO.eliminar(id);
    }

    public List<Categoria> listarCategorias(Long usuarioId) {
        return categoriaDAO.listarOrdenado(usuarioId);
    }

    public Categoria buscarCategoria(Long id, Long usuarioId) {
        return categoriaDAO.buscarPorId(id, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        validarProduto(nome, preco, stock);
        Categoria categoria = categoriaDAO.buscarPorId(categoriaId, usuarioId);
        if (categoria == null) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        String codigoNormalizado = normalizarCodigoBarras(codigoBarras);
        if (produtoDAO.codigoBarrasEmUso(codigoNormalizado, usuarioId, null)) {
            throw new ConflitoException("Já existe um produto com este código de barras.");
        }
        Produto produto = new Produto(nome.trim(), moeda(preco), quantidade(stock), unidade, categoria);
        produto.setStockMinimo(stockMinimo(stockMinimo));
        produto.setCodigoBarras(codigoNormalizado);
        produto.setUsuarioId(usuarioId);
        return produtoDAO.salvar(produto);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        Produto produto = produtoDAO.buscarPorId(id, usuarioId);
        if (produto == null) {
            throw new RecursoNaoEncontradoException("Produto não encontrado.");
        }
        validarProduto(nome, preco, stock);
        Categoria categoria = categoriaDAO.buscarPorId(categoriaId, usuarioId);
        if (categoria == null) {
            throw new RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        String codigoNormalizado = normalizarCodigoBarras(codigoBarras);
        if (produtoDAO.codigoBarrasEmUso(codigoNormalizado, usuarioId, id)) {
            throw new ConflitoException("Já existe um produto com este código de barras.");
        }
        produto.setNome(nome.trim());
        produto.setPreco(moeda(preco));
        produto.setQuantidadeStock(quantidade(stock));
        produto.setStockMinimo(stockMinimo(stockMinimo));
        produto.setUnidade(unidade);
        produto.setCategoria(categoria);
        produto.setCodigoBarras(codigoNormalizado);
        return produtoDAO.actualizar(produto);
    }

    public void eliminarProduto(Long id, Long usuarioId) {
        Produto produto = produtoDAO.buscarPorId(id, usuarioId);
        if (produto == null) {
            throw new RecursoNaoEncontradoException("Produto não encontrado.");
        }
        if (vendaDAO.existeParaProduto(id, usuarioId)) {
            throw new IllegalStateException("Não é possível eliminar um produto com vendas registadas.");
        }
        produtoDAO.eliminar(id);
    }

    public Produto buscarPorCodigoBarras(String codigo, Long usuarioId) {
        return produtoDAO.buscarPorCodigoBarras(codigo, usuarioId);
    }

    public List<Produto> listarProdutos(Long usuarioId) {
        return produtoDAO.listarOrdenado(usuarioId);
    }

    public List<Produto> buscarProdutosPorNome(String nome, Long usuarioId) {
        return produtoDAO.buscarPorNome(nome, usuarioId);
    }

    public List<Produto> produtosComStockBaixo(Long usuarioId) {
        return produtoDAO.buscarStockBaixo(usuarioId);
    }

    public long totalProdutos(Long usuarioId) {
        return produtoDAO.contarTodos(usuarioId);
    }

    public Venda registarVenda(Long produtoId, BigDecimal quantidade, String observacao, Long usuarioId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("produtoId", produtoId);
        item.put("quantidade", quantidade);
        Map<String, Object> resultado = registarVendaLote(List.of(item), usuarioId);
        @SuppressWarnings("unchecked")
        List<Venda> vendas = (List<Venda>) resultado.get("vendas");
        return vendas.get(0);
    }

    public Map<String, Object> registarVendaLote(List<Map<String, Object>> itens, Long usuarioId) {
        Map<Long, BigDecimal> quantidades = normalizarCarrinho(itens);
        try {
            return JPAUtil.emTransacao(usuarioId, em -> executarVendaLote(em, quantidades, usuarioId));
        } catch (PessimisticLockException e) {
            throw new ConflitoException("O stock está a ser actualizado por outra venda. Tente novamente.");
        }
    }

    static Map<Long, BigDecimal> normalizarCarrinho(List<Map<String, Object>> itens) {
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

    Map<String, Object> executarVendaLote(EntityManager em, Map<Long, BigDecimal> quantidades, Long usuarioId) {
        Map<Long, Produto> produtos = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> item : quantidades.entrySet()) {
            Produto produto = produtoDAO.buscarPorIdParaVenda(em, item.getKey(), usuarioId);
            if (produto == null) throw new RecursoNaoEncontradoException("Produto não encontrado.");
            if (produto.getQuantidadeStock().compareTo(item.getValue()) < 0) {
                throw new ConflitoException("Stock insuficiente para " + produto.getNome() + ".");
            }
            produtos.put(item.getKey(), produto);
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Venda> vendas = new java.util.ArrayList<>();
        for (Map.Entry<Long, BigDecimal> item : quantidades.entrySet()) {
            Produto produto = produtos.get(item.getKey());
            Venda venda = new Venda(LocalDate.now(), item.getValue(), produto, null);
            venda.setUsuarioId(usuarioId);
            produto.setQuantidadeStock(produto.getQuantidadeStock().subtract(item.getValue()));
            em.persist(venda);
            vendas.add(venda);
            total = total.add(venda.getTotal());
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("itens", vendas.size());
        resultado.put("total", moeda(total));
        resultado.put("vendas", vendas);
        return resultado;
    }

    public List<Venda> listarVendas(Long usuarioId) {
        return vendaDAO.listarOrdenado(usuarioId);
    }

    public List<Venda> vendasDeHoje(Long usuarioId) {
        return vendaDAO.vendasDeHoje(usuarioId);
    }

    public List<Venda> vendasEntreDatas(LocalDate inicio, LocalDate fim, Long usuarioId) {
        return vendaDAO.vendasEntreDatas(inicio, fim, usuarioId);
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

    public static BigDecimal decimal(Object value, String campo) {
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser numérico.");
        }
    }

    private static Long longValue(Object value, String campo) {
        if (value == null) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O campo " + campo + " deve ser um identificador válido.");
        }
    }

    private static void validarProduto(String nome, BigDecimal preco, BigDecimal stock) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do produto não pode estar vazio.");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("O preço deve ser um valor não negativo.");
        }
        if (stock == null || stock.signum() < 0) {
            throw new IllegalArgumentException("O stock não pode ser negativo.");
        }
        moeda(preco);
        quantidade(stock);
    }

    private static BigDecimal moeda(BigDecimal valor) {
        if (valor.scale() > 2) {
            throw new IllegalArgumentException("Os valores monetários aceitam no máximo duas casas decimais.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantidade(BigDecimal valor) {
        if (valor.scale() > 3) {
            throw new IllegalArgumentException("As quantidades aceitam no máximo três casas decimais.");
        }
        return valor.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal stockMinimo(BigDecimal valor) {
        BigDecimal normalizado = valor == null ? new BigDecimal("5.000") : quantidade(valor);
        if (normalizado.signum() < 0) {
            throw new IllegalArgumentException("O stock mínimo não pode ser negativo.");
        }
        return normalizado;
    }

    private static String normalizarCodigoBarras(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return null;
        }
        if (codigo.trim().length() > 100) {
            throw new IllegalArgumentException("O código de barras é demasiado longo.");
        }
        return codigo.trim();
    }

    public static final class RecursoNaoEncontradoException extends RuntimeException {
        public RecursoNaoEncontradoException(String message) { super(message); }
    }

    public static class ConflitoException extends RuntimeException {
        public ConflitoException(String message) { super(message); }
    }
}
