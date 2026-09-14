package dev.puffspark.lightframe.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.puffspark.lightframe.LightFrame;
import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.ColorLightAPI;
import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.dynamic.DynamicLightManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Additive atmospheric bloom / glow pass.
 *
 * <p>Completely decoupled from voxel light propagation (does not modify ChunkLightStorage).
 * Renders smooth additive radial coronas around bright light sources in world space.</p>
 */
public final class BloomRenderer {

    private static final Identifier BLOOM_TEXTURE = LightFrame.id("textures/environment/bloom_corona.png");
    private static final double MAX_RENDER_DIST_SQ = 48.0 * 48.0;

    private BloomRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BloomRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        ColorLightConfig cfg = ColorLightConfig.get();
        if (!cfg.enableRGBLighting || !cfg.enableBloom || cfg.bloomIntensity <= 0.01f) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Vec3d camPos = ctx.camera().getPos();
        Quaternionf camRot = ctx.camera().getRotation();
        float intensityMult = cfg.bloomIntensity;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        boolean hasVertices = false;

        MatrixStack matrices = new MatrixStack();

        // 1. Static / placed engine sources
        for (ColorLight light : ColorLightAPI.getAll(mc.world)) {
            if (!light.isEnabled()) continue;
            Vec3d pos = light.getPosition();
            double distSq = pos.squaredDistanceTo(camPos);
            if (distSq > MAX_RENDER_DIST_SQ) continue;

            float inten = light.getIntensity() * intensityMult;
            if (inten <= 0.01f) continue;

            int rgb = light.getColor().asRgb();
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;

            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            if (lum <= 0.05f) continue;

            double dist = Math.sqrt(distSq);
            float distFade = dist < 0.8 ? (float) (dist / 0.8) : 1.0f;

            float size = Math.min(2.8f, 1.2f + (float) Math.sqrt(light.getRadius()) * 0.25f);
            float alpha = Math.min(0.70f, (0.20f + lum * 0.30f) * inten) * distFade;
            if (alpha <= 0.005f) continue;

            hasVertices = true;
            renderBillboard(buffer, matrices, camPos, pos, camRot, r, g, b, alpha, size);
        }

        // 2. Handheld dynamic lights
        if (DynamicLightManager.hasActiveLights()) {
            for (DynamicLightManager.DynamicLight light : DynamicLightManager.getActiveLights()) {
                Vec3d pos = new Vec3d(light.x, light.y, light.z);
                double distSq = pos.squaredDistanceTo(camPos);
                if (distSq > MAX_RENDER_DIST_SQ) continue;

                float inten = light.intensity * intensityMult;
                if (inten <= 0.01f) continue;

                float r = light.r;
                float g = light.g;
                float b = light.b;

                float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                if (lum <= 0.05f) continue;

                double dist = Math.sqrt(distSq);
                float distFade = dist < 0.8 ? (float) (dist / 0.8) : 1.0f;

                float size = 1.8f;
                float alpha = Math.min(0.70f, (0.20f + lum * 0.30f) * inten) * distFade;
                if (alpha <= 0.005f) continue;

                hasVertices = true;
                renderBillboard(buffer, matrices, camPos, pos, camRot, r, g, b, alpha, size);
            }
        }

        if (hasVertices) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, BLOOM_TEXTURE);

            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        } else {
            // Cancel empty buffer if nothing was drawn
            buffer.end();
        }
    }

    private static void renderBillboard(BufferBuilder buffer, MatrixStack matrices,
                                        Vec3d camPos, Vec3d lightPos, Quaternionf camRot,
                                        float r, float g, float b, float a, float size) {
        matrices.push();
        matrices.translate(lightPos.x - camPos.x, lightPos.y - camPos.y, lightPos.z - camPos.z);
        matrices.multiply(camRot);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        float half = size * 0.5f;

        buffer.vertex(mat, -half, -half, 0.0f).texture(0.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(mat, half, -half, 0.0f).texture(1.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(mat, half, half, 0.0f).texture(1.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(mat, -half, half, 0.0f).texture(0.0f, 1.0f).color(r, g, b, a);

        matrices.pop();
    }
}
