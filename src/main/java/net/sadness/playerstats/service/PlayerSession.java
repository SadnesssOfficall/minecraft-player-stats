package net.sadness.playerstats.service;

import java.util.Optional;
import java.util.UUID;
import net.sadness.playerstats.stats.StatField;
import net.sadness.playerstats.stats.StatValues;

final class PlayerSession {

    private static final long MILLIS_PER_SECOND = 1_000L;

    record Drain(StatValues values, long[] generations) {
    }

    private final UUID id;
    private final String name;
    private final long[] session = new long[StatField.SIZE];
    private final long[] flushed = new long[StatField.SIZE];
    private final long[] generations = new long[StatField.SIZE];

    private StatValues base;
    private long playtimeMark;
    private boolean closed;
    private int inFlight;

    PlayerSession(UUID id, String name, long nowMillis) {
        this.id = id;
        this.name = name;
        this.playtimeMark = nowMillis;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    synchronized void add(StatField field, long amount) {
        int index = field.ordinal();
        session[index] = StatValues.saturatedAdd(session[index], amount);
    }

    synchronized void accrue(long nowMillis) {
        if (closed) {
            return;
        }
        long seconds = (nowMillis - playtimeMark) / MILLIS_PER_SECOND;
        if (seconds <= 0) {
            return;
        }
        add(StatField.PLAYTIME, seconds);
        playtimeMark += seconds * MILLIS_PER_SECOND;
    }

    synchronized void close(long nowMillis) {
        accrue(nowMillis);
        closed = true;
    }

    synchronized void completeLoad(StatValues loaded) {
        base = loaded;
    }

    synchronized boolean isLoaded() {
        return base != null;
    }

    synchronized Optional<StatValues> view() {
        if (base == null) {
            return Optional.empty();
        }
        return Optional.of(base.plus(StatValues.fromArray(session)));
    }

    synchronized Optional<Drain> drain() {
        long[] delta = new long[StatField.SIZE];
        boolean any = false;
        for (int i = 0; i < delta.length; i++) {
            delta[i] = session[i] - flushed[i];
            any |= delta[i] != 0;
        }
        if (!any) {
            return Optional.empty();
        }
        System.arraycopy(session, 0, flushed, 0, session.length);
        inFlight++;
        return Optional.of(new Drain(StatValues.fromArray(delta), generations.clone()));
    }

    synchronized StatValues currentDelta(Drain drain) {
        long[] delta = drain.values().toArray();
        for (int i = 0; i < delta.length; i++) {
            if (generations[i] != drain.generations()[i]) {
                delta[i] = 0;
            }
        }
        return StatValues.fromArray(delta);
    }

    synchronized void acknowledge() {
        inFlight--;
    }

    synchronized void restore(Drain drain) {
        inFlight--;
        long[] amounts = drain.values().toArray();
        for (int i = 0; i < flushed.length; i++) {
            if (generations[i] == drain.generations()[i]) {
                flushed[i] -= amounts[i];
            }
        }
    }

    synchronized void applySet(StatField field, long storedValue) {
        int index = field.ordinal();
        base = base.with(field, storedValue);
        session[index] = 0;
        flushed[index] = 0;
        generations[index]++;
    }

    synchronized void applyReset() {
        base = StatValues.ZERO;
        for (int i = 0; i < session.length; i++) {
            session[i] = 0;
            flushed[i] = 0;
            generations[i]++;
        }
    }

    synchronized boolean isSettled() {
        if (inFlight != 0) {
            return false;
        }
        for (int i = 0; i < session.length; i++) {
            if (session[i] != flushed[i]) {
                return false;
            }
        }
        return true;
    }
}
