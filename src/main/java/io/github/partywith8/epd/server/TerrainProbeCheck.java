package io.github.partywith8.epd.server;

import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.physics.MovingTerrain;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Reference equivalence and a bounded CPU/allocation microbenchmark, not an FPS benchmark. */
final class TerrainProbeCheck {
    private static volatile double sink;
    private TerrainProbeCheck() {}

    static double referenceSupport(ServerLevel level, AABB box, double tick) {
        double best = Double.NaN;
        var probe = new AABB(box.minX + 1e-6, box.minY - .8, box.minZ + 1e-6, box.maxX - 1e-6,
                box.minY + .8, box.maxZ - 1e-6);
        for (var shape : MovingTerrain.shapes(level, probe, tick)) {
            for (var bounds : shape.toAabbs()) {
                if (bounds.maxX > box.minX + 1e-6 && bounds.minX < box.maxX - 1e-6
                        && bounds.maxZ > box.minZ + 1e-6 && bounds.minZ < box.maxZ - 1e-6
                        && Math.abs(bounds.maxY - box.minY) <= .8
                        && (Double.isNaN(best)
                                || Math.abs(bounds.maxY - box.minY) < Math.abs(best - box.minY)))
                    best = bounds.maxY;
            }
        }
        return best;
    }

    static void verify(ServerLevel level) {
        var random = new Random(5550201);
        int checks = 0;
        for (int i = 0; i < 512; i++) {
            double x = 18 + random.nextDouble() * 4, z = 18 + random.nextDouble() * 4,
                   tick = random.nextDouble() * 800;
            double y = 64 + MotionState.server.offset((int) x, (int) z, tick)
                    + random.nextDouble() * 3 - 1.5;
            var box = new AABB(x, y, z, x + .6, y + 1.8, z + .6);
            double expected = referenceSupport(level, box, tick),
                   actual = MovingTerrain.support(level, box, tick);
            if (!(Double.isNaN(expected) && Double.isNaN(actual))
                            && Math.abs(expected - actual) > 1e-9
                    || Double.isNaN(expected) != Double.isNaN(actual))
                throw new IllegalStateException("Floor probe differs from reference");
            boolean overlap = false;
            for (var shape : MovingTerrain.shapes(level, box, tick))
                for (var bounds : shape.toAabbs())
                    if (bounds.intersects(box))
                        overlap = true;
            if (overlap != MovingTerrain.intersects(level, box, tick))
                throw new IllegalStateException("Overlap probe differs from reference");
            checks += 2;
        }
        System.out.println("[STM] PROBE EQUIVALENCE PASS: " + checks + " checks");
    }

    static void benchmark(ServerLevel level) {
        var old = MotionState.server;
        try {
            level.getChunk(1, 1);
            MotionState.server = new MotionField(1,
                    List.of(new MotionEvent(
                            1, Movement.STRESS, 20, 20, 208, 24, 0, 1200, 0, false)),
                    -1);
            var boxes = new AABB[64];
            for (int i = 0; i < boxes.length; i++) {
                double x = 18 + (i % 8) * .5, z = 18 + (i / 8) * .5;
                double y = 64 + MotionState.server.offset((int) x, (int) z, 300);
                boxes[i] = new AABB(x, y, z, x + .6, y + 1.8, z + .6);
            }
            var bean = ManagementFactory.getThreadMXBean();
            var allocation = bean instanceof com.sun.management.ThreadMXBean b
                            && b.isThreadAllocatedMemorySupported()
                            && b.isThreadAllocatedMemoryEnabled()
                    ? b
                    : null;
            long thread = Thread.currentThread().threadId();
            for (int i = 0; i < 4; i++) {
                sample(level, boxes, true, 5000);
                sample(level, boxes, false, 5000);
            }
            double[][] times = new double[2][5], bytes = new double[2][5];
            for (int round = 0; round < 5; round++)
                for (int order = 0; order < 2; order++) {
                    int kind = (round + order) % 2;
                    long allocated =
                            allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
                    long start = System.nanoTime();
                    sample(level, boxes, kind == 0, 10000);
                    times[kind][round] = (System.nanoTime() - start) / 10000.0;
                    bytes[kind][round] = allocation == null
                            ? -1
                            : (allocation.getThreadAllocatedBytes(thread) - allocated) / 10000.0;
                }
            for (var row : times)
                Arrays.sort(row);
            for (var row : bytes)
                Arrays.sort(row);
            System.out.printf(Locale.ROOT,
                    "[STM] PROBE BENCHMARK: reference=%.1f ns/op %.1f B/op; direct=%.1f ns/op %.1f "
                            + "B/op; median of 5 x 10000; CPU only%n",
                    times[0][2], bytes[0][2], times[1][2], bytes[1][2]);
        } finally {
            MotionState.server = old;
        }
    }

    private static void sample(ServerLevel level, AABB[] boxes, boolean reference, int count) {
        double total = 0;
        for (int i = 0; i < count; i++) {
            double value = reference ? referenceSupport(level, boxes[i % boxes.length], 300)
                                     : MovingTerrain.support(level, boxes[i % boxes.length], 300);
            if (Double.isFinite(value))
                total += value;
        }
        sink = total;
    }
}
