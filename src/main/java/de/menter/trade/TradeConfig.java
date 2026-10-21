package de.menter.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/handshake-trade.json; missing keys keep their defaults and the file is rewritten with all keys */
public final class TradeConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("handshake-trade");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("request_timeout_seconds")
    public int requestTimeoutSeconds = 60;

    /** confirming is blocked this long after any change to an offer, against last-second swaps */
    @SerializedName("confirm_cooldown_seconds")
    public double confirmCooldownSeconds = 1.5;

    /** 0 = no limit */
    @SerializedName("max_distance")
    public double maxDistance = 0;

    @SerializedName("allow_cross_dimension")
    public boolean allowCrossDimension = true;

    private static TradeConfig instance = new TradeConfig();

    public static TradeConfig get() {
        return instance;
    }

    static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("handshake-trade.json");
        TradeConfig config = new TradeConfig();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                TradeConfig read = GSON.fromJson(reader, TradeConfig.class);
                if (read != null) config = read;
            } catch (Exception e) {
                LOGGER.error("Could not read {}, using defaults", file, e);
            }
        }
        config.requestTimeoutSeconds = Math.max(5, config.requestTimeoutSeconds);
        config.confirmCooldownSeconds = Math.max(0, config.confirmCooldownSeconds);
        config.maxDistance = Math.max(0, config.maxDistance);
        instance = config;
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }
}
