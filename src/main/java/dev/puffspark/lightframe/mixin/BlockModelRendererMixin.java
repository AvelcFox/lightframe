package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.render.VanillaLightingBackend;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Terrain tint hook.
 *
 * <p>Target: {@code BlockModelRenderer.render(BlockRenderView, BakedModel, BlockState, BlockPos,
 * MatrixStack, VertexConsumer, boolean, Random, long, int)} — the single funnel through which
 * every vanilla block quad enters the vertex buffer during chunk mesh rebuilds.
 * The VertexConsumer argument is wrapped in a color-multiplying decorator;
 * the tint is sampled once per block at its center.</p>
 *
 * <p>Conflicts: other mods that also wrap the consumer via ModifyVariable on this
 * method compose naturally (both wrappers multiply). See docs/ARCHITECTURE.md §7.</p>
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {

    @Unique
    private static final ThreadLocal<BlockPos> CL_POS = new ThreadLocal<>();

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"))
    private void cl$capturePos(BlockRenderView world, BakedModel model, BlockState state, BlockPos pos,
                               MatrixStack matrices, VertexConsumer consumer, boolean cull, Random random,
                               long seed, int overlay, CallbackInfo ci) {
        CL_POS.set(pos);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private VertexConsumer cl$wrapConsumer(VertexConsumer consumer) {
        BlockPos pos = CL_POS.get();
        if (pos == null) return consumer;
        return VanillaLightingBackend.wrapBlock(consumer, pos);
    }

    @Inject(method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("RETURN"))
    private void cl$clearPos(BlockRenderView world, BakedModel model, BlockState state, BlockPos pos,
                             MatrixStack matrices, VertexConsumer consumer, boolean cull, Random random,
                             long seed, int overlay, CallbackInfo ci) {
        CL_POS.remove();
    }
}

