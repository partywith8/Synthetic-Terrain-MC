package io.github.partywith8.epd.physics;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
/** A hit on a translated terrain cube, distinguishable from a native placed block. */
public final class TerrainHit extends BlockHitResult {
    public final double bottom;
    public TerrainHit(
            Vec3 point, Direction direction, BlockPos source, double offset, boolean inside) {
        super(point, direction, source, inside);
        bottom = source.getY() + offset;
    }
    public BlockHitResult nativeHit() {
        return new BlockHitResult(getLocation().add(0, getBlockPos().getY() - bottom, 0),
                getDirection(), getBlockPos(), isInside());
    }
}
