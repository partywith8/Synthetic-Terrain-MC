package io.github.partywith8.epd.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.partywith8.epd.motion.MotionState;
import io.github.partywith8.epd.physics.MovingTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.*;
@Mixin(ServerPlayerGameMode.class)
public abstract class VoxelInteraction {
    @Shadow protected ServerLevel level;
    @WrapMethod(method = "useItemOn")
    private InteractionResult epd$use(ServerPlayer player, Level world, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        if (MotionState.applies(world))
            return MovingTerrain.nativeQuery(() -> original.call(player, world, stack, hand, hit));
        return original.call(player, world, stack, hand, hit);
    }
    @WrapMethod(method = "destroyBlock")
    private boolean epd$destroy(BlockPos pos, Operation<Boolean> original) {
        return MotionState.applies(level) ? MovingTerrain.nativeQuery(() -> original.call(pos))
                                          : original.call(pos);
    }
}
