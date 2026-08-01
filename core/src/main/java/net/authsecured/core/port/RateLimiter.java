package net.authsecured.core.port;

import net.authsecured.core.model.RateLimitResult;

import java.util.concurrent.CompletableFuture;

/**
 * Port for authentication rate-limiting / brute-force attack prevention.
 */
public interface RateLimiter {

    /**
     * Evaluates rate limiting parameters for a given key (IP or Username).
     *
     * @param key Client IP address or player username key.
     * @return CompletableFuture with RateLimitResult decision.
     */
    CompletableFuture<RateLimitResult> checkLimit(String key);

    /**
     * Records a failed authentication attempt for a key.
     *
     * @param key Client IP address or player username key.
     */
    CompletableFuture<Void> recordFailure(String key);

    /**
     * Resets failure counters upon successful authentication.
     *
     * @param key Client IP address or player username key.
     */
    CompletableFuture<Void> reset(String key);
}
