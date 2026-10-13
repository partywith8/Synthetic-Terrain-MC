package io.github.partywith8.epd.mixin;
import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.physics.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockGetter.class)
public interface TerrainPicking {
    @Inject(method = "clip", at = @At("RETURN"), cancellable = true)
    private void epd$clip(ClipContext context, CallbackInfoReturnable<BlockHitResult> ci) {
        if (!((Object) this instanceof Level level) || !MotionState.applies(level))
            return;
        if (MovingTerrain.raw())
            return;
        var hit = MovingTerrain.clip(level, context, MotionState.time(level));
        var nativeHit = ci.getReturnValue();
        if (hit != null
                && (nativeHit.getType() == HitResult.Type.MISS
                        || hit.getLocation().distanceToSqr(context.getFrom())
                                < nativeHit.getLocation().distanceToSqr(context.getFrom())))
            ci.setReturnValue(hit);
    }
}
