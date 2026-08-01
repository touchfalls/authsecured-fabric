package net.authsecured.core.repository;

import net.authsecured.core.model.UserAccount;
import net.authsecured.core.port.AuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enterprise Asynchronous SQL implementation of AuthRepository (SQLite / PostgreSQL).
 */
public final class SqlAuthRepository implements AuthRepository, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlAuthRepository.class);

    private final DataSource dataSource;
    private final ExecutorService executor;

    public SqlAuthRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public CompletableFuture<Optional<UserAccount>> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, username, password_hash, hashed_ip, registration_date, last_login_date FROM auth_users WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to find user by UUID: {}", uuid, e);
                throw new RuntimeException("Database error during findByUuid", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UserAccount>> findByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, username, password_hash, hashed_ip, registration_date, last_login_date FROM auth_users WHERE LOWER(username) = LOWER(?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to find user by username: {}", username, e);
                throw new RuntimeException("Database error during findByUsername", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(UserAccount account) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO auth_users (uuid, username, password_hash, hashed_ip, registration_date, last_login_date) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, account.uuid().toString());
                ps.setString(2, account.username());
                ps.setString(3, account.passwordHash());
                ps.setString(4, account.hashedIp());
                ps.setTimestamp(5, Timestamp.from(account.registrationDate()));
                ps.setTimestamp(6, Timestamp.from(account.lastLoginDate()));
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to save user account: {}", account.uuid(), e);
                throw new RuntimeException("Database error during save", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> update(UserAccount account) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE auth_users SET username = ?, password_hash = ?, hashed_ip = ?, last_login_date = ? WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, account.username());
                ps.setString(2, account.passwordHash());
                ps.setString(3, account.hashedIp());
                ps.setTimestamp(4, Timestamp.from(account.lastLoginDate()));
                ps.setString(5, account.uuid().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to update user account: {}", account.uuid(), e);
                throw new RuntimeException("Database error during update", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM auth_users WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                int rows = ps.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                LOGGER.error("Failed to delete user account: {}", uuid, e);
                throw new RuntimeException("Database error during delete", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM auth_users WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to check registration status for UUID: {}", uuid, e);
                throw new RuntimeException("Database error during isRegistered", e);
            }
        }, executor);
    }

    private UserAccount mapResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String hashedIp = rs.getString("hashed_ip");
        Instant registrationDate = rs.getTimestamp("registration_date").toInstant();
        Instant lastLoginDate = rs.getTimestamp("last_login_date").toInstant();

        return new UserAccount(uuid, username, passwordHash, hashedIp, registrationDate, lastLoginDate);
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
