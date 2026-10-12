package service;

import dao.DefinicaoLojaDAO;
import model.DefinicaoLoja;

public class ConfiguracaoService {

    private final DefinicaoLojaDAO definicaoLojaDAO = new DefinicaoLojaDAO();

    public DefinicaoLoja obterDefinicoesLoja(Long usuarioId) {
        return definicaoLojaDAO.obter(usuarioId);
    }

    public DefinicaoLoja atualizarDefinicoesLoja(boolean controloCaixa, String nomeLoja, Long usuarioId) {
        return definicaoLojaDAO.atualizar(usuarioId, controloCaixa, nomeLoja);
    }
}
