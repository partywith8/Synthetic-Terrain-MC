package io.github.partywith8.epd.motion;

import java.util.function.DoubleSupplier;
import net.minecraft.world.level.Level;

/** Separate sides matter when the same jar runs an integrated server. */
public final class MotionState {
    public static volatile MotionField server = MotionField.EMPTY;
    public static volatile MotionField client = MotionField.EMPTY;
    public static DoubleSupplier partialTick = () -> 0;
    private MotionState() {}

    public static boolean applies(Level level) {
        return level != null && level.dimension().equals(io.github.partywith8.epd.Epd.LAB);
    }
    public static MotionField field(Level level) {
        return level.isClientSide() ? client : server;
    }
    public static double time(Level level) {
        return level.getGameTime() + (level.isClientSide() ? partialTick.getAsDouble() : 0);
    }
}
