package dao;

import model.DefinicaoLoja;
import util.JPAUtil;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;

public class DefinicaoLojaDAO extends GenericDAO<DefinicaoLoja> {

    public DefinicaoLojaDAO() {
        super(DefinicaoLoja.class);
    }

    public DefinicaoLoja obter(Long uid) {
        return JPAUtil.emTransacao(uid, em -> obterOuCriar(em, uid));
    }

    public DefinicaoLoja obterOuCriar(EntityManager em, Long uid) {
        DefinicaoLoja def = em.find(DefinicaoLoja.class, uid);
        if (def == null) {
            def = new DefinicaoLoja(uid, false, null);
            em.persist(def);
            em.flush();
        }
        return def;
    }

    public DefinicaoLoja atualizar(Long uid, boolean controloCaixa, String nomeLoja) {
        return atualizar(uid, controloCaixa, nomeLoja, null, null);
    }

    public DefinicaoLoja atualizar(Long uid, Boolean controloCaixa, String nomeLoja,
                                   Boolean whatsappActivo, String whatsappTelefone) {
        return JPAUtil.emTransacao(uid, em -> {
            DefinicaoLoja def = obterOuCriar(em, uid);
            if (controloCaixa != null) {
                def.setControloCaixa(controloCaixa);
            }
            if (nomeLoja != null) {
                def.setNomeLoja(nomeLoja.trim());
            }
            if (whatsappActivo != null) {
                def.setWhatsappNotificacoesActivas(whatsappActivo);
            }
            if (whatsappTelefone != null) {
                def.setWhatsappTelefoneDono(whatsappTelefone.trim());
            }
            def.setAtualizadoEm(util.Fuso.agora());
            return em.merge(def);
        });
    }

    @Deprecated
    public DefinicaoLoja atualizar(Long uid, Boolean controloCaixa, String nomeLoja,
                                   Boolean whatsappActivo, String whatsappTelefone,
                                   String zapiInstanceId, String zapiToken, String zapiClientToken) {
        return JPAUtil.emTransacao(uid, em -> {
            DefinicaoLoja def = obterOuCriar(em, uid);
            if (controloCaixa != null) {
                def.setControloCaixa(controloCaixa);
            }
            if (nomeLoja != null) {
                def.setNomeLoja(nomeLoja.trim());
            }
            if (whatsappActivo != null) {
                def.setWhatsappNotificacoesActivas(whatsappActivo);
            }
            if (whatsappTelefone != null) {
                def.setWhatsappTelefoneDono(whatsappTelefone.trim());
            }
            if (zapiInstanceId != null) {
                def.setZapiInstanceId(zapiInstanceId.trim());
            }
            if (zapiToken != null) {
                def.setZapiToken(zapiToken.trim());
            }
            if (zapiClientToken != null) {
                def.setZapiClientToken(zapiClientToken.trim());
            }
            def.setAtualizadoEm(util.Fuso.agora());
            return em.merge(def);
        });
    }
}
