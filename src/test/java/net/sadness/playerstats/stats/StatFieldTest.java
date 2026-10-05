package net.sadness.playerstats.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatFieldTest {

    @Test
    void keysAndColumnsAreUnique() {
        Set<String> keys = new HashSet<>();
        Set<String> columns = new HashSet<>();
        for (StatField field : StatField.values()) {
            assertTrue(keys.add(field.key()));
            assertTrue(columns.add(field.column()));
        }
    }

    @Test
    void fromKeyIsCaseAndSeparatorInsensitive() {
        assertEquals(Optional.of(StatField.PLAYER_KILLS), StatField.fromKey("Player_Kills"));
        assertEquals(Optional.of(StatField.DEATHS), StatField.fromKey("DEATHS"));
    }

    @Test
    void fromKeyRejectsUnknownAndNull() {
        assertEquals(Optional.empty(), StatField.fromKey("nonsense"));
        assertEquals(Optional.empty(), StatField.fromKey(null));
        assertEquals(Optional.empty(), StatField.fromKey("deaths; DROP TABLE players;--"));
    }

    @Test
    void toStoredConvertsMetersToCentimeters() {
        assertEquals(1_200, StatField.DISTANCE_WALKED.toStored(12));
        assertEquals(12, StatField.PLAYTIME.toStored(12));
        assertEquals(0, StatField.DEATHS.toStored(0));
    }

    @Test
    void toStoredRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> StatField.DEATHS.toStored(-1));
        assertThrows(IllegalArgumentException.class, () -> StatField.DEATHS.toStored(StatField.MAX_DISPLAY_VALUE + 1));
    }

    @Test
    void paginationClampsPages() {
        assertEquals(1, Pagination.of(0, 10, 5).page());
        assertEquals(1, Pagination.of(0, 10, 5).totalPages());
        Pagination last = Pagination.of(25, 10, 99);
        assertEquals(3, last.page());
        assertEquals(20, last.offset());
        assertEquals(1, Pagination.of(25, 10, -4).page());
        assertEquals(1, Pagination.of(10, 10, 1).totalPages());
        assertThrows(IllegalArgumentException.class, () -> Pagination.of(5, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Pagination.of(-1, 5, 1));
    }
}
