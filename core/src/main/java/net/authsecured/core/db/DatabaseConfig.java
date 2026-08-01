package net.authsecured.core.db;

/**
 * Immutable database configuration properties for SQLite and PostgreSQL backends.
 */
public record DatabaseConfig(
    String driverType, // "sqlite" or "postgresql"
    String host,
    int port,
    String database,
    String username,
    String password,
    String sqliteFilePath,
    int maxPoolSize,
    int minIdle,
    long connectionTimeoutMs
) {
    public static DatabaseConfig defaultSqlite(String filePath) {
        return new DatabaseConfig(
            "sqlite",
            "localhost",
            5432,
            "authsecured",
            "",
            "",
            filePath,
            10,
            2,
            30000L
        );
    }
}
