package dev.puffspark.lightframe.block;

import dev.puffspark.lightframe.LightFrame;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {

    public static final Map<TorchColor, ColoredTorchBlock> TORCHES = new EnumMap<>(TorchColor.class);
    public static final Map<TorchColor, ColoredWallTorchBlock> WALL_TORCHES = new EnumMap<>(TorchColor.class);
    public static final Map<TorchColor, ColoredTorchItem> TORCH_ITEMS = new EnumMap<>(TorchColor.class);

    private ModBlocks() {}

    public static void register() {
        for (TorchColor color : TorchColor.values()) {
            String name = color.getName();

            ColoredTorchBlock standing = Registry.register(
                    Registries.BLOCK,
                    LightFrame.id(name + "_torch"),
                    new ColoredTorchBlock(FabricBlockSettings.copyOf(Blocks.TORCH), color)
            );

            ColoredWallTorchBlock wall = Registry.register(
                    Registries.BLOCK,
                    LightFrame.id(name + "_wall_torch"),
                    new ColoredWallTorchBlock(FabricBlockSettings.copyOf(Blocks.WALL_TORCH).dropsLike(standing), color)
            );

            ColoredTorchItem item = Registry.register(
                    Registries.ITEM,
                    LightFrame.id(name + "_torch"),
                    new ColoredTorchItem(standing, wall, new FabricItemSettings(), color)
            );

            TORCHES.put(color, standing);
            WALL_TORCHES.put(color, wall);
            TORCH_ITEMS.put(color, item);
        }

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            for (TorchColor color : TorchColor.values()) {
                entries.add(TORCH_ITEMS.get(color));
            }
        });
    }
}

