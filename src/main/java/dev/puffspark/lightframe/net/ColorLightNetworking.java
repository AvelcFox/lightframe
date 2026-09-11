package dev.puffspark.lightframe.net;

import dev.puffspark.lightframe.LightFrame;
import dev.puffspark.lightframe.engine.ColorLightSource;
import dev.puffspark.lightframe.engine.EngineRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
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
 * Fabric Networking v1 (PacketByteBuf payloads), 1.20.1 style.
 */
public final class ColorLightNetworking {

    public static final Identifier SYNC = LightFrame.id("sync");
    public static final Identifier ACTION = LightFrame.id("action");

    public static final byte OP_ADD = 0;
    public static final byte OP_UPDATE = 1;
    public static final byte OP_REMOVE = 2;
    public static final byte OP_BULK = 3;

    public static final byte ACTION_SPAWN_DEBUG_LIGHT = 0;
    public static final byte ACTION_REMOVE_ALL = 1;

    private ColorLightNetworking() {}

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, responseSender) -> {
            byte action = buf.readByte();
            server.execute(() -> handleAction(player, action));
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
                if (player.getWorld() instanceof ServerWorld world) {
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
        ServerPlayNetworking.send(player, SYNC, buf);
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

    private static void broadcast(ServerWorld world, PacketByteBuf payload) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            ServerPlayNetworking.send(p, SYNC, new PacketByteBuf(payload.copy()));
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

