package io.github.partywith8.epd.motion;

/** Immutable analytic movement, shared by server physics and client rendering. */
public record MotionEvent(long id, Movement kind, int x, int z, int radius, double amplitude,
        long start, int duration, int axis, boolean hold) {
    public MotionEvent {
        if (id < 1 || kind == null || Math.abs((long) x) > 29_000_000
                || Math.abs((long) z) > 29_000_000 || radius < 8
                || radius > (kind == Movement.STRESS ? 1024 : 128)
                || (kind == Movement.STRESS && (radius < 64 || hold)) || !Double.isFinite(amplitude)
                || amplitude < 0 || amplitude > 24 || start < 0 || duration < 160 || duration > 2400
                || axis < 0 || axis > 3)
            throw new IllegalArgumentException("Invalid motion event");
    }

    public static double ease(double x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public boolean finished(double tick) {
        return !hold && tick >= start + duration;
    }

    public double offset(int columnX, int columnZ, double tick) {
        double elapsed = tick - start;
        if (elapsed <= 0 || finished(tick))
            return 0;
        double u = columnX + .5 - x, v = columnZ + .5 - z;
        if ((axis & 1) != 0) {
            double swap = u;
            u = v;
            v = swap;
        }
        if (axis >= 2)
            u = -u;
        // A square footprint covers the view's corner chunks. Only an outer
        // 32-block apron fades; distant visible chunks receive full amplitude.
        double distance =
                kind == Movement.STRESS ? Math.max(Math.abs(u), Math.abs(v)) : Math.hypot(u, v);
        double d = distance / radius;
        if (d >= 1)
            return 0;
        double edge = kind == Movement.STRESS ? 1 - ease((distance - radius + 32) / 32)
                                              : 1 - ease((d - .70) / .30);
        // Slow starts/ends bound vertical velocity; mirages are fast but shallow.
        double envelope = ease(elapsed / 120) * (hold ? 1 : ease((duration - elapsed) / 120));
        double phase = Math.clamp(elapsed / duration, 0, 1);
        double shape = switch (kind) {
            case WAVE -> Math.exp(-Math.pow((u / radius + 1.25 - phase * 2.5) / .22, 2));
            case RIPPLE -> Math.cos(Math.hypot(u, v) * .22 - elapsed * .045) * .5;
            case MIRAGE -> Math.exp(-Math.pow((u / radius + 1.3 - phase * 2.6) / .065, 2));
            case MOUNTAIN ->
                Math.pow(Math.max(0,
                                 1 - Math.abs(u / radius + .18 * Math.sin(v / radius * 5))
                                         - Math.abs(v / radius) * .85),
                        1.3);
            case RIDGE ->
                Math.exp(-Math.pow((v / radius - .15 * Math.sin(u / radius * 4)) / .18, 2));
            case RAVINE ->
                -Math.exp(-Math.pow((v / radius - .12 * Math.sin(u / radius * 5)) / .16, 2));
            case TILES ->
                .5
                        + .5
                                * Math.sin(Math.floor(columnX / 8.0) * 1.7
                                        + Math.floor(columnZ / 8.0) * 2.3
                                        + (hold ? 1.8 : elapsed * .012));
            case TERRACES -> Math.floor(Math.max(0, 1 - d) * 4) / 4;
            case STRESS ->
                .55 * Math.sin(u * .028 - elapsed * .014)
                        + .30 * Math.sin(v * .035 + elapsed * .011)
                        + .15 * Math.sin((u + v) * .018 - elapsed * .009);
        };
        return amplitude * envelope * edge * shape;
    }
}
