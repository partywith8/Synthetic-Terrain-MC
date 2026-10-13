package io.github.partywith8.epd.motion;

/** IDs are part of the motion protocol. Never reorder existing values. */
public enum Movement {
    WAVE,
    RIPPLE,
    MIRAGE,
    MOUNTAIN,
    RIDGE,
    RAVINE,
    TILES,
    TERRACES,
    STRESS;

    public boolean formation() {
        return ordinal() >= MOUNTAIN.ordinal() && this != STRESS;
    }

    /** Stress is an explicit benchmark scene, never an ambient event. */
    public static final java.util.List<Movement> AMBIENT =
            java.util.List.of(WAVE, RIPPLE, MIRAGE, MOUNTAIN, RIDGE, RAVINE, TILES, TERRACES);
}
