package io.github.partywith8.epd.server;

import io.github.partywith8.epd.motion.*;
import io.github.partywith8.epd.physics.*;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Bounded synchronous test; restores the single test cell and motion in finally. */
final class RuntimeCheck {
    static void run(ServerLevel level) {
        var pos = new BlockPos(20, 63, 20);
        level.getChunk(1, 1);
        var original = level.getBlockState(pos);
        if (!MovingTerrain.moving(original))
            throw new IllegalStateException("Test cell edited; use a fresh lab for selftest");
        var old = MotionState.server;
        try {
            var event = new MotionEvent(1, Movement.RIDGE, 20, 20, 48, 12, 0, 800, 0, true);
            MotionState.server = new MotionField(1, List.of(event), -1);
            double tick = 200, dy = MotionState.server.offset(20, 20, tick);
            // The context-free overload reads Minecraft's immutable shape cache.
            // Entity collision uses this contextual overload, where the hook lives.
            require(level.getBlockState(pos)
                            .getCollisionShape(level, pos,
                                    net.minecraft.world.phys.shapes.CollisionContext.empty())
                            .isEmpty(),
                    "native source shape suppressed");
            require(!MovingTerrain
                            .nativeQuery(()
                                                 -> level.getBlockState(pos).getCollisionShape(
                                                         level, pos,
                                                         net.minecraft.world.phys.shapes
                                                                 .CollisionContext.empty()))
                            .isEmpty(),
                    "guard exposes native shape");
            var box = new AABB(20.2, 64 + dy, 20.2, 20.8, 65.8 + dy, 20.8);
            require(Math.abs(MovingTerrain.support(level, box, tick) - (64 + dy)) < 1e-6,
                    "support");
            var context =
                    new ClipContext(new Vec3(20.5, 70 + dy, 20.5), new Vec3(20.5, 60 + dy, 20.5),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                            net.minecraft.world.phys.shapes.CollisionContext.empty());
            var hit = MovingTerrain.clip(level, context, tick);
            require(hit != null && hit.getBlockPos().equals(pos),
                    "physical raycast maps to native cell");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            TerrainProbeCheck.verify(level);
            var mined = MovingTerrain.clip(level, context, tick);
            require(mined != null && mined.getBlockPos().equals(pos.below()),
                    "mined cell disappears");
            level.setBlock(pos, original, 3);
            require(MovingTerrain.clip(level, context, tick).getBlockPos().equals(pos),
                    "placed cell reappears");
            int riderChecks = 0;
            for (var kind : Movement.values()) {
                // Keep the rider in the same native test chunk but put the
                // stress origin kilometres away to exercise roaming support.
                var motion = new MotionEvent(1, kind, kind == Movement.STRESS ? -3072 : 20,
                        kind == Movement.STRESS ? 3072 : 20, kind == Movement.STRESS ? 208 : 48,
                        kind == Movement.MIRAGE           ? .5
                                : kind == Movement.STRESS ? 24
                                                          : 18,
                        0, 800, 0, false);
                MotionState.server = new MotionField(2, List.of(motion), -1);
                TerrainProbeCheck.verify(level);
                for (int t = 1; t < 800; t += 7) {
                    double before = 64 + motion.offset(20, 20, t - 1),
                           after = 64 + motion.offset(20, 20, t);
                    var rider = new AABB(20.2, before, 20.2, 20.8, before + 1.8, 20.8);
                    var result =
                            MovingTerrain.move(level, rider, new Vec3(0, -.08, 0), t, .6, false);
                    require(Math.abs(before + result.delta().y - after) < 1e-6,
                            "rider continuity " + kind + " tick=" + t);
                    require(result.supported(), "grounded rider " + kind);
                    riderChecks++;
                }
            }
            var registryBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            try {
                var payload =
                        new io.github.partywith8.epd.network.MotionPayload(MotionState.server);
                io.github.partywith8.epd.network.MotionPayload.CODEC.encode(
                        registryBuffer, payload);
                var decoded =
                        io.github.partywith8.epd.network.MotionPayload.CODEC.decode(registryBuffer);
                require(decoded.equals(payload), "network round trip");
            } finally {
                registryBuffer.release();
            }
            System.out.println("[STM] RUNTIME CHECK PASS: native mining, placement, moving "
                    + "support, raycast, packet roundtrip; rider samples=" + riderChecks);
        } catch (RuntimeException error) {
            System.err.println("[STM] RUNTIME CHECK FAILED: " + error.getMessage());
            throw error;
        } finally {
            level.setBlock(pos, original, 3);
            MotionState.server = old;
        }
    }
    private static void require(boolean value, String label) {
        if (!value)
            throw new IllegalStateException("STM selftest failed: " + label);
    }
}
