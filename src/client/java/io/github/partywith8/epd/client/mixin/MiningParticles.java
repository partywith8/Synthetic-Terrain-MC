package io.github.partywith8.epd.client.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.partywith8.epd.motion.MotionState;
import io.github.partywith8.epd.physics.MovingTerrain;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Particle bounds must use a nonempty source shape, even while native shapes are hidden. */
@Mixin(ClientLevel.class)
public abstract class MiningParticles {
    @WrapOperation(method = {"addBreakingBlockEffect", "addDestroyBlockEffect"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/"
                            + "minecraft/world/level/BlockGetter;Lnet/minecraft/core/"
                            + "BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape epd$particles(
            BlockState block, BlockGetter getter, BlockPos pos, Operation<VoxelShape> original) {
        var level = (ClientLevel) (Object) this;
        if (!MotionState.applies(level) || !MovingTerrain.moving(block))
            return original.call(block, getter, pos);
        var shape = MovingTerrain.nativeQuery(() -> original.call(block, getter, pos));
        if (shape.isEmpty())
            shape = Shapes.block();
        return shape.move(0, MovingTerrain.offset(level, pos, MotionState.time(level)), 0);
    }
}
