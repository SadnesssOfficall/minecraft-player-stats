package net.sadness.playerstats.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MovementTrackerTest {

    private static final UUID OVERWORLD = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();

    private MovementTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new MovementTracker(0.1, 8.0);
        tracker.reanchor(OVERWORLD, 0, 64, 0);
    }

    @Test
    void countsNormalStepsInCentimeters() {
        assertEquals(100, tracker.record(OVERWORLD, 1, 64, 0, MovementMode.WALKING));
        assertEquals(500, tracker.record(OVERWORLD, 1, 64, 5, MovementMode.WALKING));
    }

    @Test
    void ignoresTinyStepsButMeasuresAgainstTheLastCountedAnchor() {
        assertEquals(0, tracker.record(OVERWORLD, 0.05, 64, 0, MovementMode.WALKING));
        assertEquals(0, tracker.record(OVERWORLD, 0.09, 64, 0, MovementMode.WALKING));
        assertEquals(15, tracker.record(OVERWORLD, 0.15, 64, 0, MovementMode.WALKING));
    }

    @Test
    void ignoresTeleportsAndReanchors() {
        assertEquals(0, tracker.record(OVERWORLD, 500, 64, 0, MovementMode.WALKING));
        assertEquals(100, tracker.record(OVERWORLD, 501, 64, 0, MovementMode.WALKING));
    }

    @Test
    void stepExactlyAtMaximumIsCounted() {
        assertEquals(800, tracker.record(OVERWORLD, 8, 64, 0, MovementMode.FLYING));
    }

    @Test
    void worldChangeReanchorsWithoutCounting() {
        assertEquals(0, tracker.record(NETHER, 3, 64, 0, MovementMode.WALKING));
        assertEquals(200, tracker.record(NETHER, 5, 64, 0, MovementMode.WALKING));
    }

    @Test
    void firstSampleOnlyAnchors() {
        MovementTracker fresh = new MovementTracker(0.1, 8.0);
        assertEquals(0, fresh.record(OVERWORLD, 3, 64, 0, MovementMode.WALKING));
        assertEquals(100, fresh.record(OVERWORLD, 4, 64, 0, MovementMode.WALKING));
    }

    @Test
    void nonFiniteCoordinatesAreIgnored() {
        assertEquals(0, tracker.record(OVERWORLD, Double.NaN, 64, 0, MovementMode.WALKING));
        assertEquals(0, tracker.record(OVERWORLD, Double.POSITIVE_INFINITY, 64, 0, MovementMode.WALKING));
        assertEquals(100, tracker.record(OVERWORLD, 1, 64, 0, MovementMode.WALKING));
    }

    @Test
    void fractionalCentimetersAreCarried() {
        long total = 0;
        double x = 0;
        for (int i = 0; i < 4; i++) {
            x += 0.255;
            total += tracker.record(OVERWORLD, x, 64, 0, MovementMode.SWIMMING);
        }
        assertEquals(102, total);
    }

    @Test
    void verticalMovementCounts() {
        assertEquals(300, tracker.record(OVERWORLD, 0, 67, 0, MovementMode.FLYING));
    }

    @Test
    void constructorRejectsInvalidBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MovementTracker(-1, 8));
        assertThrows(IllegalArgumentException.class, () -> new MovementTracker(5, 5));
        assertThrows(IllegalArgumentException.class, () -> new MovementTracker(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new MovementTracker(Double.NaN, 8));
    }
}
