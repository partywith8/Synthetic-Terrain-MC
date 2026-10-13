package io.github.partywith8.epd.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.partywith8.epd.motion.MotionState;
import io.github.partywith8.epd.physics.MovingTerrain;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Owns chunk snapshots, one bounded worker, GPU mesh lifetime and native replacement coverage. */
final class TerrainMeshes {
    private TerrainMeshes() {}
    static double captureMs;
    static int maxIndices;
    // Normal 32-chunk flat terrain fits; heavily excavated worlds cannot grow
    // GPU residency without a bound. A rejected mesh keeps native rendering.
    private static final long MAX_GPU_BYTES = 1024L * 1024 * 1024;
    private static long gpuBytes;
    private static boolean coverageDirty;
    private static int scanDelay, viewX, viewZ, viewRadius;
    record Mesh(GpuBuffer vertices, int indices, AABB bounds, long revision, long bytes)
            implements AutoCloseable {
        public void close() {
            if (vertices != null)
                vertices.close();
        }
    }
    private record Job(
            long key, long revision, long epoch, CompletableFuture<ColumnMesh.Prepared> result) {}
    static final Map<Long, Mesh> CACHE = new HashMap<>();
    private static final Map<Long, Long> DIRTY = new HashMap<>();
    private static final Set<Long> FAILED = new HashSet<>();
    private static volatile LongSet coverage = LongSets.EMPTY_SET;
    private static Job job;
    private static long revision, epoch;
    private static ExecutorService worker;
    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
    private static int cx(long key) {
        return (int) (key >> 32);
    }
    private static int cz(long key) {
        return (int) key;
    }
    private static int radius() {
        return Math.clamp(Minecraft.getInstance().options.getEffectiveRenderDistance(), 2, 32);
    }
    public static boolean hide(BlockState block, int x, int z) {
        return EpdClient.active && MovingTerrain.moving(block)
                && coverage.contains(key(x >> 4, z >> 4));
    }
    public static void changed(BlockPos pos) {
        if (EpdClient.active) {
            long k = key(pos.getX() >> 4, pos.getZ() >> 4);
            DIRTY.put(k, ++revision);
            FAILED.remove(k);
            scanDelay = 0;
        }
    }
    private static void invalidate(long key) {
        var mc = Minecraft.getInstance();
        if (MotionState.applies(mc.level))
            mc.level.setSectionRangeDirty(cx(key), 0, cz(key), cx(key), 15, cz(key));
    }
    private static boolean wanted(long key, int x, int z, int r) {
        return Math.abs(cx(key) - x) <= r && Math.abs(cz(key) - z) <= r;
    }
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (!EpdClient.active || mc.level == null || mc.player == null)
            return;
        int x = mc.player.chunkPosition().x(), z = mc.player.chunkPosition().z(), r = radius();
        if (x != viewX || z != viewZ || r != viewRadius) {
            viewX = x;
            viewZ = z;
            viewRadius = r;
            scanDelay = 0;
        }
        boolean removed = false;
        for (var it = CACHE.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            if (!wanted(entry.getKey(), x, z, r + 1)
                    || !mc.level.hasChunk(cx(entry.getKey()), cz(entry.getKey()))) {
                gpuBytes -= entry.getValue().bytes();
                entry.getValue().close();
                it.remove();
                invalidate(entry.getKey());
                DIRTY.remove(entry.getKey());
                removed = true;
            }
        }
        if (removed) {
            var remaining = new LongOpenHashSet(coverage);
            remaining.retainAll(CACHE.keySet());
            coverage = LongSets.unmodifiable(remaining);
            scanDelay = 0;
        }
        DIRTY.keySet().removeIf(k -> !wanted(k, x, z, r + 2));
        FAILED.removeIf(k -> !wanted(k, x, z, r + 2));
        if (job != null && job.result().isDone()) {
            var done = job;
            job = null;
            scanDelay = 0;
            try (var prepared = done.result().join()) {
                if (done.epoch() == epoch && wanted(done.key(), x, z, r)
                        && mc.level.hasChunk(cx(done.key()), cz(done.key()))
                        && done.revision() == DIRTY.getOrDefault(done.key(), 0L)) {
                    var next = upload(done, prepared);
                    maxIndices = Math.max(maxIndices, next.indices());
                    coverageDirty = true;
                    var old = CACHE.put(done.key(), next);
                    gpuBytes += next.bytes() - (old == null ? 0 : old.bytes());
                    if (old != null && old.vertices() != null)
                        old.close();
                    // Publication follows the first successful replacement draw, below.
                }
            } catch (RuntimeException error) {
                FAILED.add(done.key());
                System.err.println("[STM] Chunk mesh failed: " + error);
            }
        }
        if (job != null || scanDelay-- > 0)
            return;
        long chosen = Long.MIN_VALUE;
        double best = Double.POSITIVE_INFINITY;
        for (int xx = x - r; xx <= x + r; xx++)
            for (int zz = z - r; zz <= z + r; zz++) {
                long k = key(xx, zz);
                var old = CACHE.get(k);
                if (!mc.level.hasChunk(xx, zz) || FAILED.contains(k)
                        || old != null && old.revision() == DIRTY.getOrDefault(k, 0L))
                    continue;
                double score =
                        (xx - x) * (xx - x) + (zz - z) * (zz - z) - (old != null ? 10000 : 0);
                if (score < best) {
                    chosen = k;
                    best = score;
                }
            }
        if (chosen == Long.MIN_VALUE) {
            scanDelay = 19;
            return;
        }
        long t = System.nanoTime();
        var snapshot = capture(cx(chosen), cz(chosen));
        captureMs = (System.nanoTime() - t) / 1e6;
        if (worker == null)
            worker = Executors.newSingleThreadExecutor(
                    Thread.ofPlatform().daemon(true).name("STM mesh").factory());
        var future = new CompletableFuture<ColumnMesh.Prepared>();
        job = new Job(chosen, DIRTY.getOrDefault(chosen, 0L), epoch, future);
        worker.execute(() -> {
            try {
                future.complete(ColumnMesh.build(snapshot));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
    }
    private static Mesh upload(Job done, ColumnMesh.Prepared prepared) {
        if (prepared == null) {
            return new Mesh(null, 0,
                    new AABB(cx(done.key()) * 16, 0, cz(done.key()) * 16, cx(done.key()) * 16 + 16,
                            256, cz(done.key()) * 16 + 16),
                    done.revision(), 0);
        }
        long bytes = prepared.data().vertexBuffer().remaining();
        // Account for the temporary overlap with the old buffer during replacement.
        if (bytes > MAX_GPU_BYTES - gpuBytes)
            throw new IllegalStateException("Terrain GPU budget reached");
        var buffer = RenderSystem.getDevice().createBuffer(
                TerrainMeshes::meshLabel, GpuBuffer.USAGE_VERTEX, prepared.data().vertexBuffer());
        return new Mesh(buffer, prepared.data().drawState().indexCount(), prepared.bounds(),
                done.revision(), bytes);
    }
    private static String meshLabel() {
        return "STM chunk";
    }
    private static ColumnMesh.Snapshot capture(int cx, int cz) {
        var level = Minecraft.getInstance().level;
        var chunk = level.getChunk(cx, cz);
        int height = 1;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                height = Math.max(height,
                        Math.min(256,
                                level.getHeight(
                                        Heightmap.Types.WORLD_SURFACE, cx * 16 + x, cz * 16 + z)
                                        + 1));
        var cells = new boolean[256 * height];
        var p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = 0; y < height; y++)
                    cells[(z * 16 + x) * height + y] = MovingTerrain.moving(
                            chunk.getBlockState(p.set(cx * 16 + x, y, cz * 16 + z)));
        return new ColumnMesh.Snapshot(cx, cz, height, cells);
    }
    /** Publish only after a successful draw pass. Chunk builders read this immutable snapshot. */
    static void publishCoverage() {
        if (!coverageDirty)
            return;
        var before = coverage;
        coverage = LongSets.unmodifiable(new LongOpenHashSet(CACHE.keySet()));
        for (long key : coverage)
            if (!before.contains(key))
                invalidate(key);
        coverageDirty = false;
    }
    public static String diagnostics() {
        var mc = Minecraft.getInstance();
        int loaded = 0, ready = 0;
        if (mc.level != null && mc.player != null) {
            int r = radius(), x = mc.player.chunkPosition().x(), z = mc.player.chunkPosition().z();
            for (int xx = x - r; xx <= x + r; xx++)
                for (int zz = z - r; zz <= z + r; zz++)
                    if (mc.level.hasChunk(xx, zz)) {
                        loaded++;
                        if (coverage.contains(key(xx, zz)))
                            ready++;
                    }
        }
        return String.format(Locale.ROOT,
                "view=%d, ready=%d/%d loaded, draw=%d, vertices=%d, capture=%.2fms, submit=%.2fms, "
                        + "pending=%s",
                radius(), ready, loaded, TerrainRenderer.visible, TerrainRenderer.vertices,
                captureMs, TerrainRenderer.drawMs, job != null);
    }
    public static void close() {
        epoch++;
        var old = coverage;
        coverage = LongSets.EMPTY_SET;
        for (long key : old)
            invalidate(key);
        for (var mesh : CACHE.values())
            if (mesh.vertices() != null)
                mesh.close();
        CACHE.clear();
        DIRTY.clear();
        FAILED.clear();
        if (job != null) {
            job.result().thenAccept(p -> {
                if (p != null)
                    p.close();
            });
            job = null;
        }
        coverageDirty = false;
        scanDelay = 0;
        maxIndices = 0;
        gpuBytes = 0;
        captureMs = 0;
        if (worker != null) {
            worker.shutdown();
            worker = null;
        }
    }
}
