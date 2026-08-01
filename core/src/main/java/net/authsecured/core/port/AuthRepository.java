package net.authsecured.core.port;

import net.authsecured.core.model.UserAccount;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Secondary port for user account persistence (SQLite / PostgreSQL).
 */
public interface AuthRepository {

    /**
     * Finds a user account by player UUID.
     *
     * @param uuid Player UUID.
     * @return CompletableFuture containing Optional UserAccount.
     */
    CompletableFuture<Optional<UserAccount>> findByUuid(UUID uuid);

    /**
     * Finds a user account by case-insensitive username.
     *
     * @param username Player username.
     * @return CompletableFuture containing Optional UserAccount.
     */
    CompletableFuture<Optional<UserAccount>> findByUsername(String username);

    /**
     * Saves a new user account.
     *
     * @param account User account entity to persist.
     */
    CompletableFuture<Void> save(UserAccount account);

    /**
     * Updates an existing user account's password hash and last login metadata.
     *
     * @param account Updated user account entity.
     */
    CompletableFuture<Void> update(UserAccount account);

    /**
     * Deletes a user account by player UUID.
     *
     * @param uuid Player UUID.
     * @return CompletableFuture containing true if deleted.
     */
    CompletableFuture<Boolean> delete(UUID uuid);

    /**
     * Checks if a user is registered by UUID.
     *
     * @param uuid Player UUID.
     * @return CompletableFuture containing true if registered.
     */
    CompletableFuture<Boolean> isRegistered(UUID uuid);
}
