package dev.puffspark.lightframe.api;

import dev.puffspark.lightframe.engine.EngineRegistry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point for other mods.
 *
 * <pre>{@code
 *   ColorLight light = ColorLightAPI.create(world, pos, LightColor.RED, 8, 1.0f);
 *   light.setIntensity(2.0f);
 *   light.setColor(LightColor.PURPLE);
 *   light.setPosition(newPos);
 *   light.setEnabled(false);   // temporarily off, keeps id/settings
 *   light.remove();            // permanently gone
 *
 *   ColorLightAPI.getAll(world).forEach(l -> ...);
 * }</pre>
 *
 * <p>Sources created on the logical server are authoritative and synced to
 * clients. Sources created on the client render only on the client.</p>
 */
public final class ColorLightAPI {

    private ColorLightAPI() {}

    /**
     * Creates a light source in the given world.
     *
     * @param world     the logical world (either side)
     * @param position  position of the emission center
     * @param color     RGB color, channels in [0, 1]
     * @param radius    half-side of the influence cube, blocks (clamped by config)
     * @param intensity brightness multiplier, clamped to [0, 4]
     * @return a live handle, or null if the config limit (maxLightSources) is reached
     */
    public static ColorLight create(World world, Vec3d position, LightColor color, int radius, float intensity) {
        return EngineRegistry.createSource(world, position, color, radius, intensity);
    }

    /**
     * Creates a directional / cone light source in the given world.
     *
     * @param world       the logical world (either side)
     * @param position    position of the emission center
     * @param direction   direction vector of the beam (will be normalized)
     * @param innerAngle  inner cone half-angle in degrees (full brightness inside)
     * @param outerAngle  outer cone half-angle in degrees (fades to 0 at edge)
     * @param color       RGB color, channels in [0, 1]
     * @param radius      half-side of the influence cube, blocks (clamped by config)
     * @param intensity   brightness multiplier, clamped to [0, 4]
     * @return a live handle, or null if the config limit (maxLightSources) is reached
     */
    public static ColorLight createDirectional(World world, Vec3d position, Vec3d direction,
                                               float innerAngle, float outerAngle,
                                               LightColor color, int radius, float intensity) {
        return EngineRegistry.createDirectionalSource(world, position, direction, innerAngle, outerAngle, color, radius, intensity);
    }

    public static Optional<ColorLight> get(World world, UUID id) {
        return EngineRegistry.getSource(world, id);
    }

    public static Collection<ColorLight> getAll(World world) {
        return EngineRegistry.getAllSources(world);
    }

    public static boolean remove(World world, UUID id) {
        return EngineRegistry.removeSource(world, id);
    }

    public static void removeAll(World world) {
        EngineRegistry.removeAllSources(world);
    }

    /** Number of live sources in the world (for HUD/monitoring). */
    public static int count(World world) {
        return EngineRegistry.sourceCount(world);
    }
}

