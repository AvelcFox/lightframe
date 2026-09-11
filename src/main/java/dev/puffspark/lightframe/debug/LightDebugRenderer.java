package dev.puffspark.lightframe.debug;

import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.ColorLightAPI;
import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.engine.EngineRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Debug visualization: source markers + influence box in the light's color.
 * Toggled by config debugMode or the K keybind.
 */
public final class LightDebugRenderer {

    private LightDebugRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(LightDebugRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!ColorLightConfig.get().debugMode || mc.world == null) return;
        var engine = EngineRegistry.engineOrNull(mc.world);
        if (engine == null) return;

        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;
        Vec3d cam = ctx.camera().getPos();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        VertexConsumer lines = ctx.consumers().getBuffer(RenderLayer.getLines());

        for (ColorLight light : ColorLightAPI.getAll(mc.world)) {
            Vec3d p = light.getPosition();
            int rgb = light.getColor().asRgb();
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;

            // cross at the center marker
            WorldRendererCompat.drawLine(lines, mat,
                    p.x - 0.5, p.y, p.z, p.x + 0.5, p.y, p.z, r, g, b, 1.0f);
            WorldRendererCompat.drawLine(lines, mat,
                    p.x, p.y - 0.5, p.z, p.x, p.y + 0.5, p.z, r, g, b, 1.0f);
            WorldRendererCompat.drawLine(lines, mat,
                    p.x, p.y, p.z - 0.5, p.x, p.y, p.z + 0.5, r, g, b, 1.0f);
            // stem to the ground
            int floor = worldBottom(mc, p);
            WorldRendererCompat.drawLine(lines, mat,
                    p.x, p.y, p.z, p.x, floor, p.z, r * 0.6f, g * 0.6f, b * 0.6f, 0.7f);
        }
        matrices.pop();
    }

    private static int worldBottom(MinecraftClient mc, Vec3d p) {
        BlockPos pos = BlockPos.ofFloored(p.x, p.y, p.z);
        for (int y = pos.getY(); y >= mc.world.getBottomY(); y--) {
            if (!mc.world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isAir()) {
                return y + 1;
            }
        }
        return mc.world.getBottomY();
    }

    private static final class WorldRendererCompat {
        static void drawLine(VertexConsumer vc, Matrix4f mat,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
            line(vc, mat, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        private static void line(VertexConsumer vc, Matrix4f mat,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
            vc.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(0f, 1f, 0f);
            vc.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(0f, 1f, 0f);
        }
    }
}

