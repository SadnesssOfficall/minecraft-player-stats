package net.sadness.playerstats.format;

public final class DurationFormatter {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3_600;
    private static final long SECONDS_PER_DAY = 86_400;

    private DurationFormatter() {
    }

    public static String format(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0s";
        }
        long days = totalSeconds / SECONDS_PER_DAY;
        long hours = totalSeconds % SECONDS_PER_DAY / SECONDS_PER_HOUR;
        long minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        long seconds = totalSeconds % SECONDS_PER_MINUTE;
        StringBuilder text = new StringBuilder();
        append(text, days, 'd');
        append(text, hours, 'h');
        append(text, minutes, 'm');
        append(text, seconds, 's');
        return text.toString();
    }

    private static void append(StringBuilder text, long amount, char unit) {
        if (amount == 0) {
            return;
        }
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(amount).append(unit);
    }
}
