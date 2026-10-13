package io.github.partywith8.epd.client.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.partywith8.epd.physics.*;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.*;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Keep native packet hit coordinates inside the real source block. */
@Mixin(MultiPlayerGameMode.class)
public abstract class VoxelUse {
    @WrapMethod(method = "useItemOn")
    private InteractionResult epd$use(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
            Operation<InteractionResult> original) {
        if (hit instanceof TerrainHit terrain)
            return MovingTerrain.nativeQuery(
                    () -> original.call(player, hand, terrain.nativeHit()));
        return original.call(player, hand, hit);
    }
}
