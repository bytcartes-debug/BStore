package dao;

import model.RegistroAuditoria;
import util.JPAUtil;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

public class RegistroAuditoriaDAO extends GenericDAO<RegistroAuditoria> {

    public RegistroAuditoriaDAO() {
        super(RegistroAuditoria.class);
    }

    public List<RegistroAuditoria> listarComFiltros(Long usuarioId, String tipoEntidade, String acao, int offset, int limite) {
        EntityManager em = getEM();
        try {
            StringBuilder jpql = new StringBuilder("SELECT a FROM RegistroAuditoria a WHERE a.usuarioId = :uid");
            if (tipoEntidade != null && !tipoEntidade.isBlank()) {
                jpql.append(" AND a.tipoEntidade = :tipo");
            }
            if (acao != null && !acao.isBlank()) {
                jpql.append(" AND a.acao = :acao");
            }
            jpql.append(" ORDER BY a.criadoEm DESC");

            TypedQuery<RegistroAuditoria> query = em.createQuery(jpql.toString(), RegistroAuditoria.class);
            query.setParameter("uid", usuarioId);
            if (tipoEntidade != null && !tipoEntidade.isBlank()) {
                query.setParameter("tipo", tipoEntidade.trim().toUpperCase());
            }
            if (acao != null && !acao.isBlank()) {
                query.setParameter("acao", acao.trim().toUpperCase());
            }

            query.setFirstResult(Math.max(0, offset));
            query.setMaxResults(limite > 0 ? Math.min(limite, 100) : 50);

            return query.getResultList();
        } finally {
            em.close();
        }
    }

    public long contarComFiltros(Long usuarioId, String tipoEntidade, String acao) {
        EntityManager em = getEM();
        try {
            StringBuilder jpql = new StringBuilder("SELECT COUNT(a) FROM RegistroAuditoria a WHERE a.usuarioId = :uid");
            if (tipoEntidade != null && !tipoEntidade.isBlank()) {
                jpql.append(" AND a.tipoEntidade = :tipo");
            }
            if (acao != null && !acao.isBlank()) {
                jpql.append(" AND a.acao = :acao");
            }

            TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class);
            query.setParameter("uid", usuarioId);
            if (tipoEntidade != null && !tipoEntidade.isBlank()) {
                query.setParameter("tipo", tipoEntidade.trim().toUpperCase());
            }
            if (acao != null && !acao.isBlank()) {
                query.setParameter("acao", acao.trim().toUpperCase());
            }

            Long total = query.getSingleResult();
            return total != null ? total : 0L;
        } finally {
            em.close();
        }
    }
}
