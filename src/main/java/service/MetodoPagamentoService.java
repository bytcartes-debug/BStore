package service;

import dao.MetodoPagamentoDAO;
import model.MetodoPagamento;
import util.JPAUtil;

import java.util.List;

public class MetodoPagamentoService {

    private final MetodoPagamentoDAO metodoPagamentoDAO = new MetodoPagamentoDAO();

    public List<MetodoPagamento> listarMetodosPagamento(Long usuarioId) {
        return metodoPagamentoDAO.listarTodos(usuarioId);
    }

    public List<MetodoPagamento> listarMetodosPagamentoAtivos(Long usuarioId) {
        return metodoPagamentoDAO.listarAtivos(usuarioId);
    }

    public MetodoPagamento criarMetodoPagamento(String nome, String tipo, Long usuarioId) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do método de pagamento é obrigatório.");
        }
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase() : "DIGITAL";
        if (!List.of("DINHEIRO", "DIGITAL", "FIADO").contains(tipoNorm)) {
            throw new IllegalArgumentException("Tipo de método de pagamento inválido: " + tipoNorm);
        }
        return JPAUtil.emTransacao(usuarioId, em -> {
            MetodoPagamento existente = metodoPagamentoDAO.buscarPorNome(em, nome.trim(), usuarioId);
            if (existente != null) {
                throw new BarracaService.ConflitoException("Já existe um método de pagamento com o nome " + nome.trim() + ".");
            }
            Long count = em.createQuery("SELECT COUNT(m) FROM MetodoPagamento m WHERE m.usuarioId = :uid", Long.class)
                    .setParameter("uid", usuarioId).getSingleResult();
            MetodoPagamento novo = new MetodoPagamento(usuarioId, nome.trim(), tipoNorm, true, count.intValue() + 1);
            em.persist(novo);
            return novo;
        });
    }

    public MetodoPagamento alternarMetodoPagamento(Long id, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            MetodoPagamento metodo = metodoPagamentoDAO.buscarPorId(em, id, usuarioId);
            if (metodo == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            metodo.setAtivo(!metodo.getAtivo());
            em.merge(metodo);
            return metodo;
        });
    }

    public MetodoPagamento actualizarMetodoPagamento(Long id, String nome, Boolean ativo, Integer ordem, Long usuarioId) {
        return JPAUtil.emTransacao(usuarioId, em -> {
            MetodoPagamento metodo = metodoPagamentoDAO.buscarPorId(em, id, usuarioId);
            if (metodo == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            if (nome != null && !nome.trim().isEmpty()) {
                MetodoPagamento comMesmoNome = metodoPagamentoDAO.buscarPorNome(em, nome.trim(), usuarioId);
                if (comMesmoNome != null && !comMesmoNome.getId().equals(id)) {
                    throw new BarracaService.ConflitoException("Já existe um método de pagamento com o nome " + nome.trim() + ".");
                }
                metodo.setNome(nome.trim());
            }
            if (ativo != null) metodo.setAtivo(ativo);
            if (ordem != null) metodo.setOrdem(ordem);
            em.merge(metodo);
            return metodo;
        });
    }

    public void eliminarMetodoPagamento(Long id, Long usuarioId) {
        JPAUtil.emTransacao(usuarioId, em -> {
            MetodoPagamento metodo = metodoPagamentoDAO.buscarPorId(em, id, usuarioId);
            if (metodo == null) {
                throw new BarracaService.RecursoNaoEncontradoException("Método de pagamento não encontrado.");
            }
            Long vendasComMetodo = em.createQuery(
                "SELECT COUNT(pv) FROM PagamentoVenda pv WHERE pv.metodoPagamento.id = :mid AND pv.usuarioId = :uid", Long.class)
                .setParameter("mid", id)
                .setParameter("uid", usuarioId)
                .getSingleResult();
            if (vendasComMetodo > 0) {
                throw new IllegalStateException("Não é possível apagar um método de pagamento com vendas associadas. Desactive-o em vez de apagar.");
            }
            em.remove(metodo);
            return null;
        });
    }
}
