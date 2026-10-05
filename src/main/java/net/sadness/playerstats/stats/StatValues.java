package net.sadness.playerstats.stats;

import java.util.Arrays;

public final class StatValues {

    public static final StatValues ZERO = new StatValues(new long[StatField.SIZE]);

    private final long[] values;

    private StatValues(long[] values) {
        this.values = values;
    }

    public static StatValues fromArray(long[] source) {
        if (source.length != StatField.SIZE) {
            throw new IllegalArgumentException("Expected " + StatField.SIZE + " values but got " + source.length);
        }
        return new StatValues(source.clone());
    }

    public static StatValues of(StatField field, long value) {
        return ZERO.with(field, value);
    }

    public long get(StatField field) {
        return values[field.ordinal()];
    }

    public StatValues with(StatField field, long value) {
        long[] copy = values.clone();
        copy[field.ordinal()] = value;
        return new StatValues(copy);
    }

    public StatValues plus(StatValues other) {
        long[] sum = new long[StatField.SIZE];
        for (int i = 0; i < sum.length; i++) {
            sum[i] = saturatedAdd(values[i], other.values[i]);
        }
        return new StatValues(sum);
    }

    public boolean isZero() {
        for (long value : values) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    public long[] toArray() {
        return values.clone();
    }

    public static long saturatedAdd(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return sum;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StatValues that && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("StatValues{");
        for (StatField field : StatField.values()) {
            if (field.ordinal() > 0) {
                text.append(", ");
            }
            text.append(field.key()).append('=').append(values[field.ordinal()]);
        }
        return text.append('}').toString();
    }
}
