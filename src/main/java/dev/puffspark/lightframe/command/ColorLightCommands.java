package dev.puffspark.lightframe.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.puffspark.lightframe.api.ColorLight;
import dev.puffspark.lightframe.api.ColorLightAPI;
import dev.puffspark.lightframe.api.LightColor;
import dev.puffspark.lightframe.config.ColorLightConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Test/management commands:
 *   /create_light <color> [radius] [intensity]
 *   /LightFrame list|remove <id>|clear|debug <on|off>|reload
 */
public final class ColorLightCommands {

    private static final List<String> COLOR_NAMES = List.of(
            "red", "green", "blue", "white", "purple", "yellow", "cyan", "orange", "pink", "#rrggbb");

    private static final SuggestionProvider<ServerCommandSource> COLOR_SUGGESTIONS =
            (ctx, builder) -> {
                for (String name : COLOR_NAMES) builder.suggest(name);
                return builder.buildFuture();
            };

    private static final AtomicInteger DEBUG_COLOR_CYCLE = new AtomicInteger(0);

    private ColorLightCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("create_light")
                .requires(src -> src.hasPermissionLevel(2))
                .then(argument("color", StringArgumentType.word())
                        .suggests(COLOR_SUGGESTIONS)
                        .executes(ctx -> createLight(ctx.getSource(),
                                StringArgumentType.getString(ctx, "color"), 8, 1.0f))
                        .then(argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> createLight(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "color"),
                                        IntegerArgumentType.getInteger(ctx, "radius"), 1.0f))
                                .then(argument("intensity", FloatArgumentType.floatArg(0.0f, 4.0f))
                                        .executes(ctx -> createLight(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "color"),
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                FloatArgumentType.getFloat(ctx, "intensity")))))));

        dispatcher.register(literal("create_cone_light")
                .requires(src -> src.hasPermissionLevel(2))
                .then(argument("color", StringArgumentType.word())
                        .suggests(COLOR_SUGGESTIONS)
                        .executes(ctx -> createConeLight(ctx.getSource(),
                                StringArgumentType.getString(ctx, "color"), 12, 1.5f, 25.0f, 45.0f))
                        .then(argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> createConeLight(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "color"),
                                        IntegerArgumentType.getInteger(ctx, "radius"), 1.5f, 25.0f, 45.0f))
                                .then(argument("intensity", FloatArgumentType.floatArg(0.0f, 4.0f))
                                        .executes(ctx -> createConeLight(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "color"),
                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                FloatArgumentType.getFloat(ctx, "intensity"), 25.0f, 45.0f))
                                        .then(argument("innerAngle", FloatArgumentType.floatArg(1.0f, 180.0f))
                                                .then(argument("outerAngle", FloatArgumentType.floatArg(1.0f, 180.0f))
                                                        .executes(ctx -> createConeLight(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "color"),
                                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                                FloatArgumentType.getFloat(ctx, "intensity"),
                                                                FloatArgumentType.getFloat(ctx, "innerAngle"),
                                                                FloatArgumentType.getFloat(ctx, "outerAngle")))))))));

        dispatcher.register(literal("lightframe")
                .requires(src -> src.hasPermissionLevel(0))
                .then(literal("list").executes(ctx -> list(ctx.getSource())))
                .then(literal("clear")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> clear(ctx.getSource())))
                .then(literal("remove")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(argument("id", StringArgumentType.word()).executes(ctx ->
                                remove(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(literal("debug")
                        .then(literal("on").executes(ctx -> setDebug(ctx.getSource(), true)))
                        .then(literal("off").executes(ctx -> setDebug(ctx.getSource(), false))))
                .then(literal("reload")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ColorLightConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.literal("[LightFrame] config reloaded"), false);
                            return 1;
                        })));
    }

    private static int createLight(ServerCommandSource src, String colorArg, int radius, float intensity) {
        LightColor color = resolveColor(colorArg);
        if (color == null) {
            src.sendError(Text.literal("Unknown color '" + colorArg + "'. Try: " + String.join(", ", COLOR_NAMES)));
            return 0;
        }
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            src.sendError(Text.literal("An in-game player is required."));
            return 0;
        }

        try {
            ServerWorld world = src.getWorld();
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d pos = eye.add(look.x * 3.0, look.y * 3.0, look.z * 3.0);
            pos = adjustToOpenAir(world, pos);
            ColorLight light = ColorLightAPI.create(world, pos, color, radius, intensity);
            if (light == null) {
                src.sendError(Text.literal("Light source limit reached (maxLightSources)."));
                return 0;
            }
            src.sendFeedback(() -> Text.literal(String.format(
                    "[Colored Lights] created %s r=%d i=%.1f id=%s",
                    color, radius, intensity, light.getId())), false);
            return 1;
        } catch (Exception e) {
            src.sendError(Text.literal("Error creating light source: " + e.getMessage()));
            return 0;
        }
    }

    private static int createConeLight(ServerCommandSource src, String colorStr, int radius, float intensity,
                                       float innerAngle, float outerAngle) {
        LightColor color = resolveColor(colorStr);
        if (color == null) {
            src.sendError(Text.literal("Unknown color: '" + colorStr + "'"));
            return 0;
        }

        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.literal("An in-game player is required."));
            return 0;
        }

        try {
            ServerWorld world = src.getWorld();
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d pos = eye.add(look.x * 1.5, look.y * 1.5, look.z * 1.5);
            pos = adjustToOpenAir(world, pos);
            ColorLight light = ColorLightAPI.createDirectional(world, pos, look, innerAngle, outerAngle, color, radius, intensity);
            if (light == null) {
                src.sendError(Text.literal("Light source limit reached (maxLightSources)."));
                return 0;
            }
            src.sendFeedback(() -> Text.literal(String.format(
                    "[Colored Lights] created directional cone %s r=%d i=%.1f cone=[%.0f°-%.0f°] id=%s",
                    color, radius, intensity, innerAngle, outerAngle, light.getId())), false);
            return 1;
        } catch (Exception e) {
            src.sendError(Text.literal("Error creating cone light: " + e.getMessage()));
            return 0;
        }
    }

    private static int list(ServerCommandSource src) {
        var all = ColorLightAPI.getAll(src.getWorld());
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("[Colored Lights] no light sources in this dimension"), false);
            return 0;
        }
        src.sendFeedback(() -> Text.literal("[Colored Lights] " + all.size() + " source(s):"), false);
        for (ColorLight l : all) {
            String dirStr = l.isDirectional() ? String.format(Locale.ROOT, " cone=[%.0f°-%.0f°]", l.getInnerAngle(), l.getOuterAngle()) : "";
            src.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                    "  %s %s radius=%d intensity=%.2f pos=(%.1f, %.1f, %.1f)%s%s",
                    l.getId(), l.getColor(), l.getRadius(), l.getIntensity(),
                    l.getPosition().x, l.getPosition().y, l.getPosition().z,
                    dirStr,
                    l.isEnabled() ? "" : "  [disabled]")), false);
        }
        return all.size();
    }

    private static int clear(ServerCommandSource src) {
        int n = ColorLightAPI.count(src.getWorld());
        ColorLightAPI.removeAll(src.getWorld());
        src.sendFeedback(() -> Text.literal("[LightFrame] removed " + n + " source(s)"), false);
        return n;
    }

    private static int remove(ServerCommandSource src, String idArg) {
        try {
            var id = java.util.UUID.fromString(idArg);
            boolean ok = ColorLightAPI.remove(src.getWorld(), id);
            if (ok) {
                src.sendFeedback(() -> Text.literal("[LightFrame] removed " + id), false);
                return 1;
            }
            src.sendError(Text.literal("No such source: " + id));
            return 0;
        } catch (IllegalArgumentException e) {
            src.sendError(Text.literal("Id must be a UUID (see /lightframe list)."));
            return 0;
        }
    }

    private static int setDebug(ServerCommandSource src, boolean on) {
        ColorLightConfig.get().debugMode = on;
        ColorLightConfig.save();
        src.sendFeedback(() -> Text.literal("[LightFrame] debug " + (on ? "on" : "off")), false);
        return 1;
    }

    /** Called from debug action packet (admin only). */
    public static void spawnDebugLight(ServerWorld world, ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2)) return;
        float[] cycle = {
                1.0f, 0.0f, 0.0f,   // red
                0.0f, 1.0f, 0.0f,   // green
                0.0f, 0.0f, 1.0f,   // blue
                1.0f, 1.0f, 1.0f,   // white
                1.0f, 0.0f, 1.0f    // purple
        };
        int idx = DEBUG_COLOR_CYCLE.getAndIncrement() % 5;
        LightColor color = LightColor.of(cycle[idx * 3], cycle[idx * 3 + 1], cycle[idx * 3 + 2]);
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d pos = eye.add(look.x * 2.5, look.y * 2.5, look.z * 2.5);
        pos = adjustToOpenAir(world, pos);
        ColorLight created = ColorLightAPI.create(world, pos, color, 8, 1.0f);
        if (created == null) {
            player.sendMessage(Text.literal("[LightFrame] source limit reached"), false);
        }
    }

    private static Vec3d adjustToOpenAir(ServerWorld world, Vec3d pos) {
        net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.ofFloored(pos);
        int attempts = 0;
        while (attempts < 5 && world.getBlockState(bp).isOpaqueFullCube(world, bp)) {
            bp = bp.up();
            pos = new Vec3d(pos.x, bp.getY() + 0.5, pos.z);
            attempts++;
        }
        return pos;
    }

    private static LightColor resolveColor(String arg) {
        if (arg.startsWith("#")) return LightColor.parse(arg);
        return LightColor.byName(arg);
    }
}

