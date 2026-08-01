package net.authsecured.core.session;

import net.authsecured.core.model.UserSession;
import net.authsecured.core.port.SessionStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hybrid session storage managing Redis primary storage with automatic local memory fallback.
 */
public final class HybridSessionStorage implements SessionStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridSessionStorage.class);

    private final SessionStorage redisStorage;
    private final LocalMemorySessionStorage localStorage;

    public HybridSessionStorage(SessionStorage redisStorage, LocalMemorySessionStorage localStorage) {
        this.redisStorage = redisStorage;
        this.localStorage = localStorage;
    }

    @Override
    public CompletableFuture<Void> saveSession(UserSession session) {
        localStorage.saveSession(session);
        if (redisStorage != null) {
            return redisStorage.saveSession(session).exceptionally(ex -> {
                LOGGER.warn("Redis error on saveSession, falling back to local storage: {}", ex.getMessage());
                return null;
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<UserSession>> getSession(UUID uuid) {
        if (redisStorage != null) {
            return redisStorage.getSession(uuid).exceptionally(ex -> {
                LOGGER.warn("Redis error on getSession, falling back to local storage: {}", ex.getMessage());
                return localStorage.getSession(uuid).join();
            });
        }
        return localStorage.getSession(uuid);
    }

    @Override
    public CompletableFuture<Void> invalidateSession(UUID uuid) {
        localStorage.invalidateSession(uuid);
        if (redisStorage != null) {
            return redisStorage.invalidateSession(uuid).exceptionally(ex -> {
                LOGGER.warn("Redis error on invalidateSession, falling back to local storage: {}", ex.getMessage());
                return null;
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> purgeExpired() {
        localStorage.purgeExpired();
        if (redisStorage != null) {
            return redisStorage.purgeExpired().exceptionally(ex -> null);
        }
        return CompletableFuture.completedFuture(null);
    }
}
