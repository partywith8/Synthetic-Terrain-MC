package io.github.partywith8.epd.client.mixin;
import io.github.partywith8.epd.physics.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class TerrainOutline {
    @Inject(method = "extractBlockOutline", at = @At("TAIL"))
    private void epd$outline(Camera camera, LevelRenderState state, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.hitResult instanceof TerrainHit hit) {
            var pos = hit.getBlockPos();
            double offset = hit.bottom - pos.getY();
            state.blockOutlineRenderState = new BlockOutlineRenderState(pos, false,
                    mc.options.highContrastBlockOutline().get(),
                    MovingTerrain
                            .nativeQuery(() -> mc.level.getBlockState(pos).getShape(mc.level, pos))
                            .move(0, offset, 0));
        }
    }
}
