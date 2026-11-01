package dev.realseasons;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RealSeasons implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("RealSeasons");
    public static final String WORLD_TYPE_ID = "realseasons:overworld";
    public static final String LANGUAGE_KEY = "worldType.realseasons";
    public static final String SYNC_CHANNEL = "RealSeasons:Sync";

    @Override
    public void onInitialize() {
        LOGGER.info("Real Seasons for BTA 8.0.1 initialized; calendar follows the host's local date.");
    }
}
