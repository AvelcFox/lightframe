package dev.puffspark.lightframe.engine;

import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.LightColor;
import dev.puffspark.lightframe.config.ColorLightConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Server/client implementation of {@link ColorLight}.
 * Volatile fields: mutated by the API (any thread), read by the tick thread.
 * The influence box of the last propagation is owned by the engine thread.
 */
public final class ColorLightSource implements ColorLight {

    final UUID id;
    final RegistryKey<World> worldKey;
    final boolean clientSide;
    final World world; // strong ref; released together with the engine on world unload

    volatile double x, y, z;
    volatile LightColor color = LightColor.WHITE;
    volatile float intensity = 1.0f;
    volatile int radius = 8;
    volatile boolean enabled = true;
    volatile boolean alive = true;

    // box of the last successful propagation (engine thread only)
    int boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ;
    boolean hasBox;

    ColorLightSource(World world, Vec3d pos, LightColor color, int radius, float intensity) {
        this(world, pos, color, radius, intensity, null);
    }

    ColorLightSource(World world, Vec3d pos, LightColor color, int radius, float intensity, UUID forcedId) {
        this.id = forcedId == null ? UUID.randomUUID() : forcedId;
        this.world = world;
        this.worldKey = world.getRegistryKey();
        this.clientSide = world.isClient();
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.color = color;
        this.radius = clampRadius(radius);
        this.intensity = MathHelper.clamp(intensity, 0.0f, 4.0f);
    }

    /** Network-synced copy keeps the authoritative server id (see 6-arg ctor). */

    private int clampRadius(int r) {
        int max = ColorLightConfig.get().maxLightRadius;
        return Math.max(1, Math.min(r, max));
    }

    @Override public UUID getId() { return id; }
    @Override public RegistryKey<World> getWorldKey() { return worldKey; }
    @Override public boolean isClientSide() { return clientSide; }
    @Override public LightColor getColor() { return color; }
    @Override public float getIntensity() { return intensity; }
    @Override public int getRadius() { return radius; }
    @Override public Vec3d getPosition() { return new Vec3d(x, y, z); }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAlive() { return alive; }

    @Override
    public void setColor(LightColor newColor) {
        this.color = newColor == null ? LightColor.WHITE : newColor;
        EngineRegistry.onSourceMutated(this);
    }

    @Override
    public void setIntensity(float value) {
        this.intensity = MathHelper.clamp(value, 0.0f, 4.0f);
        EngineRegistry.onSourceMutated(this);
    }

    @Override
    public void setRadius(int value) {
        this.radius = clampRadius(value);
        EngineRegistry.onSourceMutated(this);
    }

    @Override
    public void setPosition(Vec3d pos) {
        if (pos == null) return;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        EngineRegistry.onSourceMutated(this);
    }

    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
        EngineRegistry.onSourceMutated(this);
    }

    @Override
    public void remove() {
        if (!alive) return;
        alive = false;
        EngineRegistry.onSourceRemoved(this);
    }

    /**
     * Internal: directly sets fields during network sync / bulk load without extra re-enqueueing.
     */
    void applyState(double nx, double ny, double nz, float cr, float cg, float cb,
                    int rad, float inten, boolean en) {
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.color = LightColor.of(cr, cg, cb);
        this.radius = clampRadius(rad);
        this.intensity = MathHelper.clamp(inten, 0.0f, 4.0f);
        this.enabled = en;
    }

    int boxRadius() {
        return radius;
    }
}

