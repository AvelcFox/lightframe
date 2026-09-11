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
                    TorchPersistentState::fromNbt, TorchPersistentState::new, "LightFrame_torches");
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
                TorchPersistentState::fromNbt, TorchPersistentState::new, "LightFrame_torches");
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

    public static class TorchPersistentState extends PersistentState {
        final Map<BlockPos, TorchColor> torches = new ConcurrentHashMap<>();

        public TorchPersistentState() {}

        public static TorchPersistentState fromNbt(NbtCompound tag) {
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
        public NbtCompound writeNbt(NbtCompound nbt) {
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

