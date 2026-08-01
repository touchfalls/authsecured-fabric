package net.authsecured.core.port;

import net.authsecured.core.model.UserSession;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Secondary port for user session persistence (Redis distributed / local memory).
 */
public interface SessionStorage {

    /**
     * Saves an active session.
     *
     * @param session Active session instance.
     */
    CompletableFuture<Void> saveSession(UserSession session);

    /**
     * Retrieves an active session by player UUID.
     *
     * @param uuid Player UUID.
     * @return CompletableFuture containing Optional UserSession.
     */
    CompletableFuture<Optional<UserSession>> getSession(UUID uuid);

    /**
     * Invalidates an active session by player UUID.
     *
     * @param uuid Player UUID.
     */
    CompletableFuture<Void> invalidateSession(UUID uuid);

    /**
     * Purges expired sessions from the storage store.
     */
    CompletableFuture<Void> purgeExpired();
}
