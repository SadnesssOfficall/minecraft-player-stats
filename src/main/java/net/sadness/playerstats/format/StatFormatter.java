package net.sadness.playerstats.format;

import java.util.Locale;
import net.sadness.playerstats.stats.StatField;

public final class StatFormatter {

    private StatFormatter() {
    }

    public static String format(StatField field, long storedValue) {
        return switch (field.unit()) {
            case SECONDS -> DurationFormatter.format(storedValue);
            case DISTANCE -> DistanceFormatter.format(storedValue);
            case COUNT -> String.format(Locale.ROOT, "%,d", storedValue);
        };
    }
}
