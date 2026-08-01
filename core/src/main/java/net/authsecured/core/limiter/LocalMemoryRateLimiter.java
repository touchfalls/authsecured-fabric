package net.authsecured.core.limiter;

import net.authsecured.core.model.RateLimitResult;
import net.authsecured.core.port.RateLimiter;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory RateLimiter implementation with configurable attempt limits and lockout window.
 */
public final class LocalMemoryRateLimiter implements RateLimiter {

    private final int maxAttempts;
    private final long lockoutSeconds;
    private final Map<String, AttemptData> attempts = new ConcurrentHashMap<>();

    public LocalMemoryRateLimiter(int maxAttempts, long lockoutSeconds) {
        this.maxAttempts = maxAttempts;
        this.lockoutSeconds = lockoutSeconds;
    }

    @Override
    public CompletableFuture<RateLimitResult> checkLimit(String key) {
        AttemptData data = attempts.get(key);
        if (data == null) {
            return CompletableFuture.completedFuture(RateLimitResult.allow(maxAttempts));
        }

        Instant now = Instant.now();
        if (data.isLockedOut(now)) {
            long remainingLockout = data.lockoutUntil().getEpochSecond() - now.getEpochSecond();
            return CompletableFuture.completedFuture(RateLimitResult.deny(Math.max(1, remainingLockout)));
        }

        if (data.isExpired(now, lockoutSeconds)) {
            attempts.remove(key);
            return CompletableFuture.completedFuture(RateLimitResult.allow(maxAttempts));
        }

        int remaining = Math.max(0, maxAttempts - data.count());
        return CompletableFuture.completedFuture(RateLimitResult.allow(remaining));
    }

    @Override
    public CompletableFuture<Void> recordFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, old) -> {
            if (old == null || old.isExpired(now, lockoutSeconds)) {
                return new AttemptData(1, now, null);
            }
            int newCount = old.count() + 1;
            Instant lockoutUntil = (newCount >= maxAttempts) ? now.plusSeconds(lockoutSeconds) : null;
            return new AttemptData(newCount, now, lockoutUntil);
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        attempts.remove(key);
        return CompletableFuture.completedFuture(null);
    }

    private record AttemptData(int count, Instant lastAttempt, Instant lockoutUntil) {
        boolean isLockedOut(Instant now) {
            return lockoutUntil != null && now.isBefore(lockoutUntil);
        }

        boolean isExpired(Instant now, long windowSeconds) {
            return lockoutUntil == null && now.isAfter(lastAttempt.plusSeconds(windowSeconds));
        }
    }
}
