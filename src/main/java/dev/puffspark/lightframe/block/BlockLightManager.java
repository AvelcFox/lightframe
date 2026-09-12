package dev.puffspark.lightframe.block;

import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.ColorLightAPI;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockLightManager {

    private static final Map<World, Long2ObjectOpenHashMap<UUID>> WORLD_TORCHES = new ConcurrentHashMap<>();

    public static boolean hasTorch(World world, BlockPos pos) {
        Long2ObjectOpenHashMap<UUID> map = WORLD_TORCHES.get(world);
        return map != null && map.containsKey(pos.asLong());
    }

    public static void onTorchPlaced(World world, BlockPos pos, TorchColor color) {
        if (world.isClient()) return;

        Long2ObjectOpenHashMap<UUID> map = WORLD_TORCHES.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());
        long key = pos.asLong();
        UUID oldId = map.get(key);
        if (oldId != null) {
            ColorLightAPI.remove(world, oldId);
        }

        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5);
        ColorLight light = ColorLightAPI.create(world, center, color.lightColor(), 12, 1.0f);
        if (light != null) {
            map.put(key, light.getId());
            markDirty(world, pos, color, false);
        }
    }

    public static void onTorchRemoved(World world, BlockPos pos) {
        if (world.isClient()) return;

        Long2ObjectOpenHashMap<UUID> map = WORLD_TORCHES.get(world);
        if (map != null) {
            UUID id = map.remove(pos.asLong());
            if (id != null) {
                ColorLightAPI.remove(world, id);
                markDirty(world, pos, null, true);
            }
        }
    }

    private static void markDirty(World world, BlockPos pos, TorchColor color, boolean remove) {
        if (world instanceof ServerWorld serverWorld) {
            TorchPersistentState state = serverWorld.getPersistentStateManager().getOrCreate(
                    TorchPersistentState.TYPE, "lightframe_torches");
            if (remove) {
                state.remove(pos);
            } else if (color != null) {
                state.add(pos, color);
            }
            state.markDirty();
        }
    }

    public static void onServerWorldLoaded(ServerWorld world) {
        TorchPersistentState state = world.getPersistentStateManager().getOrCreate(
                TorchPersistentState.TYPE, "lightframe_torches");
        Long2ObjectOpenHashMap<UUID> map = WORLD_TORCHES.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());

        for (Map.Entry<BlockPos, TorchColor> entry : state.torches.entrySet()) {
            BlockPos pos = entry.getKey();
            TorchColor color = entry.getValue();
            Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5);
            ColorLight light = ColorLightAPI.create(world, center, color.lightColor(), 12, 1.0f);
            if (light != null) {
                map.put(pos.asLong(), light.getId());
            }
        }
    }

    public static void onChunkLoaded(ServerWorld world, net.minecraft.world.chunk.Chunk chunk) {
        net.minecraft.world.chunk.ChunkSection[] sections = chunk.getSectionArray();
        net.minecraft.util.math.ChunkPos cpos = chunk.getPos();
        int startX = cpos.getStartX();
        int startZ = cpos.getStartZ();

        for (int sIndex = 0; sIndex < sections.length; sIndex++) {
            net.minecraft.world.chunk.ChunkSection section = sections[sIndex];
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
                        net.minecraft.block.BlockState state = section.getBlockState(x, y, z);
                        TorchColor color = null;
                        if (state.getBlock() instanceof ColoredTorchBlock torch) {
                            color = torch.getTorchColor();
                        } else if (state.getBlock() instanceof ColoredWallTorchBlock wallTorch) {
                            color = wallTorch.getTorchColor();
                        }

                        if (color != null) {
                            BlockPos pos = new BlockPos(startX + x, startY + y, startZ + z);
                            if (!hasTorch(world, pos)) {
                                onTorchPlaced(world, pos, color);
                            }
                        }
                    }
                }
            }
        }
    }

    public static class TorchPersistentState extends PersistentState {
        public static final Type<TorchPersistentState> TYPE = new Type<>(
                TorchPersistentState::new,
                TorchPersistentState::fromNbt,
                null
        );

        final Map<BlockPos, TorchColor> torches = new ConcurrentHashMap<>();

        public TorchPersistentState() {}

        public static TorchPersistentState fromNbt(NbtCompound tag, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
            TorchPersistentState state = new TorchPersistentState();
            NbtList list = tag.getList("Torches", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound t = list.getCompound(i);
                BlockPos pos = BlockPos.fromLong(t.getLong("Pos"));
                String colorName = t.getString("Color");
                try {
                    TorchColor c = TorchColor.valueOf(colorName.toUpperCase());
                    state.torches.put(pos, c);
                } catch (IllegalArgumentException ignored) {}
            }
            return state;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup registryLookup) {
            NbtList list = new NbtList();
            for (Map.Entry<BlockPos, TorchColor> e : torches.entrySet()) {
                NbtCompound t = new NbtCompound();
                t.putLong("Pos", e.getKey().asLong());
                t.putString("Color", e.getValue().name());
                list.add(t);
            }
            nbt.put("Torches", list);
            return nbt;
        }

        void add(BlockPos pos, TorchColor color) {
            torches.put(pos.toImmutable(), color);
        }

        void remove(BlockPos pos) {
            torches.remove(pos);
        }
    }
}

