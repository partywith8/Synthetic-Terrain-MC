package io.github.partywith8.epd.client.mixin;
import io.github.partywith8.epd.client.TerrainRenderer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class TerrainVisibility {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void epd$surface(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
        if (TerrainRenderer.hide(ci.getReturnValue(), pos.getX(), pos.getY(), pos.getZ()))
            ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }
}
