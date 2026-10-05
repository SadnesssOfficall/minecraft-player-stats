package net.sadness.playerstats.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.sadness.playerstats.stats.LeaderboardEntry;
import net.sadness.playerstats.stats.PlayerDelta;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;
import net.sadness.playerstats.stats.StatValues;
import net.sadness.playerstats.storage.StatsRepository;
import net.sadness.playerstats.storage.StorageException;

final class InMemoryRepository implements StatsRepository {

    final AtomicBoolean failWrites = new AtomicBoolean();
    final AtomicInteger writeCalls = new AtomicInteger();
    private final Map<UUID, PlayerRecord> rows = new HashMap<>();

    synchronized void seed(UUID id, String name, StatValues values) {
        rows.put(id, new PlayerRecord(id, name, values));
    }

    @Override
    public synchronized void applyDeltas(Collection<PlayerDelta> deltas) throws StorageException {
        writeCalls.incrementAndGet();
        if (failWrites.get()) {
            throw new StorageException("simulated failure");
        }
        for (PlayerDelta delta : deltas) {
            StatValues current = rows.containsKey(delta.id()) ? rows.get(delta.id()).values() : StatValues.ZERO;
            rows.put(delta.id(), new PlayerRecord(delta.id(), delta.name(), current.plus(delta.values())));
        }
    }

    @Override
    public synchronized Optional<PlayerRecord> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public synchronized Optional<PlayerRecord> findByName(String name) {
        return rows.values().stream().filter(row -> row.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public synchronized void set(UUID id, String name, StatField field, long storedValue) {
        StatValues current = rows.containsKey(id) ? rows.get(id).values() : StatValues.ZERO;
        rows.put(id, new PlayerRecord(id, name, current.with(field, storedValue)));
    }

    @Override
    public synchronized void reset(UUID id) {
        PlayerRecord row = rows.get(id);
        if (row != null) {
            rows.put(id, new PlayerRecord(id, row.name(), StatValues.ZERO));
        }
    }

    @Override
    public synchronized List<LeaderboardEntry> top(StatField field, int limit, int offset) {
        return rows.values().stream()
                .filter(row -> row.values().get(field) > 0)
                .sorted(Comparator.comparingLong((PlayerRecord row) -> row.values().get(field)).reversed())
                .skip(offset)
                .limit(limit)
                .map(row -> new LeaderboardEntry(row.id(), row.name(), row.values().get(field)))
                .toList();
    }

    @Override
    public synchronized long countRanked(StatField field) {
        return rows.values().stream().filter(row -> row.values().get(field) > 0).count();
    }

    @Override
    public void close() {
        rows.clear();
    }
}
