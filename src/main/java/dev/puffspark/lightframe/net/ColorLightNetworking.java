package dev.puffspark.lightframe.net;

import dev.puffspark.lightframe.LightFrame;
import dev.puffspark.lightframe.engine.ColorLightSource;
import dev.puffspark.lightframe.engine.EngineRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client sync of source state (authoritative server model).
 * Fabric Networking v1 (CustomPayload records), 1.21.1 style.
 */
public final class ColorLightNetworking {

    public static final byte OP_ADD = 0;
    public static final byte OP_UPDATE = 1;
    public static final byte OP_REMOVE = 2;
    public static final byte OP_BULK = 3;

    public static final byte ACTION_SPAWN_DEBUG_LIGHT = 0;
    public static final byte ACTION_REMOVE_ALL = 1;

    public record SyncPayload(byte[] data) implements CustomPayload {
        public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(LightFrame.id("sync"));
        public static final PacketCodec<PacketByteBuf, SyncPayload> CODEC = CustomPayload.codecOf(
                (value, buf) -> buf.writeByteArray(value.data()),
                buf -> new SyncPayload(buf.readByteArray())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ActionPayload(byte action) implements CustomPayload {
        public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(LightFrame.id("action"));
        public static final PacketCodec<PacketByteBuf, ActionPayload> CODEC = CustomPayload.codecOf(
                (value, buf) -> buf.writeByte(value.action()),
                buf -> new ActionPayload(buf.readByte())
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private ColorLightNetworking() {}

    public static void initCommon() {
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> handleAction(context.player(), payload.action()));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sendBulk(handler.player);
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            sendBulk(player);
        });
    }

    private static void handleAction(ServerPlayerEntity player, byte action) {
        switch (action) {
            case ACTION_SPAWN_DEBUG_LIGHT -> {
                if (player.hasPermissionLevel(2) && player.getWorld() instanceof ServerWorld world) {
                    dev.puffspark.lightframe.command.ColorLightCommands.spawnDebugLight(world, player);
                }
            }
            case ACTION_REMOVE_ALL -> {
                if (player.hasPermissionLevel(2) && player.getWorld() instanceof ServerWorld world) {
                    EngineRegistry.removeAllSources(world);
                }
            }
            default -> {}
        }
    }

    public static void sendBulk(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        List<ColorLightSource> all = new ArrayList<>();
        for (dev.puffspark.lightframe.api.ColorLight l : EngineRegistry.getAllSources(world)) {
            all.add((ColorLightSource) l);
        }
        if (all.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(OP_BULK);
        buf.writeVarInt(all.size());
        for (ColorLightSource s : all) {
            writeSource(buf, s);
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        ServerPlayNetworking.send(player, new SyncPayload(bytes));
    }

    public static void broadcastAdd(ServerWorld world, ColorLightSource s) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(OP_ADD);
        writeSource(buf, s);
        broadcast(world, buf);
    }

    public static void broadcastUpdate(ServerWorld world, ColorLightSource s) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(OP_UPDATE);
        writeSource(buf, s);
        broadcast(world, buf);
    }

    public static void broadcastRemove(ServerWorld world, UUID id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(OP_REMOVE);
        buf.writeUuid(id);
        broadcast(world, buf);
    }

    private static void broadcast(ServerWorld world, PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        SyncPayload payload = new SyncPayload(bytes);
        for (ServerPlayerEntity p : world.getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private static void writeSource(PacketByteBuf buf, ColorLightSource s) {
        buf.writeUuid(s.getId());
        buf.writeString(s.getWorldKey().getValue().toString());
        buf.writeDouble(s.getPosition().x);
        buf.writeDouble(s.getPosition().y);
        buf.writeDouble(s.getPosition().z);
        buf.writeFloat(s.getColor().r);
        buf.writeFloat(s.getColor().g);
        buf.writeFloat(s.getColor().b);
        buf.writeInt(s.getRadius());
        buf.writeFloat(s.getIntensity());
        buf.writeBoolean(s.isEnabled());
    }

    public static RegistryKey<World> parseDim(String s) {
        Identifier id = Identifier.tryParse(s);
        if (id == null) return World.OVERWORLD;
        return RegistryKey.of(RegistryKeys.WORLD, id);
    }
}
