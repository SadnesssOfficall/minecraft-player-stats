package net.sadness.playerstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatValuesTest {

    @Test
    void zeroIsZeroAndImmutable() {
        assertTrue(StatValues.ZERO.isZero());
        StatValues changed = StatValues.ZERO.with(StatField.DEATHS, 3);
        assertEquals(3, changed.get(StatField.DEATHS));
        assertTrue(StatValues.ZERO.isZero());
        assertFalse(changed.isZero());
    }

    @Test
    void plusAddsFieldWise() {
        StatValues sum = StatValues.of(StatField.JOINS, 2).plus(StatValues.of(StatField.JOINS, 5).with(StatField.DEATHS, 1));
        assertEquals(7, sum.get(StatField.JOINS));
        assertEquals(1, sum.get(StatField.DEATHS));
    }

    @Test
    void additionSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, StatValues.saturatedAdd(Long.MAX_VALUE, 1));
        assertEquals(Long.MIN_VALUE, StatValues.saturatedAdd(Long.MIN_VALUE, -1));
        assertEquals(5, StatValues.saturatedAdd(2, 3));
    }

    @Test
    void fromArrayRejectsWrongLengthAndCopiesInput() {
        assertThrows(IllegalArgumentException.class, () -> StatValues.fromArray(new long[1]));
        long[] source = new long[StatField.SIZE];
        StatValues values = StatValues.fromArray(source);
        source[0] = 9;
        assertEquals(0, values.get(StatField.PLAYTIME));
    }

    @Test
    void equalityIsValueBased() {
        StatValues a = StatValues.of(StatField.MOB_KILLS, 4);
        StatValues b = StatValues.ZERO.with(StatField.MOB_KILLS, 4);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void deltaRejectsNegativeValuesAndBlankNames() {
        UUID id = UUID.randomUUID();
        StatValues negative = StatValues.of(StatField.JOINS, -1);
        assertThrows(IllegalArgumentException.class, () -> new PlayerDelta(id, "Steve", negative));
        assertThrows(IllegalArgumentException.class, () -> new PlayerDelta(id, " ", StatValues.ZERO));
    }
}
