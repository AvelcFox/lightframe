package dev.puffspark.lightframe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.puffspark.lightframe.LightFrame;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON config: {@code config/LightFrame.json}.
 * Recreated with defaults if missing; reloaded on /LightFrame reload.
 */
public final class ColorLightConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ColorLightConfig instance;

    // ---- Core (spec §16) ----
    /** Master switch for the whole RGB lighting system. */
    public boolean enableRGBLighting = true;
    /** Hard cap on live sources per world. */
    public int maxLightSources = 512;
    /** Hard cap on source radius, blocks. */
    public int maxLightRadius = 32;
    /** LOW: per-block tint sampling, MEDIUM: default, HIGH: reserved for per-vertex refinements. */
    public String lightingQuality = "MEDIUM"; // LOW | MEDIUM | HIGH
    /** Max milliseconds of propagation work per world per tick. */
    public double updateBudget = 2.0;
    /** Debug rendering + HUD + verbose log. */
    public boolean debugMode = false;

    // ---- Vanilla interplay ----
    /** Feed the luminance of RGB light into vanilla block light (makes colored light visible in the dark). */
    public boolean boostVanillaLight = true;
    /** If false, only the client renderer sees the boosted light (mobs still spawn in lit areas). */
    public boolean affectGameplayLighting = true;

    // ---- Rendering ----
    /** Strength of the hue applied to block/entity colors, 0..1. */
    public float tintStrength = 1.0f;
    /** Tint entities (experimental, Stage 3). */
    public boolean tintEntities = true;
    /** Directional normal-aware lighting for entities. */
    public boolean entityDirectionalLighting = true;
    /** Tint block entities (chests etc.). */
    public boolean tintBlockEntities = true;
    /** Max sections scheduled for rebuild per client tick (rebuild throttling). */
    public int maxSectionsRebuiltPerTick = 12;

    // ---- Bloom (Atmospheric colored glow) ----
    /** Enable atmospheric colored bloom aura around bright colored lights. */
    public boolean enableBloom = true;
    /** Bloom intensity multiplier (0.0 to 2.0). */
    public float bloomIntensity = 0.8f;

    // ---- Iris fallback ----
    /** Keep feeding luminance into block light when a shaderpack is active (monochrome dynamic light). */
    public boolean irisFallbackKeepDynamicLight = true;
    /** Log a single friendly message when falling back because of a shaderpack. */
    public boolean logIrisFallback = true;

    // ---- Debug perf counters ----
    /** Log propagation timings every N seconds when debugMode is on (0 = never). */
    public int debugLogIntervalSeconds = 5;

    public static ColorLightConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        Path path = path();
        ColorLightConfig cfg = new ColorLightConfig();
        try {
            if (Files.exists(path)) {
                cfg = GSON.fromJson(Files.readString(path), ColorLightConfig.class);
                if (cfg == null) cfg = new ColorLightConfig();
            }
        } catch (Exception e) {
            LightFrame.LOGGER.warn("Failed to read config, using defaults", e);
            cfg = new ColorLightConfig();
        }
        cfg.sanitize();
        instance = cfg;
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(get()));
        } catch (IOException e) {
            LightFrame.LOGGER.warn("Failed to save config", e);
        }
    }

    public boolean enabled() {
        return enableRGBLighting;
    }

    public boolean lowQuality() {
        return "LOW".equalsIgnoreCase(lightingQuality);
    }

    private void sanitize() {
        maxLightSources = Math.max(0, Math.min(maxLightSources, 1024));
        maxLightRadius = Math.max(2, Math.min(maxLightRadius, 64));
        tintStrength = Math.max(0.0f, Math.min(tintStrength, 1.0f));
        updateBudget = Math.max(0.0, Math.min(updateBudget, 50.0));
        maxSectionsRebuiltPerTick = Math.max(1, Math.min(maxSectionsRebuiltPerTick, 256));
        if (!"LOW".equalsIgnoreCase(lightingQuality) && !"HIGH".equalsIgnoreCase(lightingQuality)) {
            lightingQuality = "MEDIUM";
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("LightFrame.json");
    }
}

