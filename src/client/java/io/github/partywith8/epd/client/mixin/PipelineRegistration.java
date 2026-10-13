package io.github.partywith8.epd.client.mixin;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface PipelineRegistration {
    @Invoker("register")
    static RenderPipeline epd$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
