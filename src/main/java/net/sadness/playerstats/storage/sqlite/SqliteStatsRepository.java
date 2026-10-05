package net.sadness.playerstats.storage.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.sadness.playerstats.stats.LeaderboardEntry;
import net.sadness.playerstats.stats.PlayerDelta;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;
import net.sadness.playerstats.stats.StatValues;
import net.sadness.playerstats.storage.StatsRepository;
import net.sadness.playerstats.storage.StorageException;
import org.sqlite.SQLiteConfig;

public final class SqliteStatsRepository implements StatsRepository {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;
    private static final int ID_COLUMN = 1;
    private static final int NAME_COLUMN = 2;
    private static final int FIRST_STAT_COLUMN = 3;

    private final Connection connection;
    private final LongSupplier clock;

    private SqliteStatsRepository(Connection connection, LongSupplier clock) {
        this.connection = connection;
        this.clock = clock;
    }

    public static SqliteStatsRepository open(Path file, LongSupplier clock) throws StorageException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(clock, "clock");
        Connection connection = null;
        try {
            Path absolute = file.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            SQLiteConfig config = new SQLiteConfig();
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
            connection = config.createConnection(JDBC_PREFIX + absolute);
            Migrations.apply(connection);
            return new SqliteStatsRepository(connection, clock);
        } catch (IOException | SQLException | StorageException e) {
            closeQuietly(connection);
            if (e instanceof StorageException storageFailure) {
                throw storageFailure;
            }
            throw new StorageException("Unable to open the statistics database " + file, e);
        }
    }

    @Override
    public synchronized void applyDeltas(Collection<PlayerDelta> deltas) throws StorageException {
        if (deltas.isEmpty()) {
            return;
        }
        long now = clock.getAsLong();
        try {
            Transaction.run(connection, () -> {
                try (PreparedStatement statement = connection.prepareStatement(Sql.UPSERT_DELTA)) {
                    for (PlayerDelta delta : deltas) {
                        int index = 1;
                        statement.setString(index++, delta.id().toString());
                        statement.setString(index++, delta.name());
                        statement.setLong(index++, now);
                        for (StatField field : StatField.values()) {
                            statement.setLong(index++, delta.values().get(field));
                        }
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            });
        } catch (SQLException e) {
            throw new StorageException("Unable to write statistics", e);
        }
    }

    @Override
    public synchronized Optional<PlayerRecord> findById(UUID id) throws StorageException {
        Objects.requireNonNull(id, "id");
        try (PreparedStatement statement = connection.prepareStatement(Sql.SELECT_BY_ID)) {
            statement.setString(1, id.toString());
            return readRecord(statement);
        } catch (SQLException e) {
            throw new StorageException("Unable to read statistics", e);
        }
    }

    @Override
    public synchronized Optional<PlayerRecord> findByName(String name) throws StorageException {
        Objects.requireNonNull(name, "name");
        try (PreparedStatement statement = connection.prepareStatement(Sql.SELECT_BY_NAME)) {
            statement.setString(1, name);
            return readRecord(statement);
        } catch (SQLException e) {
            throw new StorageException("Unable to read statistics", e);
        }
    }

    @Override
    public synchronized void set(UUID id, String name, StatField field, long storedValue) throws StorageException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(field, "field");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (storedValue < 0) {
            throw new IllegalArgumentException("storedValue must not be negative");
        }
        try (PreparedStatement statement = connection.prepareStatement(Sql.set(field))) {
            statement.setString(1, id.toString());
            statement.setString(2, name);
            statement.setLong(3, clock.getAsLong());
            statement.setLong(4, storedValue);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to update statistics", e);
        }
    }

    @Override
    public synchronized void reset(UUID id) throws StorageException {
        Objects.requireNonNull(id, "id");
        try (PreparedStatement statement = connection.prepareStatement(Sql.RESET)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Unable to reset statistics", e);
        }
    }

    @Override
    public synchronized List<LeaderboardEntry> top(StatField field, int limit, int offset) throws StorageException {
        Objects.requireNonNull(field, "field");
        if (limit < 1 || offset < 0) {
            throw new IllegalArgumentException("limit must be positive and offset must not be negative");
        }
        try (PreparedStatement statement = connection.prepareStatement(Sql.top(field))) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);
            try (ResultSet result = statement.executeQuery()) {
                List<LeaderboardEntry> entries = new ArrayList<>();
                while (result.next()) {
                    entries.add(new LeaderboardEntry(
                            UUID.fromString(result.getString(ID_COLUMN)), result.getString(NAME_COLUMN), result.getLong(FIRST_STAT_COLUMN)));
                }
                return entries;
            }
        } catch (SQLException | IllegalArgumentException e) {
            throw new StorageException("Unable to read the leaderboard", e);
        }
    }

    @Override
    public synchronized long countRanked(StatField field) throws StorageException {
        Objects.requireNonNull(field, "field");
        try (PreparedStatement statement = connection.prepareStatement(Sql.countRanked(field));
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new StorageException("Unable to count leaderboard entries", e);
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly(connection);
    }

    private Optional<PlayerRecord> readRecord(PreparedStatement statement) throws SQLException, StorageException {
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }
            long[] values = new long[StatField.SIZE];
            for (StatField field : StatField.values()) {
                values[field.ordinal()] = result.getLong(FIRST_STAT_COLUMN + field.ordinal());
            }
            return Optional.of(new PlayerRecord(
                    UUID.fromString(result.getString(ID_COLUMN)), result.getString(NAME_COLUMN), StatValues.fromArray(values)));
        } catch (IllegalArgumentException e) {
            throw new StorageException("Corrupt player row", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            return;
        }
    }
}
