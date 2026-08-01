package net.authsecured.fabric.auth;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager tracking player authentication states in the Fabric adapter layer.
 */
public final class AuthManager {

    private static final AuthManager INSTANCE = new AuthManager();

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();

    private AuthManager() {}

    public static AuthManager getInstance() {
        return INSTANCE;
    }

    public boolean isAuthenticated(UUID uuid) {
        return uuid != null && authenticatedPlayers.contains(uuid);
    }

    public boolean isAuthenticated(ServerPlayerEntity player) {
        return player != null && isAuthenticated(player.getUuid());
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (uuid == null) return;
        if (authenticated) {
            authenticatedPlayers.add(uuid);
        } else {
            authenticatedPlayers.remove(uuid);
        }
    }

    public void onPlayerQuit(UUID uuid) {
        if (uuid != null) {
            authenticatedPlayers.remove(uuid);
        }
    }
}
