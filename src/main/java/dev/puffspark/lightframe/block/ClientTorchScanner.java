package dev.puffspark.lightframe.block;

import dev.puffspark.lightframe.engine.ColorLightSource;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.engine.RGBLightEngine;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side scanner and manager for colored torches placed in the terrain.
 * Essential for replay viewers (Flashback / ReplayMod) and camera flybys where
 * no server exists to broadcast block light sync packets.
 */
public final class ClientTorchScanner {

    private static final Map<ChunkPos, LongSet> CHUNK_TORCHES = new ConcurrentHashMap<>();
    private static final Long2ObjectOpenHashMap<UUID> LOCAL_TORCH_SOURCES = new Long2ObjectOpenHashMap<>();
    private static World lastScannedWorld = null;

    private ClientTorchScanner() {}

    private static int scanCooldown = 0;

    /**
     * Periodically checks for newly loaded chunks around the camera (e.g. when replay chunks stream in).
     */
    public static void tick(World world) {
        if (world == null || !world.isClient()) return;
        if (lastScannedWorld != world) {
            lastScannedWorld = world;
            clear();
            scanCooldown = 0;
        }
        if (--scanCooldown <= 0) {
            scanCooldown = 15; // Rescan every ~15 frames for newly streamed chunks
            scanLoadedChunksAroundCamera(world);
        }
    }

    public static void scanLoadedChunksAroundCamera(World world) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos cameraPos = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                ? mc.gameRenderer.getCamera().getBlockPos()
                : (mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN);

        int cx = cameraPos.getX() >> 4;
        int cz = cameraPos.getZ() >> 4;
        int viewDist = mc.options != null && mc.options.getViewDistance() != null
                ? mc.options.getViewDistance().getValue()
                : 12;

        for (int dx = -viewDist; dx <= viewDist; dx++) {
            for (int dz = -viewDist; dz <= viewDist; dz++) {
                ChunkPos cpos = new ChunkPos(cx + dx, cz + dz);
                if (CHUNK_TORCHES.containsKey(cpos)) continue;

                net.minecraft.world.chunk.Chunk chunk = world.getChunk(cpos.x, cpos.z, ChunkStatus.FULL, false);
                if (chunk instanceof WorldChunk wc) {
                    onChunkLoaded(world, wc);
                }
            }
        }
    }

    public static void onChunkLoaded(World world, WorldChunk chunk) {
        if (!world.isClient()) return;
        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        if (engine == null) return;

        ChunkPos cpos = chunk.getPos();
        if (CHUNK_TORCHES.containsKey(cpos)) return;

        ChunkSection[] sections = chunk.getSectionArray();
        LongSet inThisChunk = new LongOpenHashSet();

        int startX = cpos.getStartX();
        int startZ = cpos.getStartZ();

        for (int sIndex = 0; sIndex < sections.length; sIndex++) {
            ChunkSection section = sections[sIndex];
            if (section == null || section.isEmpty()) continue;

            if (!section.hasAny(state -> state.getBlock() instanceof ColoredTorchBlock ||
                                         state.getBlock() instanceof ColoredWallTorchBlock)) {
                continue;
            }

            int secY = chunk.sectionIndexToCoord(sIndex);
            int startY = secY << 4;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        TorchColor color = null;
                        if (state.getBlock() instanceof ColoredTorchBlock torch) {
                            color = torch.getTorchColor();
                        } else if (state.getBlock() instanceof ColoredWallTorchBlock wallTorch) {
                            color = wallTorch.getTorchColor();
                        }

                        if (color != null) {
                            BlockPos pos = new BlockPos(startX + x, startY + y, startZ + z);
                            long posKey = pos.asLong();
                            inThisChunk.add(posKey);
                            registerTorch(world, engine, pos, color);
                        }
                    }
                }
            }
        }

        if (!inThisChunk.isEmpty()) {
            CHUNK_TORCHES.put(cpos, inThisChunk);
        } else {
            CHUNK_TORCHES.put(cpos, it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET);
        }
    }

    public static void onChunkUnloaded(World world, int chunkX, int chunkZ) {
        if (!world.isClient()) return;
        ChunkPos cpos = new ChunkPos(chunkX, chunkZ);
        LongSet torchKeys = CHUNK_TORCHES.remove(cpos);
        if (torchKeys == null || torchKeys.isEmpty()) return;

        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        for (long key : torchKeys) {
            UUID id = LOCAL_TORCH_SOURCES.remove(key);
            if (id != null && engine != null) {
                engine.sources().byId(id).ifPresent(ColorLightSource::remove);
            }
        }
    }

    public static void registerTorch(World world, BlockPos pos, TorchColor color) {
        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        if (engine != null) {
            registerTorch(world, engine, pos, color);
        }
    }

    private static void registerTorch(World world, RGBLightEngine engine, BlockPos pos, TorchColor color) {
        long key = pos.asLong();
        UUID existingId = LOCAL_TORCH_SOURCES.get(key);
        if (existingId != null && engine.sources().byId(existingId).isPresent()) {
            return; // already active
        }

        // Deterministic UUID for block position: prevents duplicates and collision
        UUID id = new UUID(key, 0x4C4947485446524DL); // "LIGHTFRM"

        // Also check if server already synced a source at this position
        if (engine.sources().byId(id).isPresent()) {
            LOCAL_TORCH_SOURCES.put(key, id);
            return;
        }

        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5);
        ColorLightSource s = engine.createSource(center, color.lightColor(), 12, 1.0f, id);
        if (s != null) {
            LOCAL_TORCH_SOURCES.put(key, id);
            EngineRegistry.noteDataPresent();
        }
    }

    public static void removeTorch(World world, BlockPos pos) {
        if (!world.isClient()) return;
        long key = pos.asLong();
        UUID id = LOCAL_TORCH_SOURCES.remove(key);
        if (id == null) return;

        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        if (engine == null) return;
        engine.sources().byId(id).ifPresent(ColorLightSource::remove);
    }

    public static void clear() {
        CHUNK_TORCHES.clear();
        LOCAL_TORCH_SOURCES.clear();
        lastScannedWorld = null;
    }
}
