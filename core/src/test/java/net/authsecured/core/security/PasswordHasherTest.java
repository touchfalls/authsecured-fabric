package net.authsecured.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
    }

    @Test
    @DisplayName("Password hashing produces valid hash and wipes input array")
    void testHashAndWipe() {
        char[] password = {'S', 'e', 'c', 'u', 'r', 'e', 'P', 'a', 's', 's', '1', '2', '3'};
        String hash = passwordHasher.hash(password);

        assertNotNull(hash);
        assertFalse(hash.isBlank());
        
        // Assert that memory wiping occurred
        for (char c : password) {
            assertEquals('\0', c, "Password char array must be zero-filled in memory");
        }
    }

    @Test
    @DisplayName("Password verification works and wipes input array")
    void testVerifyAndWipe() {
        char[] originalPassword = {'M', 'y', 'S', 'e', 'c', 'r', 'e', 't', 'P', 'a', 's', 's'};
        String hash = passwordHasher.hash(originalPassword);

        char[] verifyPassword = {'M', 'y', 'S', 'e', 'c', 'r', 'e', 't', 'P', 'a', 's', 's'};
        boolean match = passwordHasher.verify(verifyPassword, hash);

        assertTrue(match);
        for (char c : verifyPassword) {
            assertEquals('\0', c, "Verification char array must be zero-filled in memory");
        }

        char[] wrongPassword = {'W', 'r', 'o', 'n', 'g', 'P', 'a', 's', 's'};
        boolean wrongMatch = passwordHasher.verify(wrongPassword, hash);

        assertFalse(wrongMatch);
        for (char c : wrongPassword) {
            assertEquals('\0', c, "Wrong password char array must be zero-filled in memory");
        }
    }
}
