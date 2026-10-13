package io.github.partywith8.epd.mixin;
import io.github.partywith8.epd.motion.MotionState;
import io.github.partywith8.epd.physics.MovingTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CollisionGetter.class)
public interface VoxelPlacement {
    @Inject(method = "isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/"
                    + "minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/"
                    + "CollisionContext;)Z",
            at = @At("HEAD"), cancellable = true)
    private void
    epd$placement(BlockState block, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<Boolean> ci) {
        if (MovingTerrain.raw() && (Object) this instanceof Level level
                && MotionState.applies(level) && MovingTerrain.moving(block)) {
            var shape = block.getCollisionShape(level, pos, context);
            double offset =
                    MovingTerrain.offset(level, pos.getX(), pos.getZ(), MotionState.time(level));
            ci.setReturnValue(shape.isEmpty()
                    || level.isUnobstructed(
                            null, shape.move(pos.getX(), pos.getY() + offset, pos.getZ())));
        }
    }
}
