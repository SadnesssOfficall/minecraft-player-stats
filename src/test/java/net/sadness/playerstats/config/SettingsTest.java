package net.sadness.playerstats.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static Map<String, Object> valid() {
        Map<String, Object> values = new HashMap<>();
        values.put(Settings.KEY_DATABASE_FILE, "stats.db");
        values.put(Settings.KEY_FLUSH_INTERVAL, 60);
        values.put(Settings.KEY_QUEUE_CAPACITY, 1024);
        values.put(Settings.KEY_SHUTDOWN_TIMEOUT, 15);
        values.put(Settings.KEY_MIN_STEP, 0.1);
        values.put(Settings.KEY_MAX_STEP, 8.0);
        values.put(Settings.KEY_PAGE_SIZE, 10);
        return values;
    }

    private static ConfigException failure(Map<String, Object> values) {
        return assertThrows(ConfigException.class, () -> Settings.parse(values::get));
    }

    @Test
    void parsesValidConfiguration() throws Exception {
        Settings settings = Settings.parse(valid()::get);
        assertEquals("stats.db", settings.databaseFile());
        assertEquals(Duration.ofSeconds(60), settings.flushInterval());
        assertEquals(1024, settings.queueCapacity());
        assertEquals(Duration.ofSeconds(15), settings.shutdownTimeout());
        assertEquals(0.1, settings.minStep());
        assertEquals(8.0, settings.maxStep());
        assertEquals(10, settings.pageSize());
    }

    @Test
    void acceptsIntegerValuesForDecimals() throws Exception {
        Map<String, Object> values = valid();
        values.put(Settings.KEY_MIN_STEP, 0);
        values.put(Settings.KEY_MAX_STEP, 8);
        assertEquals(8.0, Settings.parse(values::get).maxStep());
    }

    @Test
    void rejectsMissingKeys() {
        assertEquals(7, failure(new HashMap<>()).problems().size());
    }

    @Test
    void rejectsPathTraversalInFileName() {
        for (String name : new String[] {"../stats.db", "a/b.db", "a\\b", "", "-lead", "a".repeat(65)}) {
            Map<String, Object> values = valid();
            values.put(Settings.KEY_DATABASE_FILE, name);
            assertEquals(1, failure(values).problems().size(), name);
        }
    }

    @Test
    void rejectsOutOfRangeAndWrongTypes() {
        Map<String, Object> values = valid();
        values.put(Settings.KEY_FLUSH_INTERVAL, 1);
        values.put(Settings.KEY_QUEUE_CAPACITY, "many");
        values.put(Settings.KEY_SHUTDOWN_TIMEOUT, 1.5);
        values.put(Settings.KEY_PAGE_SIZE, 0);
        values.put(Settings.KEY_MIN_STEP, Double.NaN);
        assertEquals(5, failure(values).problems().size());
    }

    @Test
    void rejectsMinStepNotBelowMaxStep() {
        Map<String, Object> values = valid();
        values.put(Settings.KEY_MIN_STEP, 3.0);
        values.put(Settings.KEY_MAX_STEP, 2.0);
        assertTrue(failure(values).getMessage().contains(Settings.KEY_MAX_STEP));
    }
}
