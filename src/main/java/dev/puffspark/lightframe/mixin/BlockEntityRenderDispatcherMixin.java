package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.render.VanillaLightingBackend;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block entity tint hook (chests, bells, shulker boxes...).
 *
 * <p>Target: {@code BlockEntityRenderDispatcher.render(BlockEntity, float,
 * MatrixStack, VertexConsumerProvider)} — the funnel for all block entity
 * rendering. Same wrapping strategy as {@link EntityRenderDispatcherMixin}.</p>
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Unique
    private static final ThreadLocal<double[]> CL_POS = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<int[]> CL_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"))
    private void cl$capture(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        int[] depth = CL_DEPTH.get();
        if (depth[0] == 0) {
            BlockPos p = blockEntity.getPos();
            double[] pos = CL_POS.get();
            if (pos == null) {
                pos = new double[3];
                CL_POS.set(pos);
            }
            pos[0] = p.getX() + 0.5;
            pos[1] = p.getY() + 0.5;
            pos[2] = p.getZ() + 0.5;
        }
        depth[0]++;
    }

    @ModifyVariable(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private VertexConsumerProvider cl$wrap(VertexConsumerProvider vertexConsumers) {
        if (!ColorLightConfig.get().tintBlockEntities) return vertexConsumers;
        double[] pos = CL_POS.get();
        if (pos == null) return vertexConsumers;
        return VanillaLightingBackend.wrapProvider(vertexConsumers, pos[0], pos[1], pos[2]);
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("RETURN"))
    private void cl$clear(BlockEntity blockEntity, float tickDelta, MatrixStack matrices,
                          VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        int[] depth = CL_DEPTH.get();
        depth[0]--;
        if (depth[0] <= 0) {
            depth[0] = 0;
            CL_POS.remove();
        }
    }
}

