package net.sadness.playerstats.storage.sqlite;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sadness.playerstats.stats.StatField;

final class Sql {

    static final String TABLE = "players";

    private static final String STAT_COLUMNS = Stream.of(StatField.values())
            .map(StatField::column)
            .collect(Collectors.joining(", "));

    static final String UPSERT_DELTA = "INSERT INTO " + TABLE + " (uuid, name, updated_at, " + STAT_COLUMNS + ") VALUES (?, ?, ?, "
            + placeholders(StatField.SIZE) + ") ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, updated_at = excluded.updated_at, "
            + Stream.of(StatField.values())
                    .map(field -> field.column() + " = " + TABLE + "." + field.column() + " + excluded." + field.column())
                    .collect(Collectors.joining(", "));

    static final String RESET = "UPDATE " + TABLE + " SET "
            + Stream.of(StatField.values()).map(field -> field.column() + " = 0").collect(Collectors.joining(", "))
            + " WHERE uuid = ?";

    static final String SELECT_BY_ID = "SELECT uuid, name, " + STAT_COLUMNS + " FROM " + TABLE + " WHERE uuid = ?";

    static final String SELECT_BY_NAME = "SELECT uuid, name, " + STAT_COLUMNS + " FROM " + TABLE
            + " WHERE name = ? COLLATE NOCASE ORDER BY updated_at DESC, uuid ASC LIMIT 1";

    private static final Map<StatField, String> SET_STATEMENTS = buildSetStatements();
    private static final Map<StatField, String> TOP_STATEMENTS = buildTopStatements();
    private static final Map<StatField, String> COUNT_STATEMENTS = buildCountStatements();

    private Sql() {
    }

    static String set(StatField field) {
        return SET_STATEMENTS.get(field);
    }

    static String top(StatField field) {
        return TOP_STATEMENTS.get(field);
    }

    static String countRanked(StatField field) {
        return COUNT_STATEMENTS.get(field);
    }

    private static String placeholders(int count) {
        return Stream.generate(() -> "?").limit(count).collect(Collectors.joining(", "));
    }

    private static Map<StatField, String> buildSetStatements() {
        Map<StatField, String> statements = new EnumMap<>(StatField.class);
        for (StatField field : StatField.values()) {
            statements.put(field, "INSERT INTO " + TABLE + " (uuid, name, updated_at, " + field.column()
                    + ") VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET " + field.column() + " = excluded." + field.column());
        }
        return statements;
    }

    private static Map<StatField, String> buildTopStatements() {
        Map<StatField, String> statements = new EnumMap<>(StatField.class);
        for (StatField field : StatField.values()) {
            statements.put(field, "SELECT uuid, name, " + field.column() + " FROM " + TABLE + " WHERE " + field.column()
                    + " > 0 ORDER BY " + field.column() + " DESC, name COLLATE NOCASE ASC, uuid ASC LIMIT ? OFFSET ?");
        }
        return statements;
    }

    private static Map<StatField, String> buildCountStatements() {
        Map<StatField, String> statements = new EnumMap<>(StatField.class);
        for (StatField field : StatField.values()) {
            statements.put(field, "SELECT COUNT(*) FROM " + TABLE + " WHERE " + field.column() + " > 0");
        }
        return statements;
    }
}
