package net.authsecured.core.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.authsecured.core.model.UserSession;
import net.authsecured.core.port.SessionStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Distributed Redis implementation of SessionStorage using Jedis.
 */
public final class RedisSessionStorage implements SessionStorage, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSessionStorage.class);
    private static final String KEY_PREFIX = "authsecured:session:";

    private final JedisPool jedisPool;
    private final Gson gson;
    private final ExecutorService executor;

    public RedisSessionStorage(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
                @Override
                public void write(JsonWriter out, Instant value) throws IOException {
                    out.value(value != null ? value.toString() : null);
                }

                @Override
                public Instant read(JsonReader in) throws IOException {
                    String str = in.nextString();
                    return str != null ? Instant.parse(str) : null;
                }
            })
            .create();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public CompletableFuture<Void> saveSession(UserSession session) {
        return CompletableFuture.runAsync(() -> {
            String key = KEY_PREFIX + session.uuid();
            String json = gson.toJson(session);
            long secondsUntilExpiration = Math.max(1, Duration.between(Instant.now(), session.expiresAt()).getSeconds());

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(key, secondsUntilExpiration, json);
            } catch (Exception e) {
                LOGGER.error("Failed to save session to Redis for UUID: {}", session.uuid(), e);
                throw e;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UserSession>> getSession(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String key = KEY_PREFIX + uuid;
            try (Jedis jedis = jedisPool.getResource()) {
                String json = jedis.get(key);
                if (json == null || json.isBlank()) {
                    return Optional.empty();
                }
                UserSession session = gson.fromJson(json, UserSession.class);
                if (session == null || session.isExpired()) {
                    jedis.del(key);
                    return Optional.empty();
                }
                return Optional.of(session);
            } catch (Exception e) {
                LOGGER.error("Failed to get session from Redis for UUID: {}", uuid, e);
                throw e;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> invalidateSession(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            String key = KEY_PREFIX + uuid;
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                LOGGER.error("Failed to invalidate session in Redis for UUID: {}", uuid, e);
                throw e;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeExpired() {
        // Redis key TTL handles automatic expiration
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
