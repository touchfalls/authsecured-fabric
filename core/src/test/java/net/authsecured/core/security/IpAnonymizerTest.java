package net.authsecured.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpAnonymizerTest {

    private IpAnonymizer anonymizer;

    @BeforeEach
    void setUp() {
        anonymizer = new IpAnonymizer("secret-gdpr-key-12345");
    }

    @Test
    @DisplayName("HMAC-SHA256 anonymizes IP deterministically into 64-char hex string")
    void testAnonymizeIp() {
        String ip1 = "192.168.1.100";
        String hashed1 = anonymizer.anonymize(ip1);
        String hashed2 = anonymizer.anonymize(ip1);

        assertNotNull(hashed1);
        assertEquals(64, hashed1.length(), "HMAC-SHA256 hex output must be 64 characters long");
        assertEquals(hashed1, hashed2, "Identical IPs must produce identical HMAC hashes");

        String ip2 = "192.168.1.101";
        String hashedDifferent = anonymizer.anonymize(ip2);
        assertNotEquals(hashed1, hashedDifferent, "Different IPs must produce different HMAC hashes");
    }
}
