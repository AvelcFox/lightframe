package dev.puffspark.lightframe.debug;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.iris.IrisCompat;
import dev.puffspark.lightframe.render.ClientEngineListener;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Locale;

/**
 * Debug HUD (F3-style overlay, top-left): engine counters and timings.
 * Enabled by config debugMode or the K keybind.
 */
public final class DebugHud {

    private DebugHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
    }

    private static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!ColorLightConfig.get().debugMode || mc.world == null) return;
        var engine = EngineRegistry.engineOrNull(mc.world);
        if (engine == null) {
            drawLine(context, 0, "LightFrame: no engine (no sources yet)");
            return;
        }

        var stats = engine.stats;
        int line = 0;
        drawLine(context, line++, "LightFrame debug (Iris: " + (IrisCompat.isIrisPresent() ? "yes" : "no")
                + ", shaderpack: " + (IrisCompat.isShaderPackInUse() ? "ON — hue fallback" : "off") + ")");
        int dynCount = dev.puffspark.lightframe.dynamic.DynamicLightManager.hasActiveLights() ? 1 : 0;
        drawLine(context, line++, String.format(Locale.ROOT,
                "sources: %d (dynamic: %s) | pending ops: %d | storage sections: %d",
                stats.sources, dynCount > 0 ? "active" : "none", stats.pendingOps, stats.storageSections));
        drawLine(context, line++, String.format(Locale.ROOT,
                "last propagation: %.3f ms | total: %.1f ms | bfs runs: %d",
                stats.lastPropagationNanos / 1.0e6,
                stats.totalPropagationNanos / 1.0e6,
                stats.bfsRuns));
        drawLine(context, line++, String.format(Locale.ROOT,
                "dirty sections last op: %d | fps: %d",
                stats.lastDirtySections, mc.getCurrentFps() == 0 ? 0 : mc.getCurrentFps()));

        var listener = ClientEngineListener.current();
        if (listener != null) {
            drawLine(context, line++, String.format(Locale.ROOT,
                    "scheduled rebuilds pending: %d (budget %d/tick)",
                    listener.pendingCount(), ColorLightConfig.get().maxSectionsRebuiltPerTick));
        }
    }

    private static void drawLine(DrawContext context, int line, String text) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 500);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, 6, 6 + line * 11, 0xFFFFFF55);
        matrices.pop();
    }
}

