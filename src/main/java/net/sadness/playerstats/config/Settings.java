package net.sadness.playerstats.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public record Settings(
        String databaseFile,
        Duration flushInterval,
        int queueCapacity,
        Duration shutdownTimeout,
        double minStep,
        double maxStep,
        int pageSize) {

    public static final String KEY_DATABASE_FILE = "storage.file";
    public static final String KEY_FLUSH_INTERVAL = "storage.flush-interval-seconds";
    public static final String KEY_QUEUE_CAPACITY = "storage.queue-capacity";
    public static final String KEY_SHUTDOWN_TIMEOUT = "storage.shutdown-timeout-seconds";
    public static final String KEY_MIN_STEP = "movement.min-step";
    public static final String KEY_MAX_STEP = "movement.max-step";
    public static final String KEY_PAGE_SIZE = "leaderboard.page-size";

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final int MIN_FLUSH_SECONDS = 5;
    private static final int MAX_FLUSH_SECONDS = 3_600;
    private static final int MIN_QUEUE_CAPACITY = 64;
    private static final int MAX_QUEUE_CAPACITY = 65_536;
    private static final int MIN_SHUTDOWN_SECONDS = 1;
    private static final int MAX_SHUTDOWN_SECONDS = 120;
    private static final double MAX_MIN_STEP = 5.0;
    private static final double MAX_MAX_STEP = 64.0;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    public static Settings parse(Function<String, Object> source) throws ConfigException {
        Reader reader = new Reader(source);
        String file = reader.fileName(KEY_DATABASE_FILE);
        int flush = reader.integer(KEY_FLUSH_INTERVAL, MIN_FLUSH_SECONDS, MAX_FLUSH_SECONDS);
        int queue = reader.integer(KEY_QUEUE_CAPACITY, MIN_QUEUE_CAPACITY, MAX_QUEUE_CAPACITY);
        int shutdown = reader.integer(KEY_SHUTDOWN_TIMEOUT, MIN_SHUTDOWN_SECONDS, MAX_SHUTDOWN_SECONDS);
        double minStep = reader.decimal(KEY_MIN_STEP, 0.0, MAX_MIN_STEP);
        double maxStep = reader.decimal(KEY_MAX_STEP, Math.nextUp(0.0), MAX_MAX_STEP);
        int pageSize = reader.integer(KEY_PAGE_SIZE, MIN_PAGE_SIZE, MAX_PAGE_SIZE);
        reader.require(minStep < maxStep, KEY_MAX_STEP + " must be greater than " + KEY_MIN_STEP);
        reader.throwIfInvalid();
        return new Settings(file, Duration.ofSeconds(flush), queue, Duration.ofSeconds(shutdown), minStep, maxStep, pageSize);
    }

    private static final class Reader {

        private final Function<String, Object> source;
        private final List<String> problems = new ArrayList<>();

        Reader(Function<String, Object> source) {
            this.source = source;
        }

        String fileName(String key) {
            Object raw = source.apply(key);
            if (raw instanceof String text && FILE_NAME.matcher(text).matches()) {
                return text;
            }
            problems.add(key + " must be a plain file name made of letters, digits, '.', '_' or '-' (max 64 characters)");
            return "";
        }

        int integer(String key, int min, int max) {
            Object raw = source.apply(key);
            if (raw instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
                double value = number.doubleValue();
                if (value >= min && value <= max) {
                    return (int) value;
                }
            }
            problems.add(key + " must be a whole number between " + min + " and " + max);
            return min;
        }

        double decimal(String key, double min, double max) {
            Object raw = source.apply(key);
            if (raw instanceof Number number) {
                double value = number.doubleValue();
                if (Double.isFinite(value) && value >= min && value <= max) {
                    return value;
                }
            }
            problems.add(key + " must be a number between " + min + " and " + max);
            return min;
        }

        void require(boolean condition, String message) {
            if (!condition && problems.isEmpty()) {
                problems.add(message);
            }
        }

        void throwIfInvalid() throws ConfigException {
            if (!problems.isEmpty()) {
                throw new ConfigException(problems);
            }
        }
    }
}
