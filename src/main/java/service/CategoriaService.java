package service;

import dao.CategoriaDAO;
import dao.ProdutoDAO;
import model.Categoria;
import model.Produto;

import java.util.List;

public class CategoriaService {

    private final CategoriaDAO categoriaDAO = new CategoriaDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();

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
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
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
            throw new BarracaService.RecursoNaoEncontradoException("Categoria não encontrada.");
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
}
