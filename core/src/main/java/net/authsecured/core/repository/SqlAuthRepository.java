package net.authsecured.core.repository;

import net.authsecured.core.db.DatabaseService;
import net.authsecured.core.model.UserAccount;
import net.authsecured.core.port.AuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async SQL implementation of AuthRepository supporting non-blocking JDBC execution.
 */
public final class SqlAuthRepository implements AuthRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlAuthRepository.class);
    private final DatabaseService databaseService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public SqlAuthRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM auth_users WHERE uuid = ?";
            try (Connection conn = databaseService.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to check registration status for UUID: {}", uuid, e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UserAccount>> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, username, password_hash, hashed_ip, registration_date, last_login FROM auth_users WHERE uuid = ?";
            try (Connection conn = databaseService.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to find user by UUID: {}", uuid, e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<UserAccount>> findByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, username, password_hash, hashed_ip, registration_date, last_login FROM auth_users WHERE LOWER(username) = LOWER(?)";
            try (Connection conn = databaseService.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapUser(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Failed to find user by username: {}", username, e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> save(UserAccount account) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO auth_users (uuid, username, password_hash, hashed_ip, registration_date, last_login) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "username = EXCLUDED.username, " +
                    "password_hash = EXCLUDED.password_hash, " +
                    "hashed_ip = EXCLUDED.hashed_ip, " +
                    "last_login = EXCLUDED.last_login";
            try (Connection conn = databaseService.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, account.uuid().toString());
                ps.setString(2, account.username());
                ps.setString(3, account.passwordHash());
                ps.setString(4, account.hashedIp());
                ps.setTimestamp(5, Timestamp.from(account.registrationDate()));
                ps.setTimestamp(6, Timestamp.from(account.lastLoginDate()));
                ps.executeUpdate();
            } catch (SQLException e) {
                executeUpsertFallback(account);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> update(UserAccount account) {
        return save(account);
    }

    private void executeUpsertFallback(UserAccount account) {
        String updateSql = "UPDATE auth_users SET username = ?, password_hash = ?, hashed_ip = ?, last_login = ? WHERE uuid = ?";
        String insertSql = "INSERT INTO auth_users (uuid, username, password_hash, hashed_ip, registration_date, last_login) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseService.getConnection()) {
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setString(1, account.username());
                updatePs.setString(2, account.passwordHash());
                updatePs.setString(3, account.hashedIp());
                updatePs.setTimestamp(4, Timestamp.from(account.lastLoginDate()));
                updatePs.setString(5, account.uuid().toString());
                int rows = updatePs.executeUpdate();
                if (rows > 0) return;
            }
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                insertPs.setString(1, account.uuid().toString());
                insertPs.setString(2, account.username());
                insertPs.setString(3, account.passwordHash());
                insertPs.setString(4, account.hashedIp());
                insertPs.setTimestamp(5, Timestamp.from(account.registrationDate()));
                insertPs.setTimestamp(6, Timestamp.from(account.lastLoginDate()));
                insertPs.executeUpdate();
            }
        } catch (SQLException ex) {
            LOGGER.error("Failed to save user account for UUID: {}", account.uuid(), ex);
        }
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM auth_users WHERE uuid = ?";
            try (Connection conn = databaseService.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                int affected = ps.executeUpdate();
                return affected > 0;
            } catch (SQLException e) {
                LOGGER.error("Failed to delete user account for UUID: {}", uuid, e);
                return false;
            }
        }, executor);
    }

    private UserAccount mapUser(ResultSet rs) throws SQLException {
        return new UserAccount(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("hashed_ip"),
                rs.getTimestamp("registration_date").toInstant(),
                rs.getTimestamp("last_login").toInstant()
        );
    }

    public void shutdown() {
        executor.shutdown();
    }
}
