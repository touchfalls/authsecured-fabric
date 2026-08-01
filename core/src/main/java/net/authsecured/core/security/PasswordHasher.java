package net.authsecured.core.security;

import com.password4j.Argon2Function;
import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.types.Argon2;

import java.util.Arrays;
import java.util.Objects;

/**
 * Enterprise OWASP Argon2id Password Hashing engine with mandatory memory zeroing.
 */
public final class PasswordHasher {

    // OWASP Recommended Argon2id parameters (Memory: 64MB, Iterations: 3, Parallelism: 1)
    private static final int MEMORY_COST_KB = 65536;
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 1;
    private static final int HASH_LENGTH = 32;

    private final Argon2Function argon2Function;

    public PasswordHasher() {
        this.argon2Function = Argon2Function.getInstance(
            MEMORY_COST_KB,
            ITERATIONS,
            PARALLELISM,
            HASH_LENGTH,
            Argon2.ID
        );
    }

    /**
     * Hashes a raw password array using Argon2id and guarantees immediate memory zeroing.
     *
     * @param password Password char array (forcibly wiped after hashing).
     * @return Encoded Argon2id hash string.
     */
    public String hash(char[] password) {
        Objects.requireNonNull(password, "Password cannot be null");
        try {
            Hash hash = Password.hash(java.nio.CharBuffer.wrap(password))
                .addRandomSalt(16)
                .with(argon2Function);
            return hash.getResult();
        } finally {
            wipe(password);
        }
    }

    /**
     * Verifies a raw password against an Argon2id hash string and guarantees immediate memory zeroing.
     *
     * @param password    Password char array (forcibly wiped after verification).
     * @param encodedHash Stored Argon2id encoded hash string.
     * @return true if password matches, false otherwise.
     */
    public boolean verify(char[] password, String encodedHash) {
        Objects.requireNonNull(password, "Password cannot be null");
        Objects.requireNonNull(encodedHash, "Encoded hash cannot be null");
        try {
            return Password.check(java.nio.CharBuffer.wrap(password), encodedHash).with(argon2Function);
        } finally {
            wipe(password);
        }
    }

    /**
     * Securely zero-fills character arrays in memory to prevent RAM residual leaks.
     *
     * @param array Target char array.
     */
    public static void wipe(char[] array) {
        if (array != null) {
            Arrays.fill(array, '\0');
        }
    }
}
