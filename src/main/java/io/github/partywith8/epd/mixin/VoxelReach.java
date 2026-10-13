package io.github.partywith8.epd.mixin;
import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.physics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public abstract class VoxelReach {
    @Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void epd$reach(BlockPos pos, double buffer, CallbackInfoReturnable<Boolean> ci) {
        var player = (Player) (Object) this;
        if (!MotionState.applies(player.level())
                || !MovingTerrain.moving(player.level().getBlockState(pos)))
            return;
        double offset = MovingTerrain.offset(player.level(), pos, MotionState.time(player.level()));
        double range = player.blockInteractionRange() + buffer;
        ci.setReturnValue(new AABB(pos).move(0, offset, 0).distanceToSqr(player.getEyePosition())
                < range* range);
    }
}
