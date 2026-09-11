package dev.puffspark.lightframe;

import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.debug.DebugHud;
import dev.puffspark.lightframe.debug.LightDebugRenderer;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.iris.IrisCompat;
import dev.puffspark.lightframe.net.ColorLightNetworking;
import dev.puffspark.lightframe.render.ClientEngineListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client entry point: client engine lifecycle, network receiver, keybinds,
 * debug rendering + HUD, rebuild throttling.
 */
public final class LightFrameClient implements ClientModInitializer {

    private static KeyBinding debugKey;
    private static KeyBinding spawnKey;

    @Override
    public void onInitializeClient() {
        EngineRegistry.setListenerFactory(world -> {
            if (world instanceof net.minecraft.client.world.ClientWorld clientWorld) {
                ClientEngineListener listener = new ClientEngineListener(clientWorld);
                ClientEngineListener.setCurrent(listener);
                return listener;
            }
            return dev.puffspark.lightframe.engine.EngineListener.EMPTY;
        });

        ClientPlayNetworking.registerGlobalReceiver(ColorLightNetworking.SYNC,
                (client, handler, buf, responseSender) -> {
                    List<java.util.function.Consumer<World>> ops = readOps(buf);
                    client.execute(() -> applyOps(client, ops));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
                .register((handler, client) -> {
                    dev.puffspark.lightframe.dynamic.DynamicLightManager.clear();
                    EngineRegistry.clearClientEngines();
                    var listener = ClientEngineListener.current();
                    if (listener != null) listener.clear();
                });

        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            var engine = EngineRegistry.engineOrNull(world);
            if (engine != null) engine.onChunkUnload(chunk.getPos().x, chunk.getPos().z);
        });
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            var engine = EngineRegistry.engineOrNull(world);
            if (engine != null && engine.sources().size() > 0) engine.onChunkLoaded(chunk.getPos().x, chunk.getPos().z);
        });

        debugKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lightframe.toggle_debug", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.lightframe"));
        spawnKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lightframe.spawn_test_light", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.lightframe"));

        LightDebugRenderer.register();
        DebugHud.register();

        for (dev.puffspark.lightframe.block.TorchColor color : dev.puffspark.lightframe.block.TorchColor.values()) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                    dev.puffspark.lightframe.block.ModBlocks.TORCHES.get(color),
                    net.minecraft.client.render.RenderLayer.getCutout()
            );
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                    dev.puffspark.lightframe.block.ModBlocks.WALL_TORCHES.get(color),
                    net.minecraft.client.render.RenderLayer.getCutout()
            );
        }

        LightFrame.LOGGER.info("LightFrame client initialized (Iris present: {})", IrisCompat.isIrisPresent());
    }

    private static void tick(MinecraftClient client) {
        while (debugKey.wasPressed()) {
            ColorLightConfig cfg = ColorLightConfig.get();
            cfg.debugMode = !cfg.debugMode;
            ColorLightConfig.save();
        }
        while (spawnKey.wasPressed()) {
            PacketByteBuf actionBuf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            actionBuf.writeByte(ColorLightNetworking.ACTION_SPAWN_DEBUG_LIGHT);
            ClientPlayNetworking.send(ColorLightNetworking.ACTION, actionBuf);
        }

        if (client.world != null) {
            dev.puffspark.lightframe.dynamic.DynamicLightManager.tick(client);

            var engine = EngineRegistry.engineOrNull(client.world);
            if (engine != null && engine.queuedOps() > 0) {
                engine.tick();
            }
            var listener = ClientEngineListener.current();
            if (listener != null) {
                listener.flush();
            }
        }
    }

    // ------------------------------------------------------------------ network apply

    private static List<java.util.function.Consumer<World>> readOps(PacketByteBuf buf) {
        List<java.util.function.Consumer<World>> ops = new ArrayList<>();
        byte op = buf.readByte();
        if (op == ColorLightNetworking.OP_BULK) {
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                ops.add(readSource(buf));
            }
        } else if (op == ColorLightNetworking.OP_REMOVE) {
            UUID id = buf.readUuid();
            ops.add(world -> EngineRegistry.removeSourceLocal(world, id));
        } else if (op == ColorLightNetworking.OP_ADD || op == ColorLightNetworking.OP_UPDATE) {
            ops.add(readSource(buf));
        }
        return ops;
    }

    private static java.util.function.Consumer<World> readSource(PacketByteBuf buf) {
        UUID id = buf.readUuid();
        String dim = buf.readString();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float r = buf.readFloat();
        float g = buf.readFloat();
        float b = buf.readFloat();
        int radius = buf.readInt();
        float intensity = buf.readFloat();
        boolean enabled = buf.readBoolean();
        return world -> EngineRegistry.upsertLocalSource(world, id, dim, x, y, z, r, g, b, radius, intensity, enabled);
    }

    private static void applyOps(MinecraftClient client, List<java.util.function.Consumer<World>> ops) {
        if (client.world == null) return;
        for (java.util.function.Consumer<World> op : ops) {
            op.accept(client.world);
        }
    }
}

