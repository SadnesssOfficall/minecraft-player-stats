package net.sadness.playerstats.stats;

import java.util.Objects;
import java.util.UUID;

public record PlayerRecord(UUID id, String name, StatValues values) {

    public PlayerRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(values, "values");
    }
}
