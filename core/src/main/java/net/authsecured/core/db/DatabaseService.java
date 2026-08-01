package net.authsecured.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.authsecured.core.config.DatabaseConfig;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.File;

/**
 * Enterprise Database service managing HikariCP connection pools and Flyway schema migrations.
 */
public final class DatabaseService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseService.class);

    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    public DatabaseService(DatabaseConfig config) {
        this.config = config;
    }

    public void initialize() {
        if (config.type() == DatabaseConfig.DatabaseType.SQLITE) {
            File dbFile = new File(config.sqlitePath());
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.type() == DatabaseConfig.DatabaseType.SQLITE ? 1 : config.maximumPoolSize());
        hikariConfig.setConnectionTimeout(config.connectionTimeoutMs());
        hikariConfig.setPoolName("AuthSecured-HikariPool");

        if (config.type() == DatabaseConfig.DatabaseType.SQLITE) {
            hikariConfig.addDataSourceProperty("journal_mode", "WAL");
            hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
            hikariConfig.addDataSourceProperty("busy_timeout", "5000");
        }

        this.dataSource = new HikariDataSource(hikariConfig);
        LOGGER.info("HikariCP connection pool initialized for {} database.", config.type());

        runFlywayMigrations();
    }

    private void runFlywayMigrations() {
        LOGGER.info("Executing Flyway database schema migrations for {}...", config.type());
        String location = switch (config.type()) {
            case SQLITE -> "classpath:db/migration/sqlite";
            case POSTGRESQL -> "classpath:db/migration/postgresql";
        };

        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(location)
            .baselineOnMigrate(true)
            .load();

        flyway.migrate();
        LOGGER.info("Flyway database migrations applied successfully.");
    }

    public DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("DatabaseService has not been initialized!");
        }
        return dataSource;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LOGGER.info("HikariCP connection pool closed successfully.");
        }
    }
}
