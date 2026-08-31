package util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class JPAUtil {

    private static EntityManagerFactory emf;

    public static void inicializar() {
        if (emf == null || !emf.isOpen()) {
            Map<String, String> props = new HashMap<>();

            // Tenta ler configuração do PostgreSQL via variáveis de ambiente (Railway/Supabase)
            String dbUrl  = System.getenv("DATABASE_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASSWORD");

            if (dbUrl != null && !dbUrl.isEmpty()) {
                // Converte URL postgres:// para formato JDBC se necessário
                if (dbUrl.startsWith("postgres://")) {
                    dbUrl = dbUrl.replace("postgres://", "jdbc:postgresql://");
                }
                props.put("javax.persistence.jdbc.url",      dbUrl);
                props.put("javax.persistence.jdbc.driver",   "org.postgresql.Driver");
                props.put("javax.persistence.jdbc.user",     dbUser != null ? dbUser : "");
                props.put("javax.persistence.jdbc.password", dbPass != null ? dbPass : "");
                props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
                props.put("hibernate.hbm2ddl.auto", "update");
                System.out.println("[DB] Conectando ao PostgreSQL (nuvem)...");
            } else {
                // Fallback para H2 local
                props.put("javax.persistence.jdbc.url",      "jdbc:h2:./barraca-db;AUTO_SERVER=TRUE");
                props.put("javax.persistence.jdbc.driver",   "org.h2.Driver");
                props.put("javax.persistence.jdbc.user",     "sa");
                props.put("javax.persistence.jdbc.password", "");
                props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
                props.put("hibernate.hbm2ddl.auto", "update");
                System.out.println("[DB] Usando banco H2 local...");
            }

            emf = Persistence.createEntityManagerFactory("barracaPU", props);
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
