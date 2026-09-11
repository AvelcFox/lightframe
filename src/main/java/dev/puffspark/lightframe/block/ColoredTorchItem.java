package dev.puffspark.lightframe.block;

import net.minecraft.block.Block;
import net.minecraft.item.VerticallyAttachableBlockItem;
import net.minecraft.util.math.Direction;

public class ColoredTorchItem extends VerticallyAttachableBlockItem {

    private final TorchColor torchColor;

    public ColoredTorchItem(Block standingBlock, Block wallBlock, Settings settings, TorchColor torchColor) {
        super(standingBlock, wallBlock, settings, Direction.DOWN);
        this.torchColor = torchColor;
    }

    public TorchColor getTorchColor() {
        return torchColor;
    }
}

