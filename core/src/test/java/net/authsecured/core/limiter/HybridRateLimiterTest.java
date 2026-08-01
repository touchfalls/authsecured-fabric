package net.authsecured.core.limiter;

import net.authsecured.core.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HybridRateLimiterTest {

    private HybridRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        LocalMemoryRateLimiter localLimiter = new LocalMemoryRateLimiter(3, 60);
        // Null redis limiter simulates disabled/offline Redis
        rateLimiter = new HybridRateLimiter(null, localLimiter);
    }

    @Test
    @DisplayName("HybridRateLimiter locks out after max attempts with local fallback")
    void testRateLimitLockout() throws Exception {
        String userKey = "TestUserIP";

        RateLimitResult check1 = rateLimiter.checkLimit(userKey).get();
        assertTrue(check1.allowed());
        assertEquals(3, check1.remainingAttempts());

        rateLimiter.recordFailure(userKey).get();
        rateLimiter.recordFailure(userKey).get();
        rateLimiter.recordFailure(userKey).get();

        RateLimitResult checkBlocked = rateLimiter.checkLimit(userKey).get();
        assertFalse(checkBlocked.allowed());
        assertTrue(checkBlocked.retryAfterSeconds() > 0);

        rateLimiter.reset(userKey).get();

        RateLimitResult checkAfterReset = rateLimiter.checkLimit(userKey).get();
        assertTrue(checkAfterReset.allowed());
    }
}
