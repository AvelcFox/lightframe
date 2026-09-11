package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.engine.RGBLightEngine;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Listens for block state modifications (broken, placed, replaced) so that
 * colored lighting dynamically recalculates and re-propagates into newly exposed
 * areas or is occluded by newly placed solid blocks.
 */
@Mixin(World.class)
public abstract class WorldBlockChangeMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("RETURN"))
    private void cl$onBlockStateChanged(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;

        World world = (World) (Object) this;
        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        if (engine == null) return;
        if (state.getBlock() instanceof dev.puffspark.lightframe.block.ColoredTorchBlock ||
            state.getBlock() instanceof dev.puffspark.lightframe.block.ColoredWallTorchBlock ||
            dev.puffspark.lightframe.block.BlockLightManager.hasTorch(world, pos)) {
            return;
        }

        engine.onBlockChanged(pos);
    }
}

