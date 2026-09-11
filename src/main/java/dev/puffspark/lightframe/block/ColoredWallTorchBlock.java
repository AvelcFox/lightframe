package dev.puffspark.lightframe.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class ColoredWallTorchBlock extends WallTorchBlock {

    protected final TorchColor torchColor;

    public ColoredWallTorchBlock(Settings settings, TorchColor torchColor) {
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
        Direction direction = state.get(FACING);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.7;
        double z = pos.getZ() + 0.5;

        Direction direction2 = direction.getOpposite();
        double offset = 0.27;
        double px = x + offset * (double) direction2.getOffsetX();
        double py = y + 0.22;
        double pz = z + offset * (double) direction2.getOffsetZ();

        world.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0);
        world.addParticle(new DustParticleEffect(new Vector3f(torchColor.getR(), torchColor.getG(), torchColor.getB()), 0.7f),
                px, py, pz, 0.0, 0.02, 0.0);
        world.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0);
    }
}

