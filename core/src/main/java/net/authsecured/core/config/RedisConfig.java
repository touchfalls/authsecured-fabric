package net.authsecured.core.config;

import net.authsecured.core.util.EnvResolver;

/**
 * Configuration for Redis distributed session storage and rate-limiting.
 */
public record RedisConfig(
    boolean enabled,
    String host,
    int port,
    String password,
    int timeoutMs,
    int maxConnections
) {
    public RedisConfig {
        host = EnvResolver.resolve(host);
        password = EnvResolver.resolve(password);
    }

    public static RedisConfig disabled() {
        return new RedisConfig(false, "localhost", 6379, "", 2000, 16);
    }
}
