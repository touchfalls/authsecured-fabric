package net.authsecured.core.limiter;

import net.authsecured.core.model.RateLimitResult;
import net.authsecured.core.port.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Distributed Redis implementation of RateLimiter using Jedis INCR and EXPIRE.
 */
public final class RedisRateLimiter implements RateLimiter, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "authsecured:ratelimit:";

    private final JedisPool jedisPool;
    private final int maxAttempts;
    private final long lockoutSeconds;
    private final ExecutorService executor;

    public RedisRateLimiter(JedisPool jedisPool, int maxAttempts, long lockoutSeconds) {
        this.jedisPool = jedisPool;
        this.maxAttempts = maxAttempts;
        this.lockoutSeconds = lockoutSeconds;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public CompletableFuture<RateLimitResult> checkLimit(String key) {
        return CompletableFuture.supplyAsync(() -> {
            String redisKey = KEY_PREFIX + key;
            try (Jedis jedis = jedisPool.getResource()) {
                String val = jedis.get(redisKey);
                if (val == null) {
                    return RateLimitResult.allow(maxAttempts);
                }
                int count = Integer.parseInt(val);
                if (count >= maxAttempts) {
                    long ttl = jedis.ttl(redisKey);
                    return RateLimitResult.deny(Math.max(1, ttl));
                }
                return RateLimitResult.allow(maxAttempts - count);
            } catch (Exception e) {
                LOGGER.error("Redis rate-limiting error for key: {}", key, e);
                throw e;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> recordFailure(String key) {
        return CompletableFuture.runAsync(() -> {
            String redisKey = KEY_PREFIX + key;
            try (Jedis jedis = jedisPool.getResource()) {
                long current = jedis.incr(redisKey);
                if (current == 1) {
                    jedis.expire(redisKey, lockoutSeconds);
                }
            } catch (Exception e) {
                LOGGER.error("Redis recordFailure error for key: {}", key, e);
                throw e;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        return CompletableFuture.runAsync(() -> {
            String redisKey = KEY_PREFIX + key;
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(redisKey);
            } catch (Exception e) {
                LOGGER.error("Redis reset error for key: {}", key, e);
                throw e;
            }
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
