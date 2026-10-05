package net.sadness.playerstats.stats;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum StatField {
    PLAYTIME("playtime", "playtime_seconds", Unit.SECONDS),
    JOINS("joins", "joins", Unit.COUNT),
    DEATHS("deaths", "deaths", Unit.COUNT),
    PLAYER_KILLS("player-kills", "player_kills", Unit.COUNT),
    MOB_KILLS("mob-kills", "mob_kills", Unit.COUNT),
    BLOCKS_BROKEN("blocks-broken", "blocks_broken", Unit.COUNT),
    BLOCKS_PLACED("blocks-placed", "blocks_placed", Unit.COUNT),
    DISTANCE_WALKED("distance-walked", "distance_walked_cm", Unit.DISTANCE),
    DISTANCE_FLOWN("distance-flown", "distance_flown_cm", Unit.DISTANCE),
    DISTANCE_SWUM("distance-swum", "distance_swum_cm", Unit.DISTANCE);

    public static final int SIZE = values().length;
    public static final long MAX_DISPLAY_VALUE = 1_000_000_000_000L;

    private static final Map<String, StatField> BY_KEY = indexByKey();

    private final String key;
    private final String column;
    private final Unit unit;

    StatField(String key, String column, Unit unit) {
        this.key = key;
        this.column = column;
        this.unit = unit;
    }

    public String key() {
        return key;
    }

    public String column() {
        return column;
    }

    public Unit unit() {
        return unit;
    }

    public long toStored(long displayValue) {
        if (displayValue < 0 || displayValue > MAX_DISPLAY_VALUE) {
            throw new IllegalArgumentException("Value out of range: " + displayValue);
        }
        return displayValue * unit.storedPerDisplayUnit;
    }

    public static Optional<StatField> fromKey(String input) {
        if (input == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEY.get(input.toLowerCase(Locale.ROOT).replace('_', '-')));
    }

    public static String allKeys() {
        return Stream.of(values()).map(StatField::key).collect(Collectors.joining(", "));
    }

    private static Map<String, StatField> indexByKey() {
        Map<String, StatField> index = new HashMap<>();
        for (StatField field : values()) {
            index.put(field.key, field);
        }
        return Map.copyOf(index);
    }

    public enum Unit {
        COUNT(1),
        SECONDS(1),
        DISTANCE(100);

        private final long storedPerDisplayUnit;

        Unit(long storedPerDisplayUnit) {
            this.storedPerDisplayUnit = storedPerDisplayUnit;
        }
    }
}
