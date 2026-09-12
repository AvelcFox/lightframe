package dev.puffspark.lightframe.mixin.sodium;

import dev.puffspark.lightframe.render.VanillaLightingBackend;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.frapi.mesh.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererMixin extends AbstractBlockRenderContext {

    @Inject(method = "processQuad", at = @At(value = "INVOKEVIRTUAL", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"), remap = false)
    private void cl$tintSodiumQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (!VanillaLightingBackend.tintActive()) return;
        World world = MinecraftClient.getInstance().world;
        BlockPos currentPos = this.pos;
        if (world == null || currentPos == null) return;

        int bx = currentPos.getX(), by = currentPos.getY(), bz = currentPos.getZ();
        int secX = bx >> 4, secY = by >> 4, secZ = bz >> 4;

        dev.puffspark.lightframe.engine.RGBLightEngine engine = dev.puffspark.lightframe.engine.EngineRegistry.engineOrNull(world);
        boolean hasStatic = engine != null && engine.hasAnyData() && engine.storage().hasLightNearSection(secX, secY, secZ);
        boolean hasDynamic = dev.puffspark.lightframe.dynamic.DynamicLightManager.isNearSection(secX, secY, secZ);

        if (!hasStatic && !hasDynamic) {
            return;
        }

        Direction face = quad.lightFace();
        if (face == null) face = quad.cullFace();
        if (face == null) face = quad.nominalFace();

        float[] mult = new float[3];
        for (int i = 0; i < 4; i++) {
            float vx = quad.x(i);
            float vy = quad.y(i);
            float vz = quad.z(i);

            int boost = VanillaLightingBackend.sampleVertexTintAndBoost(world, currentPos, face, vx, vy, vz, mult);

            if (mult[0] < 0.999f || mult[1] < 0.999f || mult[2] < 0.999f) {
                int c = quad.color(i);
                int a = (c >> 24) & 0xFF;
                int r = Math.min(255, Math.max(0, Math.round(((c >> 16) & 0xFF) * mult[0])));
                int g = Math.min(255, Math.max(0, Math.round(((c >> 8) & 0xFF) * mult[1])));
                int b = Math.min(255, Math.max(0, Math.round((c & 0xFF) * mult[2])));
                quad.color(i, (a << 24) | (r << 16) | (g << 8) | b);
            }

            if (boost > 0) {
                int currentLight = quad.lightmap(i);
                int currentBlock = (currentLight & 0xFFFF) >> 4;
                if (boost > currentBlock) {
                    int newLight = (currentLight & 0xFFFF0000) | (boost << 4);
                    quad.lightmap(i, newLight);
                }
            }
        }
    }
}
