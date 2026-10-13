package io.github.partywith8.epd.client.mixin;
import io.github.partywith8.epd.client.TerrainRenderer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumTerrainVisibility {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void
    epd$surface(int x, int y, int z, CallbackInfoReturnable<BlockState> ci) {
        if (TerrainRenderer.hide(ci.getReturnValue(), x, y, z))
            ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
