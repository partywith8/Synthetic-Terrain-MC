package io.github.partywith8.epd.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import io.github.partywith8.epd.client.mixin.PipelineRegistration;
import io.github.partywith8.epd.motion.MotionState;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/** Draws the existing rigid-column meshes; scheduling and uniforms have separate owners. */
public final class TerrainRenderer {
    private TerrainRenderer() {}
    private static final Identifier TEXTURE =
            Identifier.parse("minecraft:textures/block/white_concrete.png");
    private static final RenderPipeline PIPELINE = pipeline();
    static double drawMs;
    static int visible, vertices;
    public static boolean hide(BlockState block, int x, int y, int z) {
        return TerrainMeshes.hide(block, x, z);
    }
    public static void changed(BlockPos pos) {
        TerrainMeshes.changed(pos);
    }
    public static void tick() {
        TerrainMeshes.tick();
    }
    public static String diagnostics() {
        return TerrainMeshes.diagnostics();
    }
    public static void close() {
        TerrainMeshes.close();
        MotionUniforms.close();
        drawMs = 0;
        visible = 0;
        vertices = 0;
    }
    private static String passLabel() {
        return "STM terrain";
    }
    private static RenderPipeline pipeline() {
        var base = RenderPipelines.END_SKY;
        var builder =
                RenderPipeline.builder()
                        .withLocation(Identifier.parse("epd:terrain"))
                        .withVertexShader(Identifier.parse("epd:core/terrain"))
                        .withFragmentShader(Identifier.parse("epd:core/terrain"))
                        .withCull(false)
                        .withColorTargetState(base.getColorTargetState())
                        .withVertexBinding(0, MeshFormat.FORMAT)
                        .withPrimitiveTopology(PrimitiveTopology.QUADS)
                        .withDepthStencilState(RenderPipelines.SOLID_BLOCK.getDepthStencilState());
        for (var layout : base.getBindGroupLayouts())
            builder.withBindGroupLayout(layout);
        builder.withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("MotionParams", UniformType.UNIFORM_BUFFER)
                        .build());
        return PipelineRegistration.epd$register(builder.build());
    }
    public static void render(RenderTarget target, CameraRenderState camera) {
        if (!EpdClient.active || !MotionState.applies(Minecraft.getInstance().level)
                || TerrainMeshes.CACHE.isEmpty())
            return;
        if (!MeshFormat.FORMAT.equals(PIPELINE.getVertexFormatBinding(0)))
            throw new IllegalStateException("Foreign vertex layout");
        long start = System.nanoTime();
        var mc = Minecraft.getInstance();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var params = MotionUniforms.upload(encoder, MotionState.client, MotionState.time(mc.level));
        var transform = new Matrix4f(camera.viewRotationMatrix)
                                .translate((float) -camera.pos.x, (float) -camera.pos.y,
                                        (float) -camera.pos.z);
        var frustum = new FrustumIntersection(new Matrix4f(camera.projectionMatrix).mul(transform));
        var uniform = RenderSystem.getDynamicUniforms().writeTransform(transform);
        var texture = mc.getTextureManager().getTexture(TEXTURE);
        visible = 0;
        vertices = 0;
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int maxIndices = TerrainMeshes.maxIndices;
        var ib = indices.getBuffer(Math.max(6, maxIndices));
        try (var pass = encoder.createRenderPass(TerrainRenderer::passLabel,
                     target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(),
                     OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", uniform);
            pass.setUniform("MotionParams", params.slice());
            pass.bindTexture("Sampler0", texture.getTextureView(),
                    RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
            pass.setIndexBuffer(ib, indices.type());
            for (var mesh : TerrainMeshes.CACHE.values()) {
                if (mesh.vertices() == null)
                    continue;
                var b = mesh.bounds();
                if (!camera.isPanoramicMode
                        && !frustum.testAab((float) b.minX, (float) b.minY - 40, (float) b.minZ,
                                (float) b.maxX, (float) b.maxY + 64, (float) b.maxZ))
                    continue;
                pass.setVertexBuffer(0, mesh.vertices().slice());
                pass.drawIndexed(mesh.indices(), 1, 0, 0, 0);
                visible++;
                vertices += mesh.indices() / 6 * 4;
            }
        }
        TerrainMeshes.publishCoverage();
        drawMs = (System.nanoTime() - start) / 1e6;
    }
}
