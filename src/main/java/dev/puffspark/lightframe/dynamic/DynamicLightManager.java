package dev.puffspark.lightframe.dynamic;

import dev.puffspark.lightframe.block.ColoredTorchItem;
import dev.puffspark.lightframe.block.TorchColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated real-time dynamic light engine for held torches and moving entities.
 * Completely separate from the static voxel engine:
 * - Does NOT use RGBLightEngine queue (zero queue pollution!).
 * - Does NOT do expensive BFS flood-fills.
 * - Samples analytically in O(1) during vertex/entity rendering.
 * - Throttles chunk rebuilds to at most once every 4 ticks when moving across blocks.
 */
public final class DynamicLightManager {

    public static final class DynamicLight {
        public double x, y, z;
        public float r, g, b;
        public int radius = 10;
        public float intensity = 1.0f;
    }

    private static volatile DynamicLight[] ACTIVE_LIGHTS = new DynamicLight[0];
    private static BlockPos lastPlayerBlock = null;
    private static int updateCooldown = 0;

    private DynamicLightManager() {}

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) {
            ACTIVE_LIGHTS = new DynamicLight[0];
            lastPlayerBlock = null;
            return;
        }

        List<DynamicLight> lightsList = new ArrayList<>(8);

        // Detect held colored torches on local player and nearby players
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(client.player) > 32 * 32) continue;

            TorchColor torchColor = getHeldTorchColor(player);
            if (torchColor != null) {
                DynamicLight light = new DynamicLight();
                light.x = player.getX();
                light.y = player.getY() + 1.1;
                light.z = player.getZ();
                light.r = torchColor.getR();
                light.g = torchColor.getG();
                light.b = torchColor.getB();
                light.radius = 10;
                light.intensity = 1.0f;
                lightsList.add(light);
            }
        }

        ACTIVE_LIGHTS = lightsList.isEmpty() ? new DynamicLight[0] : lightsList.toArray(new DynamicLight[0]);

        if (updateCooldown > 0) {
            updateCooldown--;
        }

        BlockPos currentBlock = client.player.getBlockPos();
        boolean hasDynamic = ACTIVE_LIGHTS.length > 0;

        if (hasDynamic) {
            if (!currentBlock.equals(lastPlayerBlock) && updateCooldown <= 0) {
                lastPlayerBlock = currentBlock;
                updateCooldown = 4; // At most once every 4 ticks (5 times/sec) when moving
                scheduleLocalChunkRebuild(currentBlock);
            }
        } else if (lastPlayerBlock != null) {
            // Player put torch away: refresh local area once
            scheduleLocalChunkRebuild(lastPlayerBlock);
            lastPlayerBlock = null;
        }
    }

    private static void scheduleLocalChunkRebuild(BlockPos center) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.worldRenderer == null) return;
        int cx = center.getX() >> 4;
        int cy = center.getY() >> 4;
        int cz = center.getZ() >> 4;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    BlockPos pos = new BlockPos((cx + dx) << 4, (cy + dy) << 4, (cz + dz) << 4);
                    ((dev.puffspark.lightframe.mixin.WorldRendererAccessor) mc.worldRenderer)
                            .cl$scheduleSectionRender(pos, false);
                }
            }
        }
    }

    /**
     * Fast O(1) analytical sampling of dynamic lights at any 3D vertex position.
     * Evaluates smooth Hermite falloff and screen-blends active dynamic lights.
     */
    public static boolean sampleDynamicLight(double vx, double vy, double vz, float[] outRgb, int[] outBoost) {
        DynamicLight[] lights = ACTIVE_LIGHTS;
        if (lights.length == 0) return false;

        float dynR = 0.0f, dynG = 0.0f, dynB = 0.0f;
        int maxBoost = 0;
        boolean hit = false;

        for (DynamicLight light : lights) {
            double dx = vx - light.x;
            double dy = vy - light.y;
            double dz = vz - light.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double rad = light.radius;

            if (distSq < rad * rad) {
                double dist = Math.sqrt(distSq);
                float t = (float) (dist / rad);
                if (t >= 1.0f) continue;

                float falloff = Math.max(0.0f, 1.0f - t * t * (3.0f - 2.0f * t)) * light.intensity;
                float lr = light.r * falloff;
                float lg = light.g * falloff;
                float lb = light.b * falloff;

                // Screen blend with any other dynamic lights
                dynR = dynR + lr - (dynR * lr);
                dynG = dynG + lg - (dynG * lg);
                dynB = dynB + lb - (dynB * lb);

                int boost = Math.min(15, (int) Math.round(15.0f * Math.pow(falloff, 0.75)));
                if (boost > maxBoost) maxBoost = boost;
                hit = true;
            }
        }

        if (hit) {
            outRgb[0] = dynR;
            outRgb[1] = dynG;
            outRgb[2] = dynB;
            outBoost[0] = maxBoost;
            return true;
        }
        return false;
    }

    public static boolean hasActiveLights() {
        return ACTIVE_LIGHTS.length > 0;
    }

    public static boolean isNearSection(int secX, int secY, int secZ) {
        DynamicLight[] lights = ACTIVE_LIGHTS;
        if (lights.length == 0) return false;
        double secCenterX = (secX << 4) + 8.0;
        double secCenterY = (secY << 4) + 8.0;
        double secCenterZ = (secZ << 4) + 8.0;

        for (DynamicLight light : lights) {
            double dx = secCenterX - light.x;
            double dy = secCenterY - light.y;
            double dz = secCenterZ - light.z;
            double maxDist = light.radius + 14.0; // 10 radius + half-section diagonal ~14
            if (dx * dx + dy * dy + dz * dz <= maxDist * maxDist) {
                return true;
            }
        }
        return false;
    }

    private static TorchColor getHeldTorchColor(AbstractClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.getItem() instanceof ColoredTorchItem torchItem) {
            return torchItem.getTorchColor();
        }
        ItemStack off = player.getOffHandStack();
        if (off.getItem() instanceof ColoredTorchItem torchItem) {
            return torchItem.getTorchColor();
        }
        return null;
    }

    public static void clear() {
        ACTIVE_LIGHTS = new DynamicLight[0];
        lastPlayerBlock = null;
        updateCooldown = 0;
    }
}

