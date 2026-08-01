package net.authsecured.core.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * GDPR-compliant IP Anonymizer using HMAC-SHA256.
 */
public final class IpAnonymizer {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecretKeySpec secretKeySpec;

    public IpAnonymizer(String secretKey) {
        Objects.requireNonNull(secretKey, "Secret key cannot be null");
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.secretKeySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    /**
     * Hashes an IP address using HMAC-SHA256 for GDPR compliance.
     *
     * @param ipAddress Plaintext IP address (e.g. 192.168.1.1 or 2001:db8::1).
     * @return Hex-encoded HMAC-SHA256 string.
     */
    public String anonymize(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(ipAddress.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to anonymize IP address", e);
        }
    }
}
