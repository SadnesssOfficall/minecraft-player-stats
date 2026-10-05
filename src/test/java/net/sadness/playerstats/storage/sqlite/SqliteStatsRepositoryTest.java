package net.sadness.playerstats.storage.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.sadness.playerstats.stats.LeaderboardEntry;
import net.sadness.playerstats.stats.PlayerDelta;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;
import net.sadness.playerstats.stats.StatValues;
import net.sadness.playerstats.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStatsRepositoryTest {

    private static final long NOW = 1_700_000_000_000L;

    @TempDir
    Path directory;

    private Path file;
    private SqliteStatsRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        file = directory.resolve("nested").resolve("stats.db");
        repository = SqliteStatsRepository.open(file, () -> NOW);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    private static PlayerDelta delta(UUID id, String name, StatField field, long amount) {
        return new PlayerDelta(id, name, StatValues.of(field, amount));
    }

    @Test
    void createsMissingParentDirectoriesAndEnablesWal() throws Exception {
        assertTrue(file.toFile().exists());
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = raw.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA journal_mode")) {
            assertTrue(result.next());
            assertEquals("wal", result.getString(1));
        }
    }

    @Test
    void insertsNewPlayer() throws Exception {
        UUID id = UUID.randomUUID();
        repository.applyDeltas(List.of(delta(id, "Steve", StatField.DEATHS, 2)));
        PlayerRecord record = repository.findById(id).orElseThrow();
        assertEquals("Steve", record.name());
        assertEquals(2, record.values().get(StatField.DEATHS));
        assertEquals(0, record.values().get(StatField.JOINS));
    }

    @Test
    void upsertIncrementsAndUpdatesName() throws Exception {
        UUID id = UUID.randomUUID();
        repository.applyDeltas(List.of(delta(id, "Old", StatField.JOINS, 1)));
        repository.applyDeltas(List.of(new PlayerDelta(id, "New", StatValues.of(StatField.JOINS, 4).with(StatField.PLAYTIME, 90))));
        PlayerRecord record = repository.findById(id).orElseThrow();
        assertEquals("New", record.name());
        assertEquals(5, record.values().get(StatField.JOINS));
        assertEquals(90, record.values().get(StatField.PLAYTIME));
    }

    @Test
    void emptyBatchIsNoOp() throws Exception {
        repository.applyDeltas(List.of());
        assertEquals(0, repository.countRanked(StatField.JOINS));
    }

    @Test
    void batchWritesManyPlayersAtomically() throws Exception {
        List<PlayerDelta> batch = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(delta(UUID.randomUUID(), "P" + i, StatField.BLOCKS_BROKEN, i + 1));
        }
        repository.applyDeltas(batch);
        assertEquals(50, repository.countRanked(StatField.BLOCKS_BROKEN));
    }

    @Test
    void findMissingReturnsEmpty() throws Exception {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
        assertTrue(repository.findByName("Nobody").isEmpty());
    }

    @Test
    void findByNameIsCaseInsensitiveAndTreatsInputAsData() throws Exception {
        UUID id = UUID.randomUUID();
        repository.applyDeltas(List.of(delta(id, "Steve", StatField.JOINS, 1)));
        assertEquals(id, repository.findByName("sTEVE").orElseThrow().id());
        assertTrue(repository.findByName("x' OR '1'='1").isEmpty());
    }

    @Test
    void setOverwritesOneFieldAndCreatesRow() throws Exception {
        UUID id = UUID.randomUUID();
        repository.applyDeltas(List.of(new PlayerDelta(id, "Alex", StatValues.of(StatField.DEATHS, 9).with(StatField.JOINS, 3))));
        repository.set(id, "Alex", StatField.DEATHS, 1);
        PlayerRecord record = repository.findById(id).orElseThrow();
        assertEquals(1, record.values().get(StatField.DEATHS));
        assertEquals(3, record.values().get(StatField.JOINS));
        UUID fresh = UUID.randomUUID();
        repository.set(fresh, "Fresh", StatField.MOB_KILLS, 7);
        assertEquals(7, repository.findById(fresh).orElseThrow().values().get(StatField.MOB_KILLS));
    }

    @Test
    void setRejectsInvalidArguments() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> repository.set(id, "A", StatField.DEATHS, -1));
        assertThrows(IllegalArgumentException.class, () -> repository.set(id, " ", StatField.DEATHS, 1));
    }

    @Test
    void resetZeroesAllStatsAndKeepsRow() throws Exception {
        UUID id = UUID.randomUUID();
        repository.applyDeltas(List.of(new PlayerDelta(id, "Alex", StatValues.of(StatField.DEATHS, 9).with(StatField.PLAYTIME, 100))));
        repository.reset(id);
        PlayerRecord record = repository.findById(id).orElseThrow();
        assertTrue(record.values().isZero());
        assertEquals("Alex", record.name());
        repository.reset(UUID.randomUUID());
    }

    @Test
    void topOrdersByValueThenNameAndPaginates() throws Exception {
        repository.applyDeltas(List.of(
                delta(UUID.randomUUID(), "Charlie", StatField.DEATHS, 5),
                delta(UUID.randomUUID(), "alice", StatField.DEATHS, 5),
                delta(UUID.randomUUID(), "Bob", StatField.DEATHS, 9),
                delta(UUID.randomUUID(), "Zero", StatField.JOINS, 1)));
        List<LeaderboardEntry> all = repository.top(StatField.DEATHS, 10, 0);
        assertEquals(List.of("Bob", "alice", "Charlie"), all.stream().map(LeaderboardEntry::name).toList());
        assertEquals(9, all.get(0).value());
        assertEquals(List.of("alice"), repository.top(StatField.DEATHS, 1, 1).stream().map(LeaderboardEntry::name).toList());
        assertTrue(repository.top(StatField.DEATHS, 5, 3).isEmpty());
        assertEquals(3, repository.countRanked(StatField.DEATHS));
    }

    @Test
    void topRejectsInvalidPaging() {
        assertThrows(IllegalArgumentException.class, () -> repository.top(StatField.DEATHS, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.top(StatField.DEATHS, 1, -1));
    }

    @Test
    void persistsAcrossReopen() throws Exception {
        UUID id = UUID.randomUUID();
        repository.applyDeltas(List.of(delta(id, "Steve", StatField.JOINS, 3)));
        repository.close();
        repository = SqliteStatsRepository.open(file, () -> NOW);
        assertEquals(3, repository.findById(id).orElseThrow().values().get(StatField.JOINS));
    }

    @Test
    void concurrentIncrementsAreNotLost() throws Exception {
        UUID id = UUID.randomUUID();
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    repository.applyDeltas(List.of(delta(id, "Steve", StatField.BLOCKS_PLACED, 1)));
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        assertEquals(threads * perThread, repository.findById(id).orElseThrow().values().get(StatField.BLOCKS_PLACED));
    }

    @Test
    void corruptPlayerIdSurfacesAsStorageException() throws Exception {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement statement = raw.createStatement()) {
            statement.execute("INSERT INTO players (uuid, name, deaths) VALUES ('not-a-uuid', 'Broken', 4)");
        }
        assertThrows(StorageException.class, () -> repository.findByName("Broken"));
        assertThrows(StorageException.class, () -> repository.top(StatField.DEATHS, 10, 0));
    }

    @Test
    void migratesVersionOneDatabaseKeepingData() throws Exception {
        Path legacy = directory.resolve("legacy.db");
        UUID id = UUID.randomUUID();
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + legacy)) {
            Migrations.apply(raw, 1);
            assertEquals(1, Migrations.readVersion(raw));
            try (Statement statement = raw.createStatement()) {
                statement.execute("INSERT INTO players (uuid, name, deaths) VALUES ('" + id + "', 'Old', 11)");
            }
        }
        try (SqliteStatsRepository migrated = SqliteStatsRepository.open(legacy, () -> NOW)) {
            PlayerRecord record = migrated.findById(id).orElseThrow();
            assertEquals(11, record.values().get(StatField.DEATHS));
            migrated.applyDeltas(List.of(delta(id, "Old", StatField.DEATHS, 1)));
            assertEquals(12, migrated.findById(id).orElseThrow().values().get(StatField.DEATHS));
        }
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + legacy)) {
            assertEquals(Migrations.LATEST_VERSION, Migrations.readVersion(raw));
        }
    }

    @Test
    void migrationsAreIdempotent() throws Exception {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("again.db"))) {
            Migrations.apply(raw);
            Migrations.apply(raw);
            assertEquals(Migrations.LATEST_VERSION, Migrations.readVersion(raw));
        }
    }

    @Test
    void refusesDatabaseFromNewerVersion() throws Exception {
        Path future = directory.resolve("future.db");
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + future);
                Statement statement = raw.createStatement()) {
            statement.execute("PRAGMA user_version = " + (Migrations.LATEST_VERSION + 1));
        }
        assertThrows(StorageException.class, () -> SqliteStatsRepository.open(future, () -> NOW));
    }

    @Test
    void failedMigrationRollsBack() throws Exception {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("clash.db"))) {
            try (Statement statement = raw.createStatement()) {
                statement.execute("CREATE TABLE players (uuid TEXT)");
            }
            assertThrows(StorageException.class, () -> Migrations.apply(raw));
            assertEquals(0, Migrations.readVersion(raw));
            assertFalse(raw.isClosed());
        }
    }

    @Test
    void rejectsGarbageFile() throws Exception {
        Path garbage = directory.resolve("garbage.db");
        Files.writeString(garbage, "this is definitely not a sqlite database, just text padding".repeat(40));
        assertThrows(StorageException.class, () -> SqliteStatsRepository.open(garbage, () -> NOW));
    }

    @Test
    void closedRepositoryReportsStorageException() {
        repository.close();
        assertThrows(StorageException.class, () -> repository.findById(UUID.randomUUID()));
    }
}
