package dev.puffspark.lightframe.engine;

import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.LightColor;
import dev.puffspark.lightframe.net.ColorLightNetworking;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Composition root: maps World instances to engines, exposes static lookups
 * used by mixins (hot paths) and routes API calls to the right side.
 *
 * <p>Engines are explicitly dropped on world unload, so the map uses strong
 * references and stays tiny.</p>
 */
public final class EngineRegistry {

    private static final Map<World, RGBLightEngine> ENGINES = new ConcurrentHashMap<>();
    private static volatile Function<World, EngineListener> listenerFactory = w -> EngineListener.EMPTY;
    /** Sticky global gate for the hot light-query path; recomputed on world unload. */
    private static volatile boolean ANY_DATA;

    private EngineRegistry() {}

    public static void setListenerFactory(Function<World, EngineListener> factory) {
        listenerFactory = factory;
    }

    /** Existing engine or null. Never allocates. */
    @Nullable
    public static RGBLightEngine engineOrNull(World world) {
        return ENGINES.get(world);
    }

    public static Optional<RGBLightEngine> getEngine(World world) {
        return Optional.ofNullable(ENGINES.get(world));
    }

    /** Existing engine or freshly created one. */
    public static RGBLightEngine engineFor(World world) {
        return ENGINES.computeIfAbsent(world, w -> new RGBLightEngine(w, listenerFactory.apply(w)));
    }

    public static void onWorldUnload(World world) {
        ENGINES.remove(world);
        recomputeAnyData();
    }

    /** Drops engines of client worlds (on disconnect / dimension change). */
    public static void clearClientEngines() {
        ENGINES.keySet().removeIf(World::isClient);
        recomputeAnyData();
    }

    /** Hot-path gate: true once any world has RGB data (sticky until world unload). */
    public static boolean anyEngineHasData() {
        return ANY_DATA;
    }

    public static void noteDataPresent() {
        ANY_DATA = true;
    }

    private static void recomputeAnyData() {
        boolean any = false;
        for (RGBLightEngine e : ENGINES.values()) {
            if (e.hasAnyData()) {
                any = true;
                break;
            }
        }
        ANY_DATA = any;
    }

    // ------------------------------------------------------------------ API plumbing

    public static ColorLight createSource(World world, Vec3d pos, LightColor color, int radius, float intensity) {
        if (world == null || !ColorLightConfigProxy.enabled()) return null;
        RGBLightEngine engine = engineFor(world);
        ColorLightSource s = engine.createSource(pos, color, radius, intensity, null);
        if (s != null && !world.isClient()) {
            ColorLightNetworking.broadcastAdd((net.minecraft.server.world.ServerWorld) world, s);
        }
        return s;
    }

    public static ColorLight createDirectionalSource(World world, Vec3d pos, Vec3d dir,
                                                     float innerAngle, float outerAngle,
                                                     LightColor color, int radius, float intensity) {
        if (world == null || !ColorLightConfigProxy.enabled()) return null;
        RGBLightEngine engine = engineFor(world);
        ColorLightSource s = engine.createSource(pos, color, radius, intensity, null);
        if (s != null) {
            s.setConeAngles(innerAngle, outerAngle);
            s.setDirection(dir);
            if (!world.isClient()) {
                ColorLightNetworking.broadcastAdd((net.minecraft.server.world.ServerWorld) world, s);
            }
        }
        return s;
    }

    public static Optional<ColorLight> getSource(World world, UUID id) {
        RGBLightEngine e = engineOrNull(world);
        return e == null ? Optional.empty() : e.sources().byId(id).map(s -> (ColorLight) s);
    }

    public static Collection<ColorLight> getAllSources(World world) {
        RGBLightEngine e = engineOrNull(world);
        List<ColorLight> out = new ArrayList<>();
        if (e != null) out.addAll(e.sources().all());
        return out;
    }

    public static boolean removeSource(World world, UUID id) {
        RGBLightEngine e = engineOrNull(world);
        if (e == null) return false;
        ColorLightSource s = e.sources().byId(id).orElse(null);
        if (s == null) return false;
        s.remove();
        return true;
    }

    public static void removeAllSources(World world) {
        RGBLightEngine e = engineOrNull(world);
        if (e == null) return;
        List<ColorLightSource> doomed = new ArrayList<>();
        for (ColorLightSource s : e.sources().all()) {
            s.alive = false;
            doomed.add(s);
        }
        for (ColorLightSource s : doomed) {
            e.sourceRemoved(s);
            if (!world.isClient()) {
                ColorLightNetworking.broadcastRemove((net.minecraft.server.world.ServerWorld) world, s.getId());
            }
        }
    }

    public static int sourceCount(World world) {
        RGBLightEngine e = engineOrNull(world);
        return e == null ? 0 : e.sources().size();
    }

    // called by ColorLightSource setters
    static void onSourceMutated(ColorLightSource s) {
        RGBLightEngine e = ENGINES.get(s.world);
        if (e != null) {
            e.sourceChanged(s);
            if (!s.world.isClient()) {
                ColorLightNetworking.broadcastUpdate((net.minecraft.server.world.ServerWorld) s.world, s);
            }
        }
    }

    static void onSourceRemoved(ColorLightSource s) {
        RGBLightEngine e = ENGINES.get(s.world);
        if (e != null) {
            e.sourceRemoved(s);
            if (!s.world.isClient()) {
                ColorLightNetworking.broadcastRemove((net.minecraft.server.world.ServerWorld) s.world, s.getId());
            }
        }
    }

    // ------------------------------------------------------------------ client sync application

    /** Applies a synced source state (client thread). No-ops if the dimension doesn't match. */
    public static void upsertLocalSource(World world, UUID id, String dim,
                                         double x, double y, double z,
                                         float r, float g, float b,
                                         int radius, float intensity, boolean enabled) {
        upsertLocalSource(world, id, dim, x, y, z, r, g, b, radius, intensity, enabled, null, 30.0f, 45.0f);
    }

    public static void upsertLocalSource(World world, UUID id, String dim,
                                         double x, double y, double z,
                                         float r, float g, float b,
                                         int radius, float intensity, boolean enabled,
                                         Vec3d dir, float inner, float outer) {
        if (!world.getRegistryKey().getValue().toString().equals(dim)) return;
        RGBLightEngine engine = engineFor(world);
        ColorLightSource existing = engine.sources().get(id);
        if (existing != null) {
            existing.applyState(x, y, z, r, g, b, radius, intensity, enabled, dir, inner, outer);
            engine.sourceChanged(existing);
            return;
        }
        if (engine.sources().isFull(ColorLightConfigProxy.enabled()
                ? dev.puffspark.lightframe.config.ColorLightConfig.get().maxLightSources : 0)) {
            return;
        }
        ColorLightSource created = engine.createSource(
                new Vec3d(x, y, z), LightColor.of(r, g, b), radius, intensity, id);
        if (created != null) {
            created.applyState(x, y, z, r, g, b, radius, intensity, enabled, dir, inner, outer);
        }
    }

    /** Removes a synced source locally (client thread). */
    public static void removeSourceLocal(World world, UUID id) {
        RGBLightEngine e = engineOrNull(world);
        if (e == null) return;
        ColorLightSource s = e.sources().get(id);
        if (s != null) {
            s.alive = false;
            e.sourceRemoved(s);
        }
    }

    /** Clears all local sources in the given world (client-side, e.g. on bulk sync / dimension reset / replay seek). */
    public static void clearSourcesLocal(World world) {
        RGBLightEngine e = engineOrNull(world);
        if (e == null) return;
        List<ColorLightSource> doomed = new ArrayList<>(e.sources().all());
        for (ColorLightSource s : doomed) {
            s.alive = false;
            e.sourceRemoved(s);
        }
    }

    // ------------------------------------------------------------------ hot paths (mixins)

    /** Vanilla block-light boost (0..15) at the position; O(1), early-outs on empty storage. */
    public static int lumaBoost(World world, BlockPos pos) {
        RGBLightEngine e = ENGINES.get(world);
        if (e == null || !e.hasAnyData()) return 0;
        int max = e.maxChannelAt(pos.getX(), pos.getY(), pos.getZ());
        if (max <= 0) return 0;
        float norm = max / 255.0f;
        int level = Math.min(15, (int) Math.round(15.0f * Math.pow(norm, 0.65)));
        return Math.max(1, level);
    }

    /** Normalized RGB tint at world coords; returns false if there is no data. */
    public static boolean tintAt(World world, double x, double y, double z, float[] out3) {
        RGBLightEngine e = ENGINES.get(world);
        if (e == null) return false;
        return e.sampleTint(x, y, z, out3);
    }

    /** True if the given world has any client-visible RGB data (fast render-path gate). */
    public static boolean hasData(World world) {
        RGBLightEngine e = ENGINES.get(world);
        return e != null && e.hasAnyData();
    }

    /** Luma boost policy from config (gameplay queries vs render-only). */
    public static boolean boostAllowedFor(World world) {
        var cfg = ColorLightConfigProxy.get();
        if (!cfg.enableRGBLighting || !cfg.boostVanillaLight) return false;
        return cfg.affectGameplayLighting || world.isClient();
    }

    /** Internal indirection so the engine package does not import config directly everywhere. */
    private static final class ColorLightConfigProxy {
        static boolean enabled() {
            return dev.puffspark.lightframe.config.ColorLightConfig.get().enableRGBLighting;
        }
        static dev.puffspark.lightframe.config.ColorLightConfig get() {
            return dev.puffspark.lightframe.config.ColorLightConfig.get();
        }
    }
}

