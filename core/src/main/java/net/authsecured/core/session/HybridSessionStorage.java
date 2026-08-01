package net.authsecured.core.session;

import net.authsecured.core.model.UserSession;
import net.authsecured.core.port.SessionStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hybrid Redis and ConcurrentHashMap session storage implementation with automatic fallback.
 */
public final class HybridSessionStorage implements SessionStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridSessionStorage.class);
    private final JedisPool jedisPool;
    private final Map<UUID, UserSession> localCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public HybridSessionStorage(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public HybridSessionStorage() {
        this(null);
    }

    @Override
    public CompletableFuture<Void> saveSession(UserSession session) {
        return CompletableFuture.runAsync(() -> {
            localCache.put(session.uuid(), session);
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String key = "session:" + session.uuid();
                    jedis.setex(key, 86400, session.username() + ":" + session.hashedIp());
                } catch (Exception e) {
                    LOGGER.warn("Failed to persist session to Redis for UUID: {}. Retaining in-memory cache.", session.uuid(), e);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UserSession>> getSession(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            UserSession local = localCache.get(uuid);
            if (local != null) {
                if (Instant.now().isBefore(local.expiresAt())) {
                    return Optional.of(local);
                } else {
                    localCache.remove(uuid);
                }
            }

            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String key = "session:" + uuid;
                    String val = jedis.get(key);
                    if (val != null && val.contains(":")) {
                        String[] parts = val.split(":", 2);
                        UserSession restored = new UserSession(uuid, parts[0], parts[1], Instant.now(), Instant.now().plusSeconds(86400));
                        localCache.put(uuid, restored);
                        return Optional.of(restored);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to read session from Redis for UUID: {}", uuid, e);
                }
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> invalidateSession(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            localCache.remove(uuid);
            if (jedisPool != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.del("session:" + uuid);
                } catch (Exception e) {
                    LOGGER.warn("Failed to remove session from Redis for UUID: {}", uuid, e);
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeExpired() {
        return CompletableFuture.runAsync(() -> {
            Instant now = Instant.now();
            localCache.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
