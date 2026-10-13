package io.github.partywith8.epd.physics;

import io.github.partywith8.epd.motion.MotionState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

/** Bounded horizontal DDA maps physical ray segments back into stored block columns. */
final class TerrainRaycast {
    private TerrainRaycast() {}
    static TerrainHit clip(BlockGetter level, ClipContext context, double tick) {
        if (!(level instanceof Level world) || !MotionState.applies(world))
            return null;
        var from = context.getFrom();
        var to = context.getTo();
        if (!Double.isFinite(from.lengthSqr() + to.lengthSqr())
                || from.distanceToSqr(to) > 1024 * 1024)
            return null;
        int x = (int) Math.floor(from.x), z = (int) Math.floor(from.z),
            sx = Double.compare(to.x, from.x), sz = Double.compare(to.z, from.z);
        double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
        double tx = sx == 0 ? Double.POSITIVE_INFINITY : ((sx > 0 ? x + 1 : x) - from.x) / dx,
               tz = sz == 0 ? Double.POSITIVE_INFINITY : ((sz > 0 ? z + 1 : z) - from.z) / dz;
        double stepX = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx),
               stepZ = sz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz), entered = 0;
        var p = new BlockPos.MutableBlockPos();
        for (int step = 0; step < 2048; step++) {
            double end = Math.min(1, Math.min(tx, tz));
            TerrainHit best = null;
            double distance = Double.POSITIVE_INFINITY;
            double offset = MovingTerrain.offset(world, x, z, tick),
                   a = from.y + dy * entered - offset, b = from.y + dy * end - offset;
            for (int y = Math.max(0, (int) Math.floor(Math.min(a, b)) - 1);
                    y <= Math.min(255, (int) Math.floor(Math.max(a, b)) + 1); y++) {
                p.set(x, y, z);
                var block = level.getBlockState(p);
                if (!MovingTerrain.moving(block))
                    continue;
                var shape = MovingTerrain.nativeQuery(() -> context.getBlockShape(block, level, p));
                var hit = shape.clip(from.add(0, -offset, 0), to.add(0, -offset, 0), p);
                if (hit == null)
                    continue;
                var point = hit.getLocation().add(0, offset, 0);
                double d = point.distanceToSqr(from);
                if (d < distance) {
                    distance = d;
                    best = new TerrainHit(
                            point, hit.getDirection(), p.immutable(), offset, hit.isInside());
                }
            }
            if (best != null)
                return best;
            double next = Math.min(tx, tz);
            if (next > 1 || Double.isInfinite(next))
                break;
            if (tx <= next) {
                x += sx;
                tx += stepX;
            }
            if (tz <= next) {
                z += sz;
                tz += stepZ;
            }
            entered = next;
        }
        return null;
    }
}
