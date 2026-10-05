package net.sadness.playerstats.stats;

import java.util.Objects;
import java.util.UUID;

public record PlayerDelta(UUID id, String name, StatValues values) {

    public PlayerDelta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(values, "values");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        for (long value : values.toArray()) {
            if (value < 0) {
                throw new IllegalArgumentException("Deltas must not be negative");
            }
        }
    }
}
