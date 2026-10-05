package net.sadness.playerstats.storage.sqlite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import net.sadness.playerstats.storage.StorageException;

final class Migrations {

    private static final String READ_VERSION = "PRAGMA user_version";

    private static final List<Migration> ALL = List.of(
            new Migration(1, List.of(
                    "CREATE TABLE players ("
                            + "uuid TEXT NOT NULL PRIMARY KEY, "
                            + "name TEXT NOT NULL, "
                            + "playtime_seconds INTEGER NOT NULL DEFAULT 0, "
                            + "joins INTEGER NOT NULL DEFAULT 0, "
                            + "deaths INTEGER NOT NULL DEFAULT 0, "
                            + "player_kills INTEGER NOT NULL DEFAULT 0, "
                            + "mob_kills INTEGER NOT NULL DEFAULT 0, "
                            + "blocks_broken INTEGER NOT NULL DEFAULT 0, "
                            + "blocks_placed INTEGER NOT NULL DEFAULT 0, "
                            + "distance_walked_cm INTEGER NOT NULL DEFAULT 0, "
                            + "distance_flown_cm INTEGER NOT NULL DEFAULT 0, "
                            + "distance_swum_cm INTEGER NOT NULL DEFAULT 0)")),
            new Migration(2, List.of(
                    "ALTER TABLE players ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
                    "CREATE INDEX idx_players_name ON players (name COLLATE NOCASE)")));

    static final int LATEST_VERSION = ALL.get(ALL.size() - 1).version();

    private Migrations() {
    }

    static void apply(Connection connection) throws StorageException {
        apply(connection, LATEST_VERSION);
    }

    static void apply(Connection connection, int targetVersion) throws StorageException {
        try {
            int current = readVersion(connection);
            if (current > LATEST_VERSION) {
                throw new StorageException("Database schema version " + current + " is newer than the supported version "
                        + LATEST_VERSION + "; refusing to open it");
            }
            for (Migration migration : ALL) {
                if (migration.version() > current && migration.version() <= targetVersion) {
                    Transaction.run(connection, () -> execute(connection, migration));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Database migration failed", e);
        }
    }

    static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(READ_VERSION)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void execute(Connection connection, Migration migration) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : migration.statements()) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = " + migration.version());
        }
    }

    private record Migration(int version, List<String> statements) {
    }
}
