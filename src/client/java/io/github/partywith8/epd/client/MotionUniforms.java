package io.github.partywith8.epd.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.partywith8.epd.motion.MotionEvent;
import io.github.partywith8.epd.motion.MotionField;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Render-thread owner of the fixed-size motion uniform buffer shared with terrain.vsh. */
final class MotionUniforms {
    private static GpuBuffer params;
    private static final ByteBuffer BYTES =
            ByteBuffer.allocateDirect(272).order(ByteOrder.nativeOrder());
    private MotionUniforms() {}
    static GpuBuffer upload(CommandEncoder encoder, MotionField field, double tick) {
        double sample = field.resetStart() < 0 ? tick : Math.min(tick, field.resetStart());
        if (params == null)
            params = RenderSystem.getDevice().createBuffer(
                    () -> "STM motion", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 272);
        BYTES.clear();
        BYTES.putFloat(field.resetStart() < 0
                             ? 1
                             : (float) (1 - MotionEvent.ease((tick - field.resetStart()) / 240)))
                .putFloat(field.events().size())
                .putFloat(0)
                .putFloat(0);
        for (int i = 0; i < 8; i++) {
            var e = i < field.events().size() ? field.events().get(i) : null;
            BYTES.putFloat(e == null ? 0 : e.x())
                    .putFloat(e == null ? 0 : e.z())
                    .putFloat(e == null ? 1 : e.radius())
                    .putFloat(e == null ? 0 : (float) e.amplitude());
        }
        for (int i = 0; i < 8; i++) {
            var e = i < field.events().size() ? field.events().get(i) : null;
            BYTES.putFloat(e == null ? 0 : e.kind().ordinal())
                    .putFloat(e == null ? 0 : (float) (sample - e.start()))
                    .putFloat(e == null ? 1 : e.duration())
                    .putFloat(e == null ? 0 : e.axis() + (e.hold() ? 4 : 0));
        }
        BYTES.flip();
        encoder.writeToBuffer(params.slice(), BYTES);
        return params;
    }
    static void close() {
        if (params != null)
            params.close();
        params = null;
    }
}
