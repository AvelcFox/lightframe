package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.engine.RGBLightEngine;
import dev.puffspark.lightframe.render.ClientEngineListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures light propagation and chunk rebuilds execute on every rendered frame,
 * even when the game is paused or when viewing/exporting replays (ReplayMod / Flashback)
 * where regular client ticks do not run.
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Shadow
    public ClientWorld world;

    @Inject(method = "render", at = @At("HEAD"))
    private void cl$onFrameRender(boolean tick, CallbackInfo ci) {
        if (this.world != null) {
            dev.puffspark.lightframe.block.ClientTorchScanner.ensureInitialScan(this.world);
            RGBLightEngine engine = EngineRegistry.engineOrNull(this.world);
            if (engine != null && engine.queuedOps() > 0) {
                engine.tick();
            }
            ClientEngineListener listener = ClientEngineListener.current();
            if (listener != null && listener.pendingCount() > 0) {
                listener.flush();
            }
        }
    }
}
