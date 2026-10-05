package net.sadness.playerstats.format;

import java.util.Locale;

public final class DistanceFormatter {

    private static final long CENTIMETERS_PER_METER = 100;
    private static final long CENTIMETERS_PER_KILOMETER = 100_000;
    private static final long CENTIMETERS_PER_HUNDREDTH_KILOMETER = 1_000;

    private DistanceFormatter() {
    }

    public static String format(long centimeters) {
        long value = Math.max(0, centimeters);
        if (value >= CENTIMETERS_PER_KILOMETER) {
            long kilometers = value / CENTIMETERS_PER_KILOMETER;
            long hundredths = value % CENTIMETERS_PER_KILOMETER / CENTIMETERS_PER_HUNDREDTH_KILOMETER;
            return String.format(Locale.ROOT, "%,d.%02d km", kilometers, hundredths);
        }
        return String.format(Locale.ROOT, "%d.%02d m", value / CENTIMETERS_PER_METER, value % CENTIMETERS_PER_METER);
    }
}
