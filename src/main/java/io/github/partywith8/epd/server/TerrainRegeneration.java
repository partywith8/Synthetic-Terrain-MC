package io.github.partywith8.epd.server;

import io.github.partywith8.epd.Epd;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Incremental native block restoration. Never generates or force-loads chunks. */
final class TerrainRegeneration {
    private final int centerX, centerZ, radius, width;
    private int chunkIndex, cell, completed, skipped;
    private long changed, scanned;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    TerrainRegeneration(int centerX, int centerZ, int radius) {
        if (radius < 0 || radius > 8 || Math.abs((long) centerX) > 256
                || Math.abs((long) centerZ) > 256)
            throw new IllegalArgumentException("Regeneration bounds outside the laboratory");
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        width = 2 * radius + 1;
    }

    static BlockState original(int y) {
        return (y < 64 ? Blocks.CONCRETE.white() : Blocks.AIR).defaultBlockState();
    }

    boolean contains(double x, double z) {
        int cx = ((int) Math.floor(x)) >> 4, cz = ((int) Math.floor(z)) >> 4;
        return Math.abs(cx - centerX) <= radius && Math.abs(cz - centerZ) <= radius;
    }

    boolean finished() {
        return chunkIndex == width * width;
    }

    String progress() {
        return "regeneration " + (completed + skipped) + "/" + (width * width)
                + " chunks; restored=" + completed + ", unloaded/skipped=" + skipped
                + ", changed=" + changed + ", scanned=" + scanned;
    }

    void tick(ServerLevel level) {
        if (!level.dimension().equals(Epd.LAB))
            throw new IllegalArgumentException("Not the STM laboratory");
        // The wall-clock budget is soft: a single native block update cannot be
        // interrupted. Hard cell/change caps bound each batch independently.
        long deadline = System.nanoTime() + 3_000_000;
        int checks = 0, writes = 0, height = level.getHeight();
        while (!finished() && checks < 8192 && writes < 2048 && System.nanoTime() < deadline) {
            int cx = centerX - radius + chunkIndex % width;
            int cz = centerZ - radius + chunkIndex / width;
            if (level.getChunkSource().getChunkNow(cx, cz) == null) {
                skipped++;
                chunkIndex++;
                cell = 0;
                continue;
            }
            // Finish a whole vertical column before advancing horizontally.
            int column = cell / height, y = level.getMinY() + cell % height;
            pos.set(cx * 16 + column % 16, y, cz * 16 + column / 16);
            var expected = original(y);
            checks++;
            scanned++;
            if (level.getBlockState(pos) != expected) {
                if (!level.setBlock(pos, expected, 2))
                    throw new IllegalStateException(
                            "Block restoration refused at " + pos.toShortString());
                writes++;
                changed++;
            }
            if (++cell == 256 * height) {
                completed++;
                chunkIndex++;
                cell = 0;
            }
        }
        // A restored floor must not entomb someone who was mining below it.
        for (var player : java.util.List.copyOf(level.players())) {
            if (!contains(player.getX(), player.getZ()) || player.getY() >= 64 - 1.0E-4
                    || player.isSpectator())
                continue;
            player.teleportTo(level, player.getX(), 66, player.getZ(), Set.of(), player.getYRot(),
                    player.getXRot(), false);
        }
    }
}
