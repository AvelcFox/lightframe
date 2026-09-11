package dev.puffspark.lightframe.api;

import net.minecraft.util.math.Vec3d;

/**
 * A handle to a single dynamic RGB light source.
 *
 * <p>All mutators take effect on the next engine tick; the engine re-propagates
 * only the affected region (dirty sections), never the whole world.</p>
 *
 * <p>Handles may be created on the logical server (synced to clients) or on the
 * client (visual only).</p>
 */
public interface ColorLight {

    /** Stable id, used for updates/removal across the network. */
    java.util.UUID getId();

    /** The dimension this source lives in. */
    net.minecraft.registry.RegistryKey<net.minecraft.world.World> getWorldKey();

    /** True if this source is managed by the client-side (visual only) engine. */
    boolean isClientSide();

    LightColor getColor();

    /** Sets the color; re-propagates the affected volume. */
    void setColor(LightColor color);

    /** Multiplier in [0, 4]; 1.0 = full brightness of the color channels. */
    float getIntensity();

    void setIntensity(float intensity);

    /** Half-side of the influence cube, in blocks. Clamped by config maxLightRadius. */
    int getRadius();

    void setRadius(int radius);

    Vec3d getPosition();

    /** Moves the source; only the difference of old/new influence volumes is rebuilt. */
    void setPosition(Vec3d pos);

    boolean isEnabled();

    /** A disabled source keeps its id and settings but contributes no light. */
    void setEnabled(boolean enabled);

    /** Permanently removes the source. The handle must not be used afterwards. */
    void remove();

    /** True until {@link #remove()} was called. */
    boolean isAlive();
}

