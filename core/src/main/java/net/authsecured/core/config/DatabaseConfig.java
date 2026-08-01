package net.authsecured.core.config;

import net.authsecured.core.util.EnvResolver;

/**
 * Configuration model for SQLite and PostgreSQL database connection pools.
 */
public record DatabaseConfig(
    DatabaseType type,
    String host,
    int port,
    String database,
    String username,
    String password,
    String sqlitePath,
    int maximumPoolSize,
    long connectionTimeoutMs
) {
    public enum DatabaseType {
        SQLITE,
        POSTGRESQL
    }

    public DatabaseConfig {
        host = EnvResolver.resolve(host);
        database = EnvResolver.resolve(database);
        username = EnvResolver.resolve(username);
        password = EnvResolver.resolve(password);
        sqlitePath = EnvResolver.resolve(sqlitePath);
    }

    public String getJdbcUrl() {
        return switch (type) {
            case SQLITE -> "jdbc:sqlite:" + sqlitePath;
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        };
    }

    public String getDriverClassName() {
        return switch (type) {
            case SQLITE -> "org.sqlite.JDBC";
            case POSTGRESQL -> "org.postgresql.Driver";
        };
    }

    public static DatabaseConfig sqliteDefault(String path) {
        return new DatabaseConfig(
            DatabaseType.SQLITE,
            "localhost",
            5432,
            "authsecured",
            "",
            "",
            path,
            1,
            10000L
        );
    }
}
