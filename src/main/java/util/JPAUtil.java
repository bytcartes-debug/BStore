package util;

import org.flywaydb.core.Flyway;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public final class JPAUtil {

    private static EntityManagerFactory emf;

    /**
     * Indica se a ligação activa é PostgreSQL.
     * Usado para activar/desactivar comportamentos específicos do Postgres (ex: set_config RLS).
     */
    private static boolean isPostgres = false;

    /**
     * Contexto de utilizador por thread — injectado pelo filtro de autenticação da API
     * e lido em cada criação de EntityManager para configurar o RLS via set_config.
     */
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private JPAUtil() {
    }

    /**
     * Define o ID do utilizador autenticado para o thread actual.
     * Deve ser chamado pelo filtro de autenticação da API antes de qualquer acesso à BD.
     */
    public static void setCurrentUser(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * Remove o ID do utilizador do thread actual.
     * Deve ser chamado pelo filtro "after" da API para limpar o contexto após cada pedido.
     */
    public static void clearCurrentUser() {
        CURRENT_USER_ID.remove();
    }

    public static synchronized void inicializar() {
        if (emf != null && emf.isOpen()) {
            return;
        }

        DatabaseConfig config = databaseConfig();
        isPostgres = config.driver.contains("postgresql");
        executarMigracoes(config);

        Map<String, String> props = new HashMap<>();
        props.put("javax.persistence.jdbc.url", config.url);
        props.put("javax.persistence.jdbc.driver", config.driver);
        props.put("javax.persistence.jdbc.user", config.user);
        props.put("javax.persistence.jdbc.password", config.password);
        props.put("hibernate.dialect", config.dialect);
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.show_sql", "false");
        props.put("hibernate.format_sql", "false");

        emf = Persistence.createEntityManagerFactory("barracaPU", props);
    }

    /**
     * Cria um EntityManager e injeta o ID do utilizador actual no contexto PostgreSQL
     * para que as políticas de Row Level Security (RLS) sejam aplicadas correctamente.
     *
     * Para H2 (desenvolvimento local), o set_config não é executado.
     */
    public static EntityManager getEntityManager() {
        if (emf == null || !emf.isOpen()) {
            inicializar();
        }
        EntityManager em = emf.createEntityManager();

        if (isPostgres) {
            Long userId = CURRENT_USER_ID.get();
            // Definir (ou limpar) o contexto do utilizador para esta ligação.
            // "" como valor faz com que bstore_current_user_id() retorne NULL
            // e as políticas RLS bloqueiem acesso a dados de outros utilizadores.
            String uid = userId != null ? userId.toString() : "";
            em.createNativeQuery("SELECT set_config('app.current_user_id', :uid, false)")
                .setParameter("uid", uid)
                .getSingleResult();
        }

        return em;
    }

    public static <T> T emTransacao(Long usuarioId, java.util.function.Function<EntityManager, T> operacao) {
        if (emf == null || !emf.isOpen()) inicializar();
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            if (isPostgres) {
                em.createNativeQuery("SELECT set_config('app.current_user_id', :uid, true)")
                    .setParameter("uid", usuarioId == null ? "" : usuarioId.toString())
                    .getSingleResult();
            }
            T resultado = operacao.apply(em);
            em.getTransaction().commit();
            return resultado;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public static synchronized void fechar() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    private static DatabaseConfig databaseConfig() {
        String rawUrl = System.getenv("DATABASE_URL");
        String user = emptyToNull(System.getenv("DB_USER"));
        String password = emptyToNull(System.getenv("DB_PASSWORD"));

        if (rawUrl == null || rawUrl.isBlank()) {
            System.out.println("[DB] Usando banco H2 local.");
            return new DatabaseConfig(
                "jdbc:h2:./barraca-db;AUTO_SERVER=TRUE",
                "org.h2.Driver",
                "sa",
                "",
                "org.hibernate.dialect.H2Dialect",
                "classpath:db/migration/h2"
            );
        }

        if (rawUrl.startsWith("jdbc:h2:")) {
            System.out.println("[DB] Usando banco H2 configurado.");
            return new DatabaseConfig(
                rawUrl,
                "org.h2.Driver",
                user != null ? user : "sa",
                password != null ? password : "",
                "org.hibernate.dialect.H2Dialect",
                "classpath:db/migration/h2"
            );
        }

        String jdbcUrl = postgresJdbcUrl(rawUrl);
        if (!jdbcUrl.contains("sslmode") && !jdbcUrl.contains("ssl=") && !jdbcUrl.contains(".internal")) {
            jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
        }
        // Supabase connection pooler não suporta prepared statements do lado do servidor
        if (!jdbcUrl.contains("prepareThreshold") && (jdbcUrl.contains("pooler.supabase") || jdbcUrl.contains(":6543"))) {
            jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "prepareThreshold=0";
        }
        if (user == null || password == null) {
            Credentials credentials = credentialsFromUrl(rawUrl);
            if (user == null) user = credentials.user;
            if (password == null) password = credentials.password;
        }
        if (user == null) {
            throw new IllegalStateException("DB_USER é obrigatório para PostgreSQL quando DATABASE_URL não contém utilizador.");
        }

        System.out.println("[DB] Conectando ao PostgreSQL: " + jdbcUrl.replaceAll("password=[^&]*", "password=***"));
        return new DatabaseConfig(
            jdbcUrl,
            "org.postgresql.Driver",
            user,
            password != null ? password : "",
            "org.hibernate.dialect.PostgreSQLDialect",
            "classpath:db/migration/postgresql"
        );
    }

    private static void executarMigracoes(DatabaseConfig config) {
        Flyway.configure()
            .dataSource(config.url, config.user, config.password)
            .locations(config.migrationLocation)
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load()
            .migrate();
    }

    private static String postgresJdbcUrl(String rawUrl) {
        if (rawUrl.startsWith("jdbc:postgresql:")) {
            return rawUrl;
        }
        if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
            String value = rawUrl.startsWith("postgresql://")
                ? rawUrl.substring("postgresql://".length())
                : rawUrl.substring("postgres://".length());
            int at = value.indexOf('@');
            return "jdbc:postgresql://" + (at >= 0 ? value.substring(at + 1) : value);
        }
        throw new IllegalStateException("DATABASE_URL deve usar jdbc:postgresql:, postgres://, postgresql:// ou jdbc:h2:.");
    }

    private static Credentials credentialsFromUrl(String rawUrl) {
        if (!(rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://"))) {
            return new Credentials(null, null);
        }
        String value = rawUrl.substring(rawUrl.indexOf("://") + 3);
        int at = value.indexOf('@');
        if (at < 0) {
            return new Credentials(null, null);
        }
        String userInfo = value.substring(0, at);
        int colon = userInfo.indexOf(':');
        return colon < 0
            ? new Credentials(userInfo, null)
            : new Credentials(userInfo.substring(0, colon), userInfo.substring(colon + 1));
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class DatabaseConfig {
        private final String url;
        private final String driver;
        private final String user;
        private final String password;
        private final String dialect;
        private final String migrationLocation;

        private DatabaseConfig(String url, String driver, String user, String password, String dialect, String migrationLocation) {
            this.url = url;
            this.driver = driver;
            this.user = user;
            this.password = password;
            this.dialect = dialect;
            this.migrationLocation = migrationLocation;
        }
    }

    private static final class Credentials {
        private final String user;
        private final String password;

        private Credentials(String user, String password) {
            this.user = user;
            this.password = password;
        }
    }
}
