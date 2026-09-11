package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.engine.RGBLightEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.light.BlockLightStorage;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.LightStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla-light boost hook — the intensity channel of the hybrid model.
 *
 * <p>Target: {@code ChunkLightProvider.getLightLevel(BlockPos)} — the SINGLE
 * choke point of all vanilla light queries in 1.20.1
 * ({@code BlockRenderView.getLightLevel(LightType, BlockPos)} is a default
 * method routing here; neither World nor ChunkRendererRegion override it).
 * This covers chunk mesh building (through the region snapshot), entity and
 * block entity lightmap coordinates, and gameplay queries (mob spawning).</p>
 *
 * <p>Only the block-light layer is boosted ({@code max(vanilla, lumaRGB)});
 * sky light is untouched. Filtered by storage type; early-outs on a global
 * gate when no RGB data exists anywhere. Per-instance world/engine cache
 * keeps the hot path at a couple of field reads.</p>
 *
 * <p>Conflicts: mods replacing the whole light engine (rare). Safe otherwise:
 * the hook is a pure value-max read of our own storage.</p>
 */
@Mixin(ChunkLightProvider.class)
public abstract class ChunkLightProviderMixin {

    @Shadow
    @Final
    protected ChunkProvider chunkProvider;

    @Shadow
    @Final
    protected LightStorage<?> lightStorage;

    @Unique
    @Nullable
    private World cl$cachedWorld;

    @Unique
    @Nullable
    private RGBLightEngine cl$cachedEngine;

    @Inject(method = "getLightLevel", at = @At("TAIL"), cancellable = true)
    private void cl$boostBlockLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!EngineRegistry.anyEngineHasData()) return;
        if (!(this.lightStorage instanceof BlockLightStorage)) return; // sky light untouched

        World world = this.cl$cachedWorld;
        if (world == null) {
            Object w = this.chunkProvider.getWorld();
            if (!(w instanceof World wp)) return;
            world = wp;
            this.cl$cachedWorld = wp;
        }

        RGBLightEngine engine = this.cl$cachedEngine;
        if (engine == null || engine.world() != world) {
            engine = EngineRegistry.engineOrNull(world);
            if (engine == null) return;
            this.cl$cachedEngine = engine;
        }

        ColorLightConfig cfg = ColorLightConfig.get();
        if (!cfg.enableRGBLighting || !cfg.boostVanillaLight) return;
        if (!cfg.affectGameplayLighting && !world.isClient()) return;

        int boost = engine.maxChannelAt(pos.getX(), pos.getY(), pos.getZ());
        if (boost > 0) {
            float norm = boost / 255.0f;
            int level = Math.min(15, (int) Math.round(15.0f * Math.pow(norm, 0.65)));
            if (level > cir.getReturnValueI()) {
                cir.setReturnValue(level);
            }
        }
    }
}

