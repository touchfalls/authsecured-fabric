package net.authsecured.core.repository;

import net.authsecured.core.config.DatabaseConfig;
import net.authsecured.core.db.DatabaseService;
import net.authsecured.core.model.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlAuthRepositoryTest {

    @TempDir
    File tempDir;

    private DatabaseService databaseService;
    private SqlAuthRepository repository;

    @BeforeEach
    void setUp() {
        File dbFile = new File(tempDir, "test_auth.db");
        DatabaseConfig config = DatabaseConfig.sqliteDefault(dbFile.getAbsolutePath());
        databaseService = new DatabaseService(config);
        databaseService.initialize();

        repository = new SqlAuthRepository(databaseService.getDataSource());
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
        if (databaseService != null) {
            databaseService.close();
        }
    }

    @Test
    @DisplayName("User account CRUD lifecycle executes asynchronously with SQLite")
    void testUserCrudLifecycle() throws Exception {
        UUID uuid = UUID.randomUUID();
        String username = "TestPlayer";
        String passwordHash = "$argon2id$v=19$m=65536,t=3,p=1$hash123";
        String hashedIp = "hmac_hashed_ip_12345";
        Instant now = Instant.now();

        UserAccount account = new UserAccount(uuid, username, passwordHash, hashedIp, now, now);

        // 1. Save
        repository.save(account).get();

        // 2. Check registration
        assertTrue(repository.isRegistered(uuid).get());

        // 3. Find by UUID
        Optional<UserAccount> foundByUuid = repository.findByUuid(uuid).get();
        assertTrue(foundByUuid.isPresent());
        assertEquals(username, foundByUuid.get().username());
        assertEquals(passwordHash, foundByUuid.get().passwordHash());

        // 4. Find by Username (Case-insensitive)
        Optional<UserAccount> foundByUsername = repository.findByUsername("testplayer").get();
        assertTrue(foundByUsername.isPresent());
        assertEquals(uuid, foundByUsername.get().uuid());

        // 5. Update
        String newHash = "$argon2id$v=19$m=65536,t=3,p=1$newhash456";
        UserAccount updated = new UserAccount(uuid, username, newHash, hashedIp, now, Instant.now());
        repository.update(updated).get();

        Optional<UserAccount> foundAfterUpdate = repository.findByUuid(uuid).get();
        assertTrue(foundAfterUpdate.isPresent());
        assertEquals(newHash, foundAfterUpdate.get().passwordHash());

        // 6. Delete
        boolean deleted = repository.delete(uuid).get();
        assertTrue(deleted);
        assertFalse(repository.isRegistered(uuid).get());
    }
}
