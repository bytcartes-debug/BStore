package dao;

import model.ItemVenda;
import util.JPAUtil;

import java.time.LocalDate;
import java.util.List;

public class ItemVendaDAO extends GenericDAO<ItemVenda> {

    public ItemVendaDAO() {
        super(ItemVenda.class);
    }

    public List<ItemVenda> listarPorVenda(Long usuarioId, Long vendaId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT i FROM ItemVenda i JOIN FETCH i.produto WHERE i.usuarioId = :uid AND i.venda.id = :vid ORDER BY i.id ASC",
            ItemVenda.class)
            .setParameter("uid", usuarioId)
            .setParameter("vid", vendaId)
            .getResultList());
    }

    public boolean existeParaProduto(Long produtoId, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT COUNT(i) FROM ItemVenda i WHERE i.produto.id = :produtoId AND i.usuarioId = :usuarioId",
            Long.class)
            .setParameter("produtoId", produtoId)
            .setParameter("usuarioId", usuarioId)
            .getSingleResult() > 0);
    }

    public List<Object[]> produtosMaisVendidos(Long usuarioId, LocalDate inicio, LocalDate fim, int limite) {
        return JPAUtil.emTransacao(usuarioId, em -> em.createQuery(
            "SELECT i.produto.id, i.produto.nome, SUM(i.quantidade), SUM(i.total) " +
            "FROM ItemVenda i JOIN i.venda v " +
            "WHERE i.usuarioId = :uid AND v.estado = 'CONCLUIDA' AND i.dataVenda BETWEEN :inicio AND :fim " +
            "GROUP BY i.produto.id, i.produto.nome " +
            "ORDER BY SUM(i.quantidade) DESC", Object[].class)
            .setParameter("uid", usuarioId)
            .setParameter("inicio", inicio)
            .setParameter("fim", fim)
            .setMaxResults(limite)
            .getResultList());
    }
}
