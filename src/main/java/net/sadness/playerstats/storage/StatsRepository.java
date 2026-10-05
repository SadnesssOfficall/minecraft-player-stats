package net.sadness.playerstats.storage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.sadness.playerstats.stats.LeaderboardEntry;
import net.sadness.playerstats.stats.PlayerDelta;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;

public interface StatsRepository extends AutoCloseable {

    void applyDeltas(Collection<PlayerDelta> deltas) throws StorageException;

    Optional<PlayerRecord> findById(UUID id) throws StorageException;

    Optional<PlayerRecord> findByName(String name) throws StorageException;

    void set(UUID id, String name, StatField field, long storedValue) throws StorageException;

    void reset(UUID id) throws StorageException;

    List<LeaderboardEntry> top(StatField field, int limit, int offset) throws StorageException;

    long countRanked(StatField field) throws StorageException;

    @Override
    void close();
}
