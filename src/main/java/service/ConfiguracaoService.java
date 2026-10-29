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

    public DefinicaoLoja atualizarDefinicoesLoja(Boolean controloCaixa, String nomeLoja,
                                                Boolean whatsappActivo, String whatsappTelefone,
                                                Long usuarioId) {
        return definicaoLojaDAO.atualizar(usuarioId, controloCaixa, nomeLoja, whatsappActivo, whatsappTelefone);
    }

    @Deprecated
    public DefinicaoLoja atualizarDefinicoesLoja(Boolean controloCaixa, String nomeLoja,
                                                Boolean whatsappActivo, String whatsappTelefone,
                                                String zapiInstanceId, String zapiToken, String zapiClientToken,
                                                Long usuarioId) {
        return definicaoLojaDAO.atualizar(usuarioId, controloCaixa, nomeLoja, whatsappActivo, whatsappTelefone, zapiInstanceId, zapiToken, zapiClientToken);
    }
}
