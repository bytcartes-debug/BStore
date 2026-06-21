package util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class JPAUtil {

    private static EntityManagerFactory emf;

    public static void inicializar() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("barracaPU");
        }
    }

    public static EntityManager getEntityManager() {
        if (emf == null || !emf.isOpen()) {
            inicializar();
        }
        return emf.createEntityManager();
    }

    public static void fechar() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
