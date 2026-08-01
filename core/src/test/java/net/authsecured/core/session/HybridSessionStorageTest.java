package net.authsecured.core.session;

import net.authsecured.core.model.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HybridSessionStorageTest {

    private HybridSessionStorage sessionStorage;

    @BeforeEach
    void setUp() {
        LocalMemorySessionStorage localStorage = new LocalMemorySessionStorage();
        // Null redis storage simulates disabled/offline Redis
        sessionStorage = new HybridSessionStorage(null, localStorage);
    }

    @Test
    @DisplayName("HybridSessionStorage gracefully falls back to local memory when Redis is null")
    void testLocalFallback() throws Exception {
        UUID uuid = UUID.randomUUID();
        UserSession session = new UserSession(uuid, "TestUser", "hashed_ip", Instant.now(), Instant.now().plusSeconds(3600));

        sessionStorage.saveSession(session).get();

        Optional<UserSession> retrieved = sessionStorage.getSession(uuid).get();
        assertTrue(retrieved.isPresent());
        assertEquals("TestUser", retrieved.get().username());

        sessionStorage.invalidateSession(uuid).get();

        Optional<UserSession> afterInvalidate = sessionStorage.getSession(uuid).get();
        assertFalse(afterInvalidate.isPresent());
    }
}
