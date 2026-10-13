package io.github.partywith8.epd.client.mixin;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import io.github.partywith8.epd.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Add terrain after the native sky. The sky pass itself is never replaced. */
@Mixin(LevelRenderer.class)
public abstract class WorldPass {
    @Shadow @Final private LevelTargetBundle targets;
    @Inject(method = "addSkyPass", at = @At("RETURN"))
    private void epd$terrain(FrameGraphBuilder graph, CameraRenderState camera, GpuBufferSlice fog,
            CallbackInfo ci) {
        if (!EpdClient.active)
            return;
        var pass = graph.addPass("epd_terrain");
        targets.main = pass.readsAndWrites(targets.main);
        var target = targets.main;
        pass.executes(() -> TerrainRenderer.render(target.get(), camera));
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void epd$close(CallbackInfo ci) {
        TerrainRenderer.close();
    }
}
