package dev.puffspark.lightframe.block;

import dev.puffspark.lightframe.LightFrame;
import dev.puffspark.lightframe.engine.ColorLightSource;
import dev.puffspark.lightframe.engine.EngineRegistry;
import dev.puffspark.lightframe.engine.RGBLightEngine;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Client-side scanner and manager for colored torches placed in the terrain.
 * Essential for replay viewers (Flashback / ReplayMod) and camera flybys where
 * no server exists to broadcast block light sync packets.
 */
public final class ClientTorchScanner {

    private static final Set<ChunkPos> SCANNED_CHUNKS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<ChunkPos, LongSet> CHUNK_TORCHES = new ConcurrentHashMap<>();
    private static final Map<Long, UUID> LOCAL_TORCH_SOURCES = new ConcurrentHashMap<>();
    private static World lastScannedWorld = null;
    private static int scanCooldown = 0;

    private static Field chunkMapField = null;
    private static Field arrayField = null;
    private static boolean reflectionFailed = false;

    private ClientTorchScanner() {}

    /**
     * Called on every rendered frame to ensure chunks are scanned and updated.
     */
    public static void tick(World world) {
        if (world == null || !world.isClient()) return;
        if (lastScannedWorld != world) {
            clear();
            lastScannedWorld = world;
            scanCooldown = 0;
        }
        if (--scanCooldown <= 0) {
            scanCooldown = 15; // Scan every ~15 frames for any newly streamed chunks
            scanLoadedChunks(world);
        }
    }

    private static void initReflection(ClientChunkManager cm) {
        if (chunkMapField != null || reflectionFailed) return;
        try {
            for (Field f : ClientChunkManager.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                        && f.getType() != WorldChunk.class
                        && f.getType() != ClientWorld.class
                        && f.getType() != LightingProvider.class) {
                    f.setAccessible(true);
                    chunkMapField = f;
                    break;
                }
            }
            if (chunkMapField != null) {
                Object mapObj = chunkMapField.get(cm);
                if (mapObj != null) {
                    for (Field f : mapObj.getClass().getDeclaredFields()) {
                        if (AtomicReferenceArray.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            arrayField = f;
                            break;
                        }
                    }
                }
            }
            if (chunkMapField != null && arrayField != null) {
                LightFrame.LOGGER.info("ClientTorchScanner: reflection successfully hooked into ClientChunkMap");
            } else {
                LightFrame.LOGGER.warn("ClientTorchScanner: reflection failed to locate chunk array fields");
                reflectionFailed = true;
            }
        } catch (Throwable t) {
            LightFrame.LOGGER.warn("ClientTorchScanner: error initializing reflection: {}", t.getMessage());
            reflectionFailed = true;
        }
    }

    /**
     * Directly iterates all chunks currently in client memory from the internal ClientChunkMap array.
     * Independent of camera position, view distance, or player position.
     */
    @SuppressWarnings("unchecked")
    public static void scanLoadedChunks(World world) {
        if (!(world.getChunkManager() instanceof ClientChunkManager cm)) return;
        initReflection(cm);

        int scanned = 0;
        if (!reflectionFailed && chunkMapField != null && arrayField != null) {
            try {
                Object mapObj = chunkMapField.get(cm);
                if (mapObj != null) {
                    AtomicReferenceArray<WorldChunk> array = (AtomicReferenceArray<WorldChunk>) arrayField.get(mapObj);
                    if (array != null) {
                        int len = array.length();
                        for (int i = 0; i < len; i++) {
                            WorldChunk wc = array.get(i);
                            if (wc != null) {
                                onChunkLoaded(world, wc);
                                scanned++;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LightFrame.LOGGER.warn("ClientTorchScanner: error scanning chunk array: {}", t.getMessage());
            }
        }

        // Complementary fallback: also scan around camera
        if (scanned == 0) {
            scanAroundCamera(world);
        }
    }

    private static void scanAroundCamera(World world) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera cam = mc.gameRenderer != null ? mc.gameRenderer.getCamera() : null;
        Vec3d pos = (cam != null && cam.isReady()) ? cam.getPos()
                : (mc.player != null ? mc.player.getPos() : null);
        if (pos == null) return;

        int centerChunkX = ChunkSectionPos.getSectionCoord(pos.x);
        int centerChunkZ = ChunkSectionPos.getSectionCoord(pos.z);
        int radius = 16;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                net.minecraft.world.chunk.Chunk c = world.getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, false);
                if (c instanceof WorldChunk wc) {
                    onChunkLoaded(world, wc);
                }
            }
        }
    }

    public static void onChunkLoaded(World world, WorldChunk chunk) {
        if (!world.isClient() || chunk == null) return;
        ChunkPos cpos = chunk.getPos();
        if (SCANNED_CHUNKS.contains(cpos)) return;

        ChunkSection[] sections = chunk.getSectionArray();
        if (sections == null || sections.length == 0) return;

        // Ensure chunk is actually populated with blocks before marking it scanned
        boolean hasPopulatedSection = false;
        for (ChunkSection section : sections) {
            if (section != null && !section.isEmpty()) {
                hasPopulatedSection = true;
                break;
            }
        }
        if (!hasPopulatedSection) {
            // Wait until the chunk has block data loaded
            return;
        }

        // Ensure the client engine is initialized!
        RGBLightEngine engine = EngineRegistry.engineFor(world);
        if (engine == null) return;

        SCANNED_CHUNKS.add(cpos);

        LongSet inThisChunk = new LongOpenHashSet();
        int startX = cpos.getStartX();
        int startZ = cpos.getStartZ();

        for (int sIndex = 0; sIndex < sections.length; sIndex++) {
            ChunkSection section = sections[sIndex];
            if (section == null || section.isEmpty()) continue;

            // Super fast palette check: 99% of sections skip here with zero block loop overhead
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
            LightFrame.LOGGER.info("ClientTorchScanner: found {} colored torches in chunk [{}, {}]", inThisChunk.size(), cpos.x, cpos.z);
        }
    }

    public static void onChunkUnloaded(World world, int chunkX, int chunkZ) {
        if (!world.isClient()) return;
        ChunkPos cpos = new ChunkPos(chunkX, chunkZ);
        SCANNED_CHUNKS.remove(cpos);
        LongSet torchKeys = CHUNK_TORCHES.remove(cpos);
        if (torchKeys == null || torchKeys.isEmpty()) return;

        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        for (long key : torchKeys) {
            UUID id = LOCAL_TORCH_SOURCES.remove(key);
            if (id == null) {
                id = new UUID(key, 0x4C4947485446524DL);
            }
            if (engine != null) {
                engine.sources().byId(id).ifPresent(ColorLightSource::remove);
            }
        }
    }

    public static void registerTorch(World world, BlockPos pos, TorchColor color) {
        RGBLightEngine engine = EngineRegistry.engineFor(world);
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
        if (id == null) {
            id = new UUID(key, 0x4C4947485446524DL);
        }

        RGBLightEngine engine = EngineRegistry.engineOrNull(world);
        if (engine == null) return;
        engine.sources().byId(id).ifPresent(ColorLightSource::remove);
    }

    public static void clear() {
        SCANNED_CHUNKS.clear();
        CHUNK_TORCHES.clear();
        LOCAL_TORCH_SOURCES.clear();
        lastScannedWorld = null;
        chunkMapField = null;
        arrayField = null;
        reflectionFailed = false;
    }
}
