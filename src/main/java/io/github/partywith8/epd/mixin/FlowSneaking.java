package io.github.partywith8.epd.mixin;
import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.physics.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Keep vanilla's edge-clamping algorithm, but include the physical moving floor. */
@Mixin(Player.class)
public abstract class FlowSneaking {
    @Inject(method = "canFallAtLeast(DDD)Z", at = @At("RETURN"), cancellable = true)
    private void epd$floor(
            double x, double z, double depth, CallbackInfoReturnable<Boolean> result) {
        var player = (Player) (Object) this;
        var level = player.level();
        if (!result.getReturnValueZ() || !MotionState.applies(level))
            return;
        // Match 26.2's thin foot probe. Augment the result instead of redirecting
        // noCollision: ASH and other mods may already own that invocation.
        var bounds = player.getBoundingBox();
        var probe = new AABB(bounds.minX + 1.0E-7 + x, bounds.minY - depth - 1.0E-7,
                bounds.minZ + 1.0E-7 + z, bounds.maxX - 1.0E-7 + x, bounds.minY,
                bounds.maxZ - 1.0E-7 + z);
        if (MovingTerrain.intersects(level, probe, level.getGameTime()))
            result.setReturnValue(false);
    }
}
