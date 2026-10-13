package io.github.partywith8.epd.client.mixin;
import io.github.partywith8.epd.client.TerrainRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class TerrainUpdates {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void epd$dirty(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        TerrainRenderer.changed(pos);
    }
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void epd$changed(
            BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        TerrainRenderer.changed(pos);
    }
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void epd$loaded(net.minecraft.world.level.ChunkPos pos, CallbackInfo ci) {
        TerrainRenderer.changed(new BlockPos(pos.getMinBlockX(), 0, pos.getMinBlockZ()));
    }
}
