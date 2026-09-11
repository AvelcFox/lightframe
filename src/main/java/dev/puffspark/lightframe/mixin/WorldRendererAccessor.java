package dev.puffspark.lightframe.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor: {@code WorldRenderer.scheduleSectionRender(BlockPos, boolean)} is
 * private in 1.20.1 — the mod needs it to mark sections dirty after light
 * changes (same primitive vanilla uses for its own light updates).
 */
@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {

    @Invoker("scheduleSectionRender")
    void cl$scheduleSectionRender(BlockPos pos, boolean important);
}

