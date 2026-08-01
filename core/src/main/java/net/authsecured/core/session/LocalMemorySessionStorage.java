package net.authsecured.core.session;

import net.authsecured.core.model.UserSession;
import net.authsecured.core.port.SessionStorage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance local in-memory implementation of SessionStorage using ConcurrentHashMap.
 */
public final class LocalMemorySessionStorage implements SessionStorage {

    private final Map<UUID, UserSession> sessions = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> saveSession(UserSession session) {
        sessions.put(session.uuid(), session);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<UserSession>> getSession(UUID uuid) {
        UserSession session = sessions.get(uuid);
        if (session != null && session.isExpired()) {
            sessions.remove(uuid);
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(session));
    }

    @Override
    public CompletableFuture<Void> invalidateSession(UUID uuid) {
        sessions.remove(uuid);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> purgeExpired() {
        sessions.values().removeIf(UserSession::isExpired);
        return CompletableFuture.completedFuture(null);
    }
}
