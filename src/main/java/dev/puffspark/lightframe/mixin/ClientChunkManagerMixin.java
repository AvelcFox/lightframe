package dev.puffspark.lightframe.mixin;

import dev.puffspark.lightframe.block.ClientTorchScanner;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {

    @Shadow
    @Final
    ClientWorld world;

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void cl$onLoadChunkFromPacket(int x, int z, PacketByteBuf buf, NbtCompound nbt,
                                         Consumer<ChunkData.BlockEntityVisitor> visitor,
                                         CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk chunk = cir.getReturnValue();
        if (chunk != null) {
            ClientTorchScanner.onChunkLoaded(this.world, chunk);
        }
    }
}
