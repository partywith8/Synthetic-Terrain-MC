package io.github.partywith8.epd.physics;
import io.github.partywith8.epd.motion.MotionState;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
/**
 * Native white-concrete cells remain authoritative for mining and storage. Queries map
 * physical coordinates back through each column's translation before consulting
 * those cells. The reentrant nativeQuery guard prevents recursive mixin calls.
 */
public final class MovingTerrain {
    private static final ThreadLocal<Integer> RAW = ThreadLocal.withInitial(() -> 0);
    public static boolean raw() {
        return RAW.get() != 0;
    }
    public static <T> T nativeQuery(Supplier<T> work) {
        int previous = RAW.get();
        RAW.set(previous + 1);
        try {
            return work.get();
        } finally {
            RAW.set(previous);
        }
    }
    public static boolean moving(BlockState block) {
        return (block.is(Blocks.CONCRETE.white()) || block.is(Blocks.CALCITE));
    }
    public static double offset(Level level, int x, int z, double tick) {
        return MotionState.field(level).offset(x, z, tick);
    }
    public static double offset(Level level, BlockPos pos, double tick) {
        return moving(level.getBlockState(pos)) ? offset(level, pos.getX(), pos.getZ(), tick) : 0;
    }
    public static List<VoxelShape> shapes(BlockGetter getter, AABB box, double tick) {
        var result = new ArrayList<VoxelShape>();
        if (!(getter instanceof Level level) || !MotionState.applies(level))
            return result;
        var p = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++) {
            for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++) {
                double shift = offset(level, x, z, tick);
                int lo = Math.max(0, (int) Math.floor(box.minY - shift) - 1);
                int hi = Math.min(255, (int) Math.floor(box.maxY - shift) + 1);
                for (int y = lo; y <= hi; y++) {
                    var block = level.getBlockState(p.set(x, y, z));
                    if (moving(block))
                        result.add(Shapes.create(
                                new AABB(x, y + shift, z, x + 1, y + shift + 1, z + 1)));
                }
            }
        }
        return result;
    }
    /** Early-exit cube overlap query; no temporary shapes or AABB lists. */
    public static boolean intersects(BlockGetter getter, AABB box, double tick) {
        if (!(getter instanceof Level level) || !MotionState.applies(level))
            return false;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++) {
            for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++) {
                if (x + 1 <= box.minX || x >= box.maxX || z + 1 <= box.minZ || z >= box.maxZ)
                    continue;
                double shift = offset(level, x, z, tick);
                int lo = Math.max(0, (int) Math.floor(box.minY - shift) - 1);
                int hi = Math.min(255, (int) Math.floor(box.maxY - shift) + 1);
                for (int y = lo; y <= hi; y++) {
                    if (y + shift + 1 > box.minY && y + shift < box.maxY
                            && moving(level.getBlockState(pos.set(x, y, z))))
                        return true;
                }
            }
        }
        return false;
    }

    /** Closest solid cube top within the existing rider tolerance, without shape allocation. */
    public static double support(BlockGetter getter, AABB box, double tick) {
        if (!(getter instanceof Level level) || !MotionState.applies(level))
            return Double.NaN;
        double best = Double.NaN;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX + 1e-6); x <= Math.floor(box.maxX - 1e-6); x++) {
            for (int z = (int) Math.floor(box.minZ + 1e-6); z <= Math.floor(box.maxZ - 1e-6); z++) {
                if (x + 1 <= box.minX + 1e-6 || x >= box.maxX - 1e-6 || z + 1 <= box.minZ + 1e-6
                        || z >= box.maxZ - 1e-6)
                    continue;
                double shift = offset(level, x, z, tick);
                int lo = Math.max(0, (int) Math.floor(box.minY - .8 - shift) - 1);
                int hi = Math.min(255, (int) Math.floor(box.minY + .8 - shift) + 1);
                for (int y = lo; y <= hi; y++) {
                    double top = y + shift + 1;
                    if (Math.abs(top - box.minY) <= .8
                            && moving(level.getBlockState(pos.set(x, y, z)))
                            && (Double.isNaN(best)
                                    || Math.abs(top - box.minY) < Math.abs(best - box.minY)))
                        best = top;
                }
            }
        }
        return best;
    }
    public static CollisionSolver.Result move(
            BlockGetter level, AABB box, Vec3 request, double tick, double step, boolean network) {
        if (Math.abs(request.x) > 16 || Math.abs(request.z) > 16 || Math.abs(request.y) > 64)
            return new CollisionSolver.Result(request, false);
        double previous = support(level, box, tick - 1), current = support(level, box, tick);
        boolean grounded = Double.isFinite(previous) && Double.isFinite(current)
                && Math.abs(box.minY - previous) < (network ? .65 : .12)
                && request.y <= Math.max(.15, current - previous + .15);
        double carry = grounded ? current - box.minY : 0;
        if (Math.abs(carry) > .65)
            carry = 0;
        var carried = box.move(0, carry, 0);
        var motion = new Vec3(request.x, request.y - (network ? carry : 0), request.z);
        var shapes = shapes(level, carried.expandTowards(motion).inflate(Math.max(1, step)), tick);
        var result = CollisionSolver.collide(motion, carried, shapes);
        if (grounded && step > 0 && (result.x != motion.x || result.z != motion.z)) {
            double up = Shapes.collide(Direction.Axis.Y, carried, shapes, step);
            var walk = CollisionSolver.collide(
                    new Vec3(motion.x, 0, motion.z), carried.move(0, up, 0), shapes);
            double down = Shapes.collide(Direction.Axis.Y, carried.move(walk.x, up, walk.z), shapes,
                    -up + Math.min(0, motion.y));
            if (walk.horizontalDistanceSqr() > result.horizontalDistanceSqr())
                result = new Vec3(walk.x, up + down, walk.z);
        }
        var delta = result.add(0, carry, 0);
        var end = box.move(delta);
        double floor = support(level, end, tick);
        return new CollisionSolver.Result(delta,
                Double.isFinite(floor) && Math.abs(end.minY - floor) < 1e-6
                        && request.y <= Math.max(.15, carry + .15));
    }
    public static TerrainHit clip(BlockGetter level, ClipContext context, double tick) {
        return TerrainRaycast.clip(level, context, tick);
    }
}
