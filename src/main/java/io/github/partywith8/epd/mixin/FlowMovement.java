package io.github.partywith8.epd.mixin;
import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.physics.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A continuous supporting surface, applied equally to client prediction and server motion. */
@Mixin(Entity.class)
public abstract class FlowMovement {
    @Unique private boolean epd$supported;
    @Unique
    private static boolean epd$eligible(Entity e) {
        return MotionState.applies(e.level()) && !e.noPhysics
                && (!e.isNoGravity() || e instanceof Player) && !e.isPassenger()
                && !(e instanceof Player p && p.isSpectator());
    }
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 epd$flow(Vec3 requested) {
        epd$supported = false;
        Entity e = (Entity) (Object) this;
        if (!epd$eligible(e))
            return requested;
        var result = MovingTerrain.move(e.level(), e.getBoundingBox(), requested,
                e.level().getGameTime(), e.maxUpStep(),
                e instanceof Player && !e.level().isClientSide());
        epd$supported = result.supported();
        return result.delta();
    }
    @Inject(method = "move", at = @At("TAIL"))
    private void epd$ground(MoverType type, Vec3 delta, CallbackInfo ci) {
        if (!epd$supported)
            return;
        Entity e = (Entity) (Object) this;
        double floor =
                MovingTerrain.support(e.level(), e.getBoundingBox(), e.level().getGameTime());
        if (Math.abs(e.getY() - floor) < .12) {
            e.setOnGround(true);
            e.resetFallDistance();
            var v = e.getDeltaMovement();
            if (v.y < 0)
                e.setDeltaMovement(v.x, 0, v.z);
        }
    }
}
