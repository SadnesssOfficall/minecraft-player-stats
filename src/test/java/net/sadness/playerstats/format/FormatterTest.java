package net.sadness.playerstats.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.sadness.playerstats.stats.StatField;
import org.junit.jupiter.api.Test;

class FormatterTest {

    @Test
    void durationHandlesZeroAndNegative() {
        assertEquals("0s", DurationFormatter.format(0));
        assertEquals("0s", DurationFormatter.format(-5));
    }

    @Test
    void durationSkipsEmptyUnits() {
        assertEquals("59s", DurationFormatter.format(59));
        assertEquals("1m", DurationFormatter.format(60));
        assertEquals("1h 1s", DurationFormatter.format(3_601));
        assertEquals("1d 1h 1m 1s", DurationFormatter.format(90_061));
        assertEquals("2d", DurationFormatter.format(172_800));
    }

    @Test
    void durationHandlesLargeValues() {
        assertEquals("106751991167300d 15h 30m 7s", DurationFormatter.format(Long.MAX_VALUE));
    }

    @Test
    void distanceUsesMetersBelowOneKilometer() {
        assertEquals("0.00 m", DistanceFormatter.format(0));
        assertEquals("0.05 m", DistanceFormatter.format(5));
        assertEquals("12.34 m", DistanceFormatter.format(1_234));
        assertEquals("999.99 m", DistanceFormatter.format(99_999));
    }

    @Test
    void distanceUsesKilometersFromOneKilometer() {
        assertEquals("1.00 km", DistanceFormatter.format(100_000));
        assertEquals("1.23 km", DistanceFormatter.format(123_456));
        assertEquals("1,234.56 km", DistanceFormatter.format(123_456_789));
    }

    @Test
    void distanceClampsNegativeValues() {
        assertEquals("0.00 m", DistanceFormatter.format(-100));
    }

    @Test
    void statFormatterDispatchesByUnit() {
        assertEquals("1h", StatFormatter.format(StatField.PLAYTIME, 3_600));
        assertEquals("1,234", StatFormatter.format(StatField.DEATHS, 1_234));
        assertEquals("2.00 m", StatFormatter.format(StatField.DISTANCE_WALKED, 200));
    }
}
