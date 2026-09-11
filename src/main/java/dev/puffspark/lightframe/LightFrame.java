package dev.puffspark.lightframe;

import dev.puffspark.lightframe.command.ColorLightCommands;
import dev.puffspark.lightframe.config.ColorLightConfig;
import dev.puffspark.lightframe.engine.EngineListener;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.net.ColorLightNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colored Lights — true RGB dynamic lighting on top of the vanilla renderer.
 *
 * <p>Server-side entry point: config, networking, commands, engine lifecycle.</p>
 */
public final class LightFrame implements ModInitializer {

    public static final String MOD_ID = "lightframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ColorLightConfig.load();
        configureIndigo();

        EngineRegistry.setListenerFactory(world -> EngineListener.EMPTY);

        dev.puffspark.lightframe.block.ModBlocks.register();
        ColorLightNetworking.initCommon();
        ColorLightNetworking.registerServer();
        ColorLightCommands.register();

        ServerWorldEvents.LOAD.register((server, world) -> {
            dev.puffspark.lightframe.block.BlockLightManager.onServerWorldLoaded(world);
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            var engine = EngineRegistry.engineOrNull(world);
            if (engine != null && engine.queuedOps() > 0) {
                engine.tick();
            }
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            var engine = EngineRegistry.engineOrNull(world);
            if (engine != null) {
                engine.onChunkUnload(chunk.getPos().x, chunk.getPos().z);
            }
        });

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            var engine = EngineRegistry.engineOrNull(world);
            if (engine != null && engine.sources().size() > 0) {
                engine.onChunkLoaded(chunk.getPos().x, chunk.getPos().z);
            }
        });

        ServerWorldEvents.UNLOAD.register((server, world) -> EngineRegistry.onWorldUnload(world));

        LOGGER.info("Colored Lights initialized (vanilla backend, no shader dependency)");
    }

    private static void configureIndigo() {
        try {
            java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            java.nio.file.Path fabricDir = configDir.resolve("fabric");
            if (!java.nio.file.Files.exists(fabricDir)) {
                java.nio.file.Files.createDirectories(fabricDir);
            }
            java.nio.file.Path propsPath = fabricDir.resolve("indigo-renderer.properties");
            java.util.Properties props = new java.util.Properties();
            if (java.nio.file.Files.exists(propsPath)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(propsPath)) {
                    props.load(in);
                }
            }
            if (!"false".equalsIgnoreCase(props.getProperty("always-tesselate-blocks"))) {
                props.setProperty("always-tesselate-blocks", "false");
                try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(propsPath)) {
                    props.store(out, "Configured by Colored Lights for vanilla BlockModelRenderer tinting");
                }
                LOGGER.info("Configured fabric/indigo-renderer.properties (always-tesselate-blocks=false)");
            }
        } catch (Throwable t) {
            LOGGER.debug("Could not auto-configure Indigo properties: {}", t.getMessage());
        }
    }
}

