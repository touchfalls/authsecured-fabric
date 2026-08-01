package net.authsecured.core.model;

/**
 * Result of a rate limiting evaluation.
 */
public record RateLimitResult(
    boolean allowed,
    int remainingAttempts,
    long retryAfterSeconds
) {
    public static RateLimitResult allow(int remaining) {
        return new RateLimitResult(true, remaining, 0);
    }

    public static RateLimitResult deny(long retryAfterSeconds) {
        return new RateLimitResult(false, 0, retryAfterSeconds);
    }
}
