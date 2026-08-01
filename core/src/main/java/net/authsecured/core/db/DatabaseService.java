package net.authsecured.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Core Database Service managing HikariCP connection pools, Flyway migrations,
 * and automatic fallback from PostgreSQL to local SQLite storage on connection failure.
 */
public final class DatabaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public DatabaseService(DatabaseConfig config) {
        this.config = config;
    }

    public synchronized void initialize() {
        if ("postgresql".equalsIgnoreCase(config.driverType())) {
            try {
                LOGGER.info("Attempting connection to PostgreSQL database at {}:{}...", config.host(), config.port());
                this.dataSource = createPostgresDataSource(config);
                runMigrations("postgresql");
                LOGGER.info("PostgreSQL database initialized successfully.");
                return;
            } catch (Exception e) {
                LOGGER.warn("Failed to connect/initialize PostgreSQL. Falling back to local SQLite storage!", e);
            }
        }

        LOGGER.info("Initializing local SQLite database at: {}", config.sqliteFilePath());
        this.dataSource = createSqliteDataSource(config);
        runMigrations("sqlite");
        LOGGER.info("SQLite database initialized successfully.");
    }

    private HikariDataSource createPostgresDataSource(DatabaseConfig cfg) {
        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName("org.postgresql.Driver");
        hikari.setJdbcUrl("jdbc:postgresql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.database());
        hikari.setUsername(cfg.username());
        hikari.setPassword(cfg.password());
        hikari.setMaximumPoolSize(cfg.maxPoolSize());
        hikari.setMinimumIdle(cfg.minIdle());
        hikari.setConnectionTimeout(cfg.connectionTimeoutMs());
        hikari.setPoolName("AuthSecured-Postgres");
        return new HikariDataSource(hikari);
    }

    private HikariDataSource createSqliteDataSource(DatabaseConfig cfg) {
        File file = new File(cfg.sqliteFilePath());
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName("org.sqlite.JDBC");
        hikari.setJdbcUrl("jdbc:sqlite:" + cfg.sqliteFilePath());
        hikari.setMaximumPoolSize(1);
        hikari.setPoolName("AuthSecured-SQLite");
        return new HikariDataSource(hikari);
    }

    private void runMigrations(String dbType) {
        try {
            LOGGER.info("Executing Flyway database migrations for {}...", dbType);
            Flyway flyway = Flyway.configure()
                .dataSource(this.dataSource)
                .locations("db/migration")
                .baselineOnMigrate(true)
                .load();
            flyway.migrate();
        } catch (Exception e) {
            LOGGER.error("Flyway migration error!", e);
            throw new RuntimeException("Database migration failed", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DatabaseService is not initialized or pool is closed.");
        }
        return dataSource.getConnection();
    }

    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("Database connection pool closed.");
        }
    }
}
