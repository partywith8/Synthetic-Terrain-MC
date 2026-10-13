package io.github.partywith8.epd.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.world.phys.AABB;
import org.lwjgl.system.MemoryUtil;

/**
 * Greedy vertical runs preserve excavated air gaps while reducing an untouched
 * 64-block column to six quads. Horizontal faces remain: adjacent columns can
 * separate later without rebuilding geometry every frame. Concrete UVs repeat
 * every block, so a long side retains Minecraft's original block scale.
 */
public final class ColumnMesh {
    public record Snapshot(int cx, int cz, int height, boolean[] cells) {
        public Snapshot {
            if (height < 1 || height > 256 || cells.length != 256 * height)
                throw new IllegalArgumentException("Snapshot size");
        }
        boolean solid(int x, int y, int z) {
            return cells[(z * 16 + x) * height + y];
        }
    }
    public record Prepared(ByteBufferBuilder storage, MeshData data, AABB bounds)
            implements AutoCloseable {
        public void close() {
            data.close();
            storage.close();
        }
    }
    public static Prepared build(Snapshot s) {
        var storage = new ByteBufferBuilder(128 * 1024, 16L * 1024 * 1024);
        try {
            var w = new Writer(storage);
            int min = 256, max = 0;
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < s.height(); y++) {
                        if (!s.solid(x, y, z))
                            continue;
                        int low = y;
                        while (y + 1 < s.height() && s.solid(x, y + 1, z))
                            y++;
                        int top = y + 1;
                        min = Math.min(min, low);
                        max = Math.max(max, top);
                        int wx = s.cx() * 16 + x, wz = s.cz() * 16 + z;
                        w.box(wx, low, wz, top);
                    }
                }
            if (w.count == 0) {
                storage.close();
                return null;
            }
            var data = new MeshData(storage.build(),
                    new MeshData.DrawState(MeshFormat.FORMAT, w.count, w.count / 4 * 6,
                            PrimitiveTopology.QUADS, com.mojang.blaze3d.IndexType.least(w.count)));
            return new Prepared(storage, data,
                    new AABB(s.cx() * 16, min, s.cz() * 16, s.cx() * 16 + 16, max,
                            s.cz() * 16 + 16));
        } catch (Throwable e) {
            storage.close();
            throw e;
        }
    }
    private static final class Writer {
        final ByteBufferBuilder bytes;
        int count;
        Writer(ByteBufferBuilder bytes) {
            this.bytes = bytes;
        }
        void vertex(float x, float y, float z, float u, float v, int ox, int oz, int color) {
            if (count >= 500_000)
                throw new IllegalStateException("Chunk mesh exceeds vertex budget");
            long p = bytes.reserve(MeshFormat.STRIDE);
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4, y);
            MemoryUtil.memPutFloat(p + 8, z);
            for (int i = 12; i < 15; i++)
                MemoryUtil.memPutByte(p + i, (byte) color);
            MemoryUtil.memPutByte(p + 15, (byte) 255);
            MemoryUtil.memPutFloat(p + 16, u);
            MemoryUtil.memPutFloat(p + 20, v);
            MemoryUtil.memPutShort(p + 24, (short) ox);
            MemoryUtil.memPutShort(p + 26, (short) oz);
            count++;
        }
        void faceVertex(int x, int z, int shade, boolean side, boolean alongZ, float px, float py,
                float pz) {
            vertex(px, py, pz, side ? (alongZ ? pz : px) : px, side ? py : pz, x, z, shade);
        }
        void face(int x, int z, int shade, boolean side, boolean alongZ, float ax, float ay,
                float az, float bx, float by, float bz, float cx, float cy, float cz, float dx,
                float dy, float dz) {
            // Explicit vertices avoid a temporary varargs float[] for every face.
            faceVertex(x, z, shade, side, alongZ, ax, ay, az);
            faceVertex(x, z, shade, side, alongZ, bx, by, bz);
            faceVertex(x, z, shade, side, alongZ, cx, cy, cz);
            faceVertex(x, z, shade, side, alongZ, dx, dy, dz);
        }
        void box(int x, int lo, int z, int hi) {
            face(x, z, 255, false, false, x, hi, z, x + 1, hi, z, x + 1, hi, z + 1, x, hi, z + 1);
            face(x, z, 125, false, false, x, lo, z, x, lo, z + 1, x + 1, lo, z + 1, x + 1, lo, z);
            face(x, z, 175, true, true, x, lo, z, x, hi, z, x, hi, z + 1, x, lo, z + 1);
            face(x, z, 175, true, true, x + 1, lo, z, x + 1, lo, z + 1, x + 1, hi, z + 1, x + 1, hi,
                    z);
            face(x, z, 210, true, false, x, lo, z, x + 1, lo, z, x + 1, hi, z, x, hi, z);
            face(x, z, 210, true, false, x, lo, z + 1, x, hi, z + 1, x + 1, hi, z + 1, x + 1, lo,
                    z + 1);
        }
    }
}
