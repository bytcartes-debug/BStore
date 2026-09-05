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

            String rawUrl = System.getenv("DATABASE_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASSWORD");

            if (rawUrl != null && !rawUrl.isEmpty()) {
                String jdbcUrl = rawUrl;

                // Trata formato: postgres://user:pass@host/db  ou  postgresql://user:pass@host/db
                if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                    String sem = rawUrl.startsWith("postgresql://")
                        ? rawUrl.substring("postgresql://".length())
                        : rawUrl.substring("postgres://".length());

                    // Extrai user:pass do URL se presentes e DB_USER/DB_PASSWORD não definidos
                    if (sem.contains("@")) {
                        String userInfo = sem.substring(0, sem.indexOf("@"));
                        String hostDb   = sem.substring(sem.indexOf("@") + 1);

                        if ((dbUser == null || dbUser.isEmpty()) && userInfo.contains(":")) {
                            dbUser = userInfo.substring(0, userInfo.indexOf(":"));
                            dbPass = userInfo.substring(userInfo.indexOf(":") + 1);
                        } else if ((dbUser == null || dbUser.isEmpty())) {
                            dbUser = userInfo;
                        }
                        jdbcUrl = "jdbc:postgresql://" + hostDb;
                    } else {
                        jdbcUrl = "jdbc:postgresql://" + sem;
                    }
                }

                // Adiciona SSL apenas se não interno (Render interno não precisa)
                if (!jdbcUrl.contains("sslmode") && !jdbcUrl.contains("ssl=")
                        && !jdbcUrl.contains(".internal")) {
                    jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
                }

                props.put("javax.persistence.jdbc.url",    jdbcUrl);
                props.put("javax.persistence.jdbc.driver", "org.postgresql.Driver");
                props.put("hibernate.dialect",             "org.hibernate.dialect.PostgreSQLDialect");
                props.put("hibernate.hbm2ddl.auto",        "update");
                props.put("hibernate.show_sql",            "false");

                if (dbUser != null && !dbUser.isEmpty())
                    props.put("javax.persistence.jdbc.user", dbUser);
                if (dbPass != null && !dbPass.isEmpty())
                    props.put("javax.persistence.jdbc.password", dbPass);

                System.out.println("[DB] Conectando ao PostgreSQL: " + jdbcUrl.replaceAll("password=[^&]+", "password=***"));
            } else {
                props.put("javax.persistence.jdbc.url",      "jdbc:h2:./barraca-db;AUTO_SERVER=TRUE");
                props.put("javax.persistence.jdbc.driver",   "org.h2.Driver");
                props.put("javax.persistence.jdbc.user",     "sa");
                props.put("javax.persistence.jdbc.password", "");
                props.put("hibernate.dialect",               "org.hibernate.dialect.H2Dialect");
                props.put("hibernate.hbm2ddl.auto",          "update");
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
