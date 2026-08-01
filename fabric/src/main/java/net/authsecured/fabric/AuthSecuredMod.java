package net.authsecured.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthSecuredMod implements ModInitializer {

    public static final String MOD_ID = "authsecured";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("AuthSecured Enterprise Fabric Mod v1.0.3 initializing...");
    }
}
