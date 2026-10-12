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

    public static boolean isPostgres() {
        return isPostgres;
    }

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

        DatabaseConfig migrationConfig = migrationDatabaseConfig();
        executarMigracoes(migrationConfig);

        DatabaseConfig config = databaseConfig();
        isPostgres = config.driver.contains("postgresql");

        Map<String, String> props = new HashMap<>();
        props.put("javax.persistence.jdbc.url", config.url);
        props.put("javax.persistence.jdbc.driver", config.driver);
        props.put("javax.persistence.jdbc.user", config.user);
        props.put("javax.persistence.jdbc.password", config.password);
        props.put("hibernate.dialect", config.dialect);
        props.put("hibernate.jdbc.time_zone", "Africa/Maputo");
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.show_sql", "false");
        props.put("hibernate.format_sql", "false");
        props.put("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        props.put("hibernate.hikari.maximumPoolSize", getEnvOrProperty("DB_POOL_MAX_SIZE") != null ? getEnvOrProperty("DB_POOL_MAX_SIZE") : "10");
        props.put("hibernate.hikari.minimumIdle", getEnvOrProperty("DB_POOL_MIN_IDLE") != null ? getEnvOrProperty("DB_POOL_MIN_IDLE") : "2");
        props.put("hibernate.hikari.idleTimeout", "600000");
        props.put("hibernate.hikari.connectionTimeout", "30000");
        props.put("hibernate.hikari.maxLifetime", "1800000");

        emf = Persistence.createEntityManagerFactory("barracaPU", props);
    }

    /**
     * Cria um EntityManager para operações com a base de dados PostgreSQL.
     */
    public static EntityManager getEntityManager() {
        if (emf == null || !emf.isOpen()) {
            inicializar();
        }
        return emf.createEntityManager();
    }

    public static void aplicarContextoTransacao(EntityManager em, Long usuarioId) {
        if (isPostgres) {
            String uid = usuarioId != null ? usuarioId.toString() : (CURRENT_USER_ID.get() != null ? CURRENT_USER_ID.get().toString() : "");
            em.createNativeQuery("SELECT set_config('app.current_user_id', :uid, true)")
                .setParameter("uid", uid)
                .getSingleResult();
        }
    }

    public static <T> T emTransacao(Long usuarioId, java.util.function.Function<EntityManager, T> operacao) {
        if (emf == null || !emf.isOpen()) inicializar();
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            aplicarContextoTransacao(em, usuarioId);
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
        String rawUrl = getEnvOrProperty("DATABASE_URL");
        String user = emptyToNull(getEnvOrProperty("DB_USER"));
        String password = emptyToNull(getEnvOrProperty("DB_PASSWORD"));

        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException("DATABASE_URL não configurada no .env ou ambiente. O sistema exige conexão PostgreSQL.");
        }

        String jdbcUrl = postgresJdbcUrl(rawUrl);
        if (!jdbcUrl.contains("sslmode") && !jdbcUrl.contains("ssl=") && !jdbcUrl.contains(".internal") && !jdbcUrl.contains("localhost") && !jdbcUrl.contains("127.0.0.1")) {
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

        System.out.println("[DB] Conectando exclusivamente ao PostgreSQL: " + jdbcUrl.replaceAll(":[^:@]+@", ":***@").replaceAll("password=[^&]*", "password=***"));
        return new DatabaseConfig(
            jdbcUrl,
            "org.postgresql.Driver",
            user,
            password != null ? password : "",
            "org.hibernate.dialect.PostgreSQLDialect",
            "classpath:db/migration/postgresql"
        );
    }

    private static DatabaseConfig migrationDatabaseConfig() {
        String rawUrl = getEnvOrProperty("MIGRATION_DB_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            return databaseConfig();
        }
        String user = emptyToNull(getEnvOrProperty("MIGRATION_DB_USER"));
        String password = emptyToNull(getEnvOrProperty("MIGRATION_DB_PASSWORD"));

        String jdbcUrl = postgresJdbcUrl(rawUrl);
        if (!jdbcUrl.contains("sslmode") && !jdbcUrl.contains("ssl=") && !jdbcUrl.contains(".internal") && !jdbcUrl.contains("localhost") && !jdbcUrl.contains("127.0.0.1")) {
            jdbcUrl += (jdbcUrl.contains("?") ? "&" : "?") + "sslmode=require";
        }
        if (user == null || password == null) {
            Credentials credentials = credentialsFromUrl(rawUrl);
            if (user == null) user = credentials.user;
            if (password == null) password = credentials.password;
        }
        if (user == null) {
            user = "postgres";
        }
        System.out.println("[DB] Executando migrações como " + user + " em: " + jdbcUrl.replaceAll(":[^:@]+@", ":***@").replaceAll("password=[^&]*", "password=***"));
        return new DatabaseConfig(
            jdbcUrl,
            "org.postgresql.Driver",
            user,
            password != null ? password : "",
            "org.hibernate.dialect.PostgreSQLDialect",
            "classpath:db/migration/postgresql"
        );
    }

    private static String getEnvOrProperty(String key) {
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return EnvUtil.get(key);
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
        throw new IllegalStateException("DATABASE_URL deve ser uma URL PostgreSQL (ex: postgresql:// ou jdbc:postgresql:).");
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
