package io.github.partywith8.epd.motion;

import java.util.List;

/** Atomic snapshots avoid exposing half a network update to collision or rendering. */
public record MotionField(long revision, List<MotionEvent> events, long resetStart) {
    public static final int MAX_EVENTS = 8;
    public static final MotionField EMPTY = new MotionField(0, List.of(), -1);

    public MotionField {
        events = List.copyOf(events);
        if (revision < 0 || events.size() > MAX_EVENTS || resetStart < -1
                || events.stream().map(MotionEvent::id).distinct().count() != events.size())
            throw new IllegalArgumentException("Invalid motion snapshot");
    }

    public double offset(int x, int z, double tick) {
        double sum = 0;
        // Freeze the underlying functions during reset, then ease the whole field
        // to zero. Fading each moving wave independently would produce a jump.
        double sample = resetStart < 0 ? tick : Math.min(tick, resetStart);
        for (var event : events)
            sum += event.offset(x, z, sample);
        double fade = resetStart < 0 ? 1 : 1 - MotionEvent.ease((tick - resetStart) / 240);
        return Math.clamp(sum, -40, 64) * fade;
    }

    /** Preserve snapshot identity and avoid per-tick allocation unless an event expires. */
    public MotionField expire(long tick) {
        if (resetStart >= 0)
            return this;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).finished(tick)) {
                var remaining = new java.util.ArrayList<MotionEvent>(events.size() - 1);
                for (var event : events)
                    if (!event.finished(tick))
                        remaining.add(event);
                return new MotionField(revision + 1, remaining, -1);
            }
        }
        return this;
    }
}
