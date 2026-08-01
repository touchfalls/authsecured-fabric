package net.authsecured.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing an active authenticated session.
 */
public record UserSession(
    UUID uuid,
    String username,
    String hashedIp,
    Instant createdAt,
    Instant expiresAt
) {
    public UserSession {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
