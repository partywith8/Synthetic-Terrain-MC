package io.github.partywith8.epd.physics;
import java.util.List;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
/** Minecraft axis clipping, extracted from ASH. No terrain generation dependency. */
public final class CollisionSolver {
    public record Result(Vec3 delta, boolean supported) {}
    static Vec3 collide(Vec3 delta, AABB box, List<VoxelShape> shapes) {
        double y = Shapes.collide(Axis.Y, box, shapes, delta.y);
        box = box.move(0, y, 0);
        double x = delta.x, z = delta.z;
        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst) {
            z = Shapes.collide(Axis.Z, box, shapes, z);
            box = box.move(0, 0, z);
        }
        x = Shapes.collide(Axis.X, box, shapes, x);
        box = box.move(x, 0, 0);
        if (!zFirst)
            z = Shapes.collide(Axis.Z, box, shapes, z);
        return new Vec3(x, y, z);
    }
}
