package dev.puffspark.lightframe.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.TorchBlock;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class ColoredTorchBlock extends TorchBlock {

    protected final TorchColor torchColor;

    public ColoredTorchBlock(Settings settings, TorchColor torchColor) {
        super(ParticleTypes.FLAME, settings);
        this.torchColor = torchColor;
    }

    public TorchColor getTorchColor() {
        return torchColor;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient() && !oldState.isOf(state.getBlock())) {
            BlockLightManager.onTorchPlaced(world, pos, torchColor);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient() && !state.isOf(newState.getBlock())) {
            BlockLightManager.onTorchRemoved(world, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5;

        world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.0, 0.0);
        world.addParticle(new DustParticleEffect(new Vector3f(torchColor.getR(), torchColor.getG(), torchColor.getB()), 0.7f),
                x, y, z, 0.0, 0.02, 0.0);
        world.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
    }
}

