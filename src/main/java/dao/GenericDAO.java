package dao;

import util.JPAUtil;

import javax.persistence.EntityManager;
import java.util.List;

public class GenericDAO<T> {

    private Class<T> classe;

    public GenericDAO(Class<T> classe) {
        this.classe = classe;
    }

    protected EntityManager getEM() {
        return JPAUtil.getEntityManager();
    }

    public T salvar(T objeto) {
        EntityManager em = getEM();
        try {
            em.getTransaction().begin();
            em.persist(objeto);
            em.getTransaction().commit();
            return objeto;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public T buscarPorId(Long id) {
        EntityManager em = getEM();
        try {
            return em.find(classe, id);
        } finally {
            em.close();
        }
    }

    public List<T> listarTodos() {
        EntityManager em = getEM();
        try {
            String jpql = "SELECT e FROM " + classe.getSimpleName() + " e";
            return em.createQuery(jpql, classe).getResultList();
        } finally {
            em.close();
        }
    }

    public T actualizar(T objeto) {
        EntityManager em = getEM();
        try {
            em.getTransaction().begin();
            T resultado = em.merge(objeto);
            em.getTransaction().commit();
            return resultado;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public void eliminar(Long id) {
        EntityManager em = getEM();
        try {
            em.getTransaction().begin();
            T objeto = em.find(classe, id);
            if (objeto != null) {
                em.remove(objeto);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }
}
