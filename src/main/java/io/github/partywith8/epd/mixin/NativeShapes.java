package io.github.partywith8.epd.mixin;
import io.github.partywith8.epd.motion.MotionState;
import io.github.partywith8.epd.physics.MovingTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Suppress only the source shapes that the physical-column solver replaces. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class NativeShapes {
    @Inject(method = {"getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/"
                            + "core/BlockPos;Lnet/minecraft/world/phys/shapes/"
                            + "CollisionContext;)Lnet/"
                            + "minecraft/world/phys/shapes/VoxelShape;",
                    "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/"
                            + "BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/"
                            + "minecraft/"
                            + "world/phys/shapes/VoxelShape;",
                    "getVisualShape"},
            at = @At("HEAD"), cancellable = true)
    private void epd$shape(BlockGetter getter, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> ci) {
        if (!MovingTerrain.raw() && getter instanceof Level level && MotionState.applies(level)
                && MovingTerrain.moving((BlockState) (Object) this))
            ci.setReturnValue(Shapes.empty());
    }
    @Inject(method = {"isSuffocating", "isViewBlocking"}, at = @At("HEAD"), cancellable = true)
    private void epd$source(BlockGetter getter, BlockPos pos, CallbackInfoReturnable<Boolean> ci) {
        if (!MovingTerrain.raw() && getter instanceof Level level && MotionState.applies(level)
                && MovingTerrain.moving((BlockState) (Object) this))
            ci.setReturnValue(false);
    }
}
