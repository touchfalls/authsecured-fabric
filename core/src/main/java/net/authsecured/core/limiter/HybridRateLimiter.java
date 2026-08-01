package net.authsecured.core.limiter;

import net.authsecured.core.model.RateLimitResult;
import net.authsecured.core.port.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Hybrid RateLimiter managing Redis primary rate limiter with automatic local memory fallback.
 */
public final class HybridRateLimiter implements RateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridRateLimiter.class);

    private final RateLimiter redisLimiter;
    private final LocalMemoryRateLimiter localLimiter;

    public HybridRateLimiter(RateLimiter redisLimiter, LocalMemoryRateLimiter localLimiter) {
        this.redisLimiter = redisLimiter;
        this.localLimiter = localLimiter;
    }

    @Override
    public CompletableFuture<RateLimitResult> checkLimit(String key) {
        if (redisLimiter != null) {
            return redisLimiter.checkLimit(key).exceptionally(ex -> {
                LOGGER.warn("Redis error on checkLimit, falling back to local rate limiter: {}", ex.getMessage());
                return localLimiter.checkLimit(key).join();
            });
        }
        return localLimiter.checkLimit(key);
    }

    @Override
    public CompletableFuture<Void> recordFailure(String key) {
        localLimiter.recordFailure(key);
        if (redisLimiter != null) {
            return redisLimiter.recordFailure(key).exceptionally(ex -> {
                LOGGER.warn("Redis error on recordFailure, falling back to local rate limiter: {}", ex.getMessage());
                return null;
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        localLimiter.reset(key);
        if (redisLimiter != null) {
            return redisLimiter.reset(key).exceptionally(ex -> {
                LOGGER.warn("Redis error on reset, falling back to local rate limiter: {}", ex.getMessage());
                return null;
            });
        }
        return CompletableFuture.completedFuture(null);
    }
}
