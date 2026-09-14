package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.render.VanillaLightingBackend;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity tint hook (Stage 3, experimental, behind config tintEntities).
 *
 * <p>Target: {@code EntityRenderDispatcher.render(Entity, DDDFF, MatrixStack,
 * VertexConsumerProvider, int)} — the funnel for all entity rendering.
 * The VertexConsumerProvider argument is wrapped so model colors are multiplied
 * by the RGB light sampled at the entity's center.</p>
 *
 * <p>Conflicts: mods that also wrap the VCP here compose (both multiply).
 * If the TL capture loses a race, the entity simply renders untinted.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Unique
    private static final ThreadLocal<double[]> CL_POS = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<int[]> CL_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void cl$capture(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                            CallbackInfo ci) {
        int[] depth = CL_DEPTH.get();
        if (depth[0] == 0) {
            double[] pos = CL_POS.get();
            if (pos == null) {
                pos = new double[3];
                CL_POS.set(pos);
            }
            pos[0] = entity.getX();
            pos[1] = entity.getY() + entity.getHeight() * 0.5;
            pos[2] = entity.getZ();
        }
        depth[0]++;
    }

    @ModifyVariable(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private VertexConsumerProvider cl$wrap(VertexConsumerProvider vertexConsumers) {
        if (!ColorLightConfig.get().tintEntities) return vertexConsumers;
        double[] pos = CL_POS.get();
        if (pos == null) return vertexConsumers;
        return VanillaLightingBackend.wrapEntityProvider(vertexConsumers, pos[0], pos[1], pos[2]);
    }

    @Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void cl$clear(Entity entity, double x, double y, double z, float yaw, float tickDelta,
                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                          CallbackInfo ci) {
        int[] depth = CL_DEPTH.get();
        depth[0]--;
        if (depth[0] <= 0) {
            depth[0] = 0;
            CL_POS.remove();
        }
    }
}

