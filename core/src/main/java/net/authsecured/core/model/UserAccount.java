package net.authsecured.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a user's authentication account.
 */
public record UserAccount(
    UUID uuid,
    String username,
    String passwordHash,
    String hashedIp,
    Instant registrationDate,
    Instant lastLoginDate
) {
    public UserAccount {
        Objects.requireNonNull(uuid, "UUID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(passwordHash, "Password hash cannot be null");
    }
}
