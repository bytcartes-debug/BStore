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
        return JPAUtil.emTransacao(uid, em -> {
            DefinicaoLoja def = obterOuCriar(em, uid);
            def.setControloCaixa(controloCaixa);
            if (nomeLoja != null) {
                def.setNomeLoja(nomeLoja.trim());
            }
            def.setAtualizadoEm(LocalDateTime.now());
            return em.merge(def);
        });
    }
}
