package net.sadness.playerstats.movement;

import net.sadness.playerstats.stats.StatField;

public enum MovementMode {
    WALKING(StatField.DISTANCE_WALKED),
    FLYING(StatField.DISTANCE_FLOWN),
    SWIMMING(StatField.DISTANCE_SWUM);

    private final StatField field;

    MovementMode(StatField field) {
        this.field = field;
    }

    public StatField field() {
        return field;
    }
}
