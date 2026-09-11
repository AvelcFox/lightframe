package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.render.VanillaLightingBackend;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluid tint hook: water/lava quads bypass BlockModelRenderer and go through
 * {@code FluidRenderer.render(BlockRenderView, BlockPos, VertexConsumer, BlockState, FluidState)}.
 * Same wrapping strategy as {@link BlockModelRendererMixin}.
 */
@Mixin(FluidRenderer.class)
public class FluidRendererMixin {

    @Unique
    private static final ThreadLocal<BlockPos> CL_POS = new ThreadLocal<>();

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V",
            at = @At("HEAD"))
    private void cl$capturePos(BlockRenderView world, BlockPos pos, VertexConsumer consumer,
                               BlockState state, FluidState fluidState, CallbackInfo ci) {
        CL_POS.set(pos);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private VertexConsumer cl$wrapConsumer(VertexConsumer consumer) {
        BlockPos pos = CL_POS.get();
        if (pos == null) return consumer;
        return VanillaLightingBackend.wrapBlock(consumer, pos);
    }

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V",
            at = @At("RETURN"))
    private void cl$clearPos(BlockRenderView world, BlockPos pos, VertexConsumer consumer,
                             BlockState state, FluidState fluidState, CallbackInfo ci) {
        CL_POS.remove();
    }
}

