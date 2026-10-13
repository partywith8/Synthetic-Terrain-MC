package io.github.partywith8.epd.motion;

/** Fixed phase origin and lifetime. Stress reaches loaded terrain without following the camera. */
public record StressScene(int x, int z, int viewChunks, long start, int duration) {
    public StressScene {
        if (viewChunks < 2 || viewChunks > 32 || start < 0 || duration < 300 || duration > 2400)
            throw new IllegalArgumentException("Invalid stress scene");
    }

    public static StressScene at(
            int blockX, int blockZ, int requestedView, int serverView, long start, int seconds) {
        int view = Math.clamp(Math.min(requestedView, serverView), 2, 32);
        return new StressScene((blockX >> 4) * 16 + 8, (blockZ >> 4) * 16 + 8, view, start,
                Math.multiplyExact(seconds, 20));
    }

    public MotionEvent event(long id) {
        // Radius records the starting view for diagnostics. STRESS has no spatial
        // falloff; only loaded/rendered chunks incur work as the owner travels.
        return new MotionEvent(id, Movement.STRESS, x, z, (viewChunks + 1) * 16 + 32, 24, start,
                duration, 0, false);
    }

    public boolean finished(long tick) {
        return tick >= start + duration;
    }
}
