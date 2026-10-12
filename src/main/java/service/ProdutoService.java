package service;

import dao.CategoriaDAO;
import dao.ItemVendaDAO;
import dao.MovimentoStockDAO;
import dao.ProdutoDAO;
import model.Categoria;
import model.MovimentoStock;
import model.Produto;
import util.JPAUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static service.ServiceValidation.*;

public class ProdutoService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final ItemVendaDAO itemVendaDAO = new ItemVendaDAO();

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return criarProduto(nome, preco, BigDecimal.ZERO, stock, unidade, stockMinimo, categoriaId, codigoBarras, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return criarProduto(nome, preco, custo, stock, unidade, stockMinimo, null, categoriaId, codigoBarras, usuarioId);
    }

    public Produto criarProduto(String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                BigDecimal stockMinimo, BigDecimal stockMaximo, Long categoriaId, String codigoBarras, Long usuarioId) {
        validarProduto(nome, preco, stock);
        BigDecimal custoNorm = moeda(custo != null ? custo : BigDecimal.ZERO);
        Categoria categoria = categoriaDAO.buscarPorId(categoriaId, usuarioId);
        if (categoria == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        String codigoNormalizado = normalizarCodigoBarras(codigoBarras);
        if (produtoDAO.codigoBarrasEmUso(codigoNormalizado, usuarioId, null)) {
            throw new BarracaService.ConflitoException("Já existe um produto com este código de barras.");
        }
        BigDecimal stockNorm = quantidade(stock);
        BigDecimal stockMaxNorm = stockMaximo != null ? quantidade(stockMaximo) : null;
        return JPAUtil.emTransacao(usuarioId, em -> {
            Produto produto = new Produto(nome.trim(), moeda(preco), stockNorm, unidade, categoria);
            produto.setCusto(custoNorm);
            produto.setAtivo(true);
            produto.setStockMinimo(stockMinimo(stockMinimo));
            produto.setStockMaximo(stockMaxNorm);
            produto.setCodigoBarras(codigoNormalizado);
            produto.setUsuarioId(usuarioId);
            em.persist(produto);
            if (stockNorm.compareTo(BigDecimal.ZERO) > 0) {
                MovimentoStock mov = new MovimentoStock(
                    usuarioId, produto, "STOCK_INICIAL", stockNorm, custoNorm,
                    "Stock inicial do produto", null, null, usuarioId
                );
                em.persist(mov);
            }
            return produto;
        });
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return actualizarProduto(id, nome, preco, null, stock, unidade, stockMinimo, null, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, Long categoriaId, String codigoBarras, Long usuarioId) {
        return actualizarProduto(id, nome, preco, custo, stock, unidade, stockMinimo, null, categoriaId, codigoBarras, usuarioId);
    }

    public Produto actualizarProduto(Long id, String nome, BigDecimal preco, BigDecimal custo, BigDecimal stock, String unidade,
                                     BigDecimal stockMinimo, BigDecimal stockMaximo, Long categoriaId, String codigoBarras, Long usuarioId) {
        Produto produto = produtoDAO.buscarPorId(id, usuarioId);
        if (produto == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
        }
        if (stock != null && quantidade(stock).compareTo(produto.getQuantidadeStock()) != 0) {
            throw new IllegalArgumentException("Não é permitido alterar o stock directamente. Use Entrada ou Ajuste de stock.");
        }
        validarProduto(nome, preco, produto.getQuantidadeStock());
        Categoria categoria = categoriaDAO.buscarPorId(categoriaId, usuarioId);
        if (categoria == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
        }
        String codigoNormalizado = normalizarCodigoBarras(codigoBarras);
        if (produtoDAO.codigoBarrasEmUso(codigoNormalizado, usuarioId, id)) {
            throw new BarracaService.ConflitoException("Já existe um produto com este código de barras.");
        }
        produto.setNome(nome.trim());
        produto.setPreco(moeda(preco));
        if (custo != null) {
            produto.setCusto(moeda(custo));
        }
        produto.setStockMinimo(stockMinimo(stockMinimo));
        produto.setStockMaximo(stockMaximo != null ? quantidade(stockMaximo) : null);
        produto.setUnidade(unidade);
        produto.setCategoria(categoria);
        produto.setCodigoBarras(codigoNormalizado);
        return produtoDAO.actualizar(produto);
    }

    public void eliminarProduto(Long id, Long usuarioId) {
        Produto produto = produtoDAO.buscarPorId(id, usuarioId);
        if (produto == null) {
            throw new BarracaService.RecursoNaoEncontradoException("Produto não encontrado.");
        }
        boolean temVendas = itemVendaDAO.existeParaProduto(id, usuarioId);
        boolean temMovimentos = new MovimentoStockDAO().temMovimentosParaProduto(usuarioId, id);
        if (temVendas || temMovimentos) {
            produto.setAtivo(false);
            produtoDAO.actualizar(produto);
        } else {
            produtoDAO.eliminar(id);
        }
    }

    public Produto buscarPorCodigoBarras(String codigo, Long usuarioId) {
        return produtoDAO.buscarPorCodigoBarras(codigo, usuarioId);
    }

    public List<Produto> listarProdutos(Long usuarioId) {
        return listarProdutos(usuarioId, false);
    }

    public List<Produto> listarProdutos(Long usuarioId, boolean incluirArquivados) {
        return produtoDAO.listarOrdenado(usuarioId, incluirArquivados);
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

    public List<Map<String, Object>> obterProdutosFaltaRepor(Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            List<Produto> lista = em.createQuery(
                "SELECT p FROM Produto p WHERE p.usuarioId = :uid AND p.ativo = true AND p.quantidadeStock <= p.stockMinimo " +
                "ORDER BY (p.quantidadeStock - p.stockMinimo) ASC, p.nome ASC", Produto.class)
                .setParameter("uid", usuarioId)
                .getResultList();

            List<Map<String, Object>> res = new ArrayList<>();
            for (Produto p : lista) {
                BigDecimal atual = p.getQuantidadeStock();
                BigDecimal min = p.getStockMinimo() != null ? p.getStockMinimo() : BigDecimal.ZERO;
                BigDecimal max = p.getStockMaximo();
                BigDecimal sugerido;
                if (max != null && max.compareTo(atual) > 0) {
                    sugerido = max.subtract(atual);
                } else {
                    sugerido = min.multiply(new BigDecimal("2")).subtract(atual);
                }
                if (sugerido.compareTo(BigDecimal.ONE) < 0) {
                    sugerido = BigDecimal.ONE;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", p.getId());
                item.put("nome", p.getNome());
                item.put("stockAtual", decimalJson(atual, 3));
                item.put("stockMinimo", decimalJson(min, 3));
                item.put("stockMaximo", max != null ? decimalJson(max, 3) : null);
                item.put("quantidadeSugerida", decimalJson(sugerido, 3));
                item.put("unidade", p.getUnidade());
                item.put("preco", decimalJson(p.getPreco(), 2));
                item.put("custo", decimalJson(p.getCusto(), 2));
                res.add(item);
            }
            return res;
        });
    }
}
