package dev.puffspark.lightframe.render;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.engine.RGBLightEngine;
import dev.puffspark.lightframe.world.ChunkLightStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

/**
 * Client-side glue for the "vanilla backend": decides whether tinting is
 * active and wraps {@link VertexConsumer}s at the mixin hook points.
 */
public final class VanillaLightingBackend {

    private static final ThreadLocal<float[]> SAMPLE = ThreadLocal.withInitial(() -> new float[3]);

    private VanillaLightingBackend() {}

    /** Global gate: config + world/engine presence. */
    public static boolean tintActive() {
        ColorLightConfig cfg = ColorLightConfig.get();
        if (!cfg.enableRGBLighting) return false;
        World world = MinecraftClient.getInstance().world;
        return world != null && (EngineRegistry.hasData(world) || dev.puffspark.lightframe.dynamic.DynamicLightManager.hasActiveLights());
    }

    /** Wraps the terrain/fluid mesh consumer for one block/fluid render call. */
    public static VertexConsumer wrapBlock(VertexConsumer consumer, BlockPos pos) {
        if (consumer instanceof TintingVertexConsumer) return consumer;
        if (!tintActive()) return consumer;

        World world = MinecraftClient.getInstance().world;
        if (world == null) return consumer;

        int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
        int secX = bx >> 4, secY = by >> 4, secZ = bz >> 4;

        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        boolean hasStatic = engine != null && engine.hasAnyData() && engine.storage().hasLightNearSection(secX, secY, secZ);
        boolean hasDynamic = dev.puffspark.lightframe.dynamic.DynamicLightManager.isNearSection(secX, secY, secZ);

        if (!hasStatic && !hasDynamic) {
            return consumer;
        }

        float[] fallbackTint = new float[3];
        if (hasStatic) {
            sampleFallbackLight(engine, bx, by, bz, fallbackTint);
        }
        int fallbackBoost = 0;
        float m = Math.max(fallbackTint[0], Math.max(fallbackTint[1], fallbackTint[2]));
        if (m > 0.001f) {
            fallbackBoost = Math.min(15, Math.max(0, (int) Math.round(15.0f * Math.pow(m, 0.75))));
        }

        if (hasDynamic) {
            float[] dynRgb = new float[3];
            int[] dynBoost = new int[1];
            if (dev.puffspark.lightframe.dynamic.DynamicLightManager.sampleDynamicLight(bx + 0.5, by + 0.5, bz + 0.5, dynRgb, dynBoost)) {
                fallbackTint[0] = fallbackTint[0] + dynRgb[0] - (fallbackTint[0] * dynRgb[0]);
                fallbackTint[1] = fallbackTint[1] + dynRgb[1] - (fallbackTint[1] * dynRgb[1]);
                fallbackTint[2] = fallbackTint[2] + dynRgb[2] - (fallbackTint[2] * dynRgb[2]);
                fallbackBoost = Math.max(fallbackBoost, dynBoost[0]);
            }
        }

        float[] mult = new float[3];
        computeTintMultiplier(fallbackTint[0], fallbackTint[1], fallbackTint[2], mult);

        return new TintingVertexConsumer(consumer, world, pos, mult[0], mult[1], mult[2], fallbackBoost);
    }

    private static void sampleFallbackLight(RGBLightEngine engine, int bx, int by, int bz, float[] out) {
        int[] rgb = new int[3];
        ChunkLightStorage storage = engine.storage();
        if (storage.get(bx, by, bz, rgb) && (rgb[0] > 0 || rgb[1] > 0 || rgb[2] > 0)) {
            out[0] = rgb[0] / 255.0f;
            out[1] = rgb[1] / 255.0f;
            out[2] = rgb[2] / 255.0f;
            return;
        }
        if (storage.get(bx, by + 1, bz, rgb) && (rgb[0] > 0 || rgb[1] > 0 || rgb[2] > 0)) {
            out[0] = rgb[0] / 255.0f;
            out[1] = rgb[1] / 255.0f;
            out[2] = rgb[2] / 255.0f;
            return;
        }
        int[][] dirs = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}};
        for (int[] d : dirs) {
            if (storage.get(bx + d[0], by + d[1], bz + d[2], rgb) && (rgb[0] > 0 || rgb[1] > 0 || rgb[2] > 0)) {
                out[0] = rgb[0] / 255.0f;
                out[1] = rgb[1] / 255.0f;
                out[2] = rgb[2] / 255.0f;
                return;
            }
        }
        out[0] = out[1] = out[2] = 0.0f;
    }

    /**
     * Calculates smooth per-vertex tint multiplier and returns boosted block light level (0..15).
     */
    public static int sampleVertexTintAndBoost(World world, BlockPos pos, Direction face,
                                               float vx, float vy, float vz, float[] out) {
        if (world == null || pos == null) {
            out[0] = 1.0f; out[1] = 1.0f; out[2] = 1.0f;
            return 0;
        }

        float staticR = 0f, staticG = 0f, staticB = 0f;
        int staticBoost = 0;

        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        if (engine != null && engine.hasAnyData()) {
            ChunkLightStorage storage = engine.storage();
            int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();

            if (face == null) {
                float[] fallback = new float[3];
                sampleFallbackLight(engine, bx, by, bz, fallback);
                staticR = fallback[0];
                staticG = fallback[1];
                staticB = fallback[2];
                float m = Math.max(staticR, Math.max(staticG, staticB));
                if (m > 0.001f) {
                    staticBoost = Math.min(15, Math.max(0, (int) Math.floor(15.0f * Math.pow(m, 0.85))));
                }
            } else {
                Vec3i norm = face.getVector();
                int px = bx + norm.getX();
                int py = by + norm.getY();
                int pz = bz + norm.getZ();

                int x0, x1, y0, y1, z0, z1;
                int totalR = 0, totalG = 0, totalB = 0;
                int[] rgb = new int[3];

                switch (face) {
                    case UP, DOWN -> {
                        x0 = bx;
                        x1 = vx < 0.5f ? bx - 1 : bx + 1;
                        z0 = bz;
                        z1 = vz < 0.5f ? bz - 1 : bz + 1;
                        for (int i = 0; i < 4; i++) {
                            int sx = (i & 1) == 0 ? x0 : x1;
                            int sz = (i & 2) == 0 ? z0 : z1;
                            if (storage.get(sx, py, sz, rgb)) {
                                totalR += rgb[0]; totalG += rgb[1]; totalB += rgb[2];
                            }
                        }
                    }
                    case NORTH, SOUTH -> {
                        x0 = bx;
                        x1 = vx < 0.5f ? bx - 1 : bx + 1;
                        y0 = by;
                        y1 = vy < 0.5f ? by - 1 : by + 1;
                        for (int i = 0; i < 4; i++) {
                            int sx = (i & 1) == 0 ? x0 : x1;
                            int sy = (i & 2) == 0 ? y0 : y1;
                            if (storage.get(sx, sy, pz, rgb)) {
                                totalR += rgb[0]; totalG += rgb[1]; totalB += rgb[2];
                            }
                        }
                    }
                    case WEST, EAST -> {
                        z0 = bz;
                        z1 = vz < 0.5f ? bz - 1 : bz + 1;
                        y0 = by;
                        y1 = vy < 0.5f ? by - 1 : by + 1;
                        for (int i = 0; i < 4; i++) {
                            int sz = (i & 1) == 0 ? z0 : z1;
                            int sy = (i & 2) == 0 ? y0 : y1;
                            if (storage.get(px, sy, sz, rgb)) {
                                totalR += rgb[0]; totalG += rgb[1]; totalB += rgb[2];
                            }
                        }
                    }
                }

                if (totalR == 0 && totalG == 0 && totalB == 0) {
                    if (storage.get(bx, by, bz, rgb)) {
                        totalR = rgb[0] * 4;
                        totalG = rgb[1] * 4;
                        totalB = rgb[2] * 4;
                    }
                }

                staticR = (totalR / 4.0f) / 255.0f;
                staticG = (totalG / 4.0f) / 255.0f;
                staticB = (totalB / 4.0f) / 255.0f;

                float maxChan = Math.max(staticR, Math.max(staticG, staticB));
                if (maxChan > 0.001f) {
                    staticBoost = Math.min(15, Math.max(0, (int) Math.round(15.0f * Math.pow(maxChan, 0.75))));
                }
            }
        }

        // Screen blend dynamic light on top of static light
        float lr = staticR;
        float lg = staticG;
        float lb = staticB;
        int boost = staticBoost;

        if (dev.puffspark.lightframe.dynamic.DynamicLightManager.hasActiveLights()) {
            float[] dynRgb = new float[3];
            int[] dynBoost = new int[1];
            double worldX = pos.getX() + vx;
            double worldY = pos.getY() + vy;
            double worldZ = pos.getZ() + vz;
            if (dev.puffspark.lightframe.dynamic.DynamicLightManager.sampleDynamicLight(worldX, worldY, worldZ, dynRgb, dynBoost)) {
                lr = lr + dynRgb[0] - (lr * dynRgb[0]);
                lg = lg + dynRgb[1] - (lg * dynRgb[1]);
                lb = lb + dynRgb[2] - (lb * dynRgb[2]);
                boost = Math.max(boost, dynBoost[0]);
            }
        }

        computeTintMultiplier(lr, lg, lb, out);
        return boost;
    }

    public static void sampleVertexTint(World world, BlockPos pos, Direction face,
                                        float vx, float vy, float vz, float[] out) {
        sampleVertexTintAndBoost(world, pos, face, vx, vy, vz, out);
    }

    /**
     * Blends light RGB into a vertex color multiplier that preserves luminance,
     * guarantees smooth fade-out at radius edges, and prevents black muddy shadows.
     */
    public static void computeTintMultiplier(float lr, float lg, float lb, float[] out) {
        float maxChan = Math.max(lr, Math.max(lg, lb));
        if (maxChan <= 0.001f) {
            out[0] = 1.0f;
            out[1] = 1.0f;
            out[2] = 1.0f;
            return;
        }

        float s = ColorLightConfig.get().tintStrength;
        float normR = lr / maxChan;
        float normG = lg / maxChan;
        float normB = lb / maxChan;

        // Smooth curve keeps the color rich and punchy while gently decaying to 0 at the tail
        float factor = (float) Math.pow(maxChan, 0.75) * s;

        // Deep vibrant floor: prevents pale milky wash-out while keeping texture details
        float minFloor = 0.12f;
        float range = 1.0f - minFloor;

        out[0] = 1.0f - factor * (1.0f - normR) * range;
        out[1] = 1.0f - factor * (1.0f - normG) * range;
        out[2] = 1.0f - factor * (1.0f - normB) * range;
    }

    /** Public sampler for provider wrappers (entities / block entities). */
    public static float[] sampleAt(double x, double y, double z) {
        World world = MinecraftClient.getInstance().world;
        if (world == null) return null;
        float[] sample = SAMPLE.get();
        boolean hasStatic = EngineRegistry.tintAt(world, x, y, z, sample);
        if (!hasStatic) {
            sample[0] = 0f; sample[1] = 0f; sample[2] = 0f;
        }

        if (dev.puffspark.lightframe.dynamic.DynamicLightManager.hasActiveLights()) {
            float[] dynRgb = new float[3];
            int[] dynBoost = new int[1];
            if (dev.puffspark.lightframe.dynamic.DynamicLightManager.sampleDynamicLight(x, y, z, dynRgb, dynBoost)) {
                sample[0] = sample[0] + dynRgb[0] - (sample[0] * dynRgb[0]);
                sample[1] = sample[1] + dynRgb[1] - (sample[1] * dynRgb[1]);
                sample[2] = sample[2] + dynRgb[2] - (sample[2] * dynRgb[2]);
                hasStatic = true;
            }
        }

        float m = Math.max(sample[0], Math.max(sample[1], sample[2]));
        if (m < 1.0f / 255.0f) return null;
        return sample;
    }

    /** Wraps an entity/block-entity consumer provider at the given world position. */
    public static VertexConsumerProvider wrapProvider(VertexConsumerProvider provider, double x, double y, double z) {
        if (provider instanceof TintingVertexConsumerProvider) return provider;
        if (!tintActive()) return provider;
        float[] tint = sampleAt(x, y, z);
        if (tint == null) return provider;

        float[] mult = new float[3];
        computeTintMultiplier(tint[0], tint[1], tint[2], mult);
        if (mult[0] >= 0.999f && mult[1] >= 0.999f && mult[2] >= 0.999f) return provider;

        float m = Math.max(tint[0], Math.max(tint[1], tint[2]));
        int boost = Math.min(15, Math.max(0, (int) Math.floor(15.0f * Math.pow(m, 0.85))));

        return new TintingVertexConsumerProvider(provider, mult[0], mult[1], mult[2], boost);
    }

    /**
     * Schedules a rebuild of one section (called from the client engine listener, client thread).
     */
    public static void scheduleSectionRender(World world, long sectionKey) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer == null || mc.world != world) return;
        BlockPos raw = BlockPos.fromLong(sectionKey);
        int secX = raw.getX() >> 4;
        int secY = raw.getY() >> 4;
        int secZ = raw.getZ() >> 4;
        ((dev.puffspark.lightframe.mixin.WorldRendererAccessor) mc.worldRenderer)
                .cl$scheduleChunkRender(secX, secY, secZ, true);
    }
}

