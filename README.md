# minecraft-player-stats

## Description

A Paper plugin that tracks per-player statistics and stores them in SQLite
without doing database work on the main server thread. It provides a player
command, an admin command and paginated leaderboards. All messages are
MiniMessage templates in `config.yml`.

> **Verification status:** the plugin compiles against Paper API 1.20.4 and the
> core logic (accumulation and flushing, movement filtering, the SQLite
> repository, formatting, command parsing and config validation) is covered by
> unit tests. It has **not** been run on a real Paper server. The Bukkit layer
> (listeners, commands, scheduler wiring) needs manual testing on a server
> before production use.

## Features

- Tracked statistics: playtime, joins, deaths, player kills, mob kills, blocks broken, blocks placed, distance walked, distance flown and distance swum
- SQLite storage with WAL journaling, prepared statements and versioned schema migrations
- In-memory accumulation: events only touch memory; the database is written by a periodic asynchronous flush, on player quit and synchronously on shutdown
- Single writer thread with a bounded queue; a full queue rejects work instead of blocking the server
- Movement filtering: tiny steps, teleports and large jumps are not counted, world changes re-anchor the position, and vehicle travel is ignored
- Events that arrive while a player's data is still loading are kept and merged, not dropped
- `/stats` and `/adminstats` with tab completion and paginated leaderboards
- Failed flushes are retried on the next flush without losing or double counting data

## Installation

Requirements: Paper 1.20.4 or newer (API version 1.20) and Java 17 or newer.

1. Build the plugin (see Development) or take `target/minecraft-player-stats-0.1.0.jar`.
2. Put the jar in the server `plugins/` folder.
3. Start the server with network access to Maven Central once. Paper downloads the SQLite JDBC driver declared under `libraries` in `plugin.yml`.
4. Edit `plugins/PlayerStats/config.yml` if needed and restart.

## Usage

| Command | Permission | Description |
| --- | --- | --- |
| `/stats` | `playerstats.view` | Show your own statistics |
| `/stats <player>` | `playerstats.view.others` | Show another player's statistics (online or previously seen) |
| `/stats top <field> [page]` | `playerstats.view` | Show a leaderboard |
| `/adminstats <player> reset` | `playerstats.admin` | Reset all statistics of a player |
| `/adminstats <player> set <field> <value>` | `playerstats.admin` | Set one field; distances are in meters, playtime in seconds |
| `/adminstats top <field> [page]` | `playerstats.admin` | Show a leaderboard |

Fields: `playtime`, `joins`, `deaths`, `player-kills`, `mob-kills`, `blocks-broken`, `blocks-placed`, `distance-walked`, `distance-flown`, `distance-swum`.

Defaults: `playerstats.view` and `playerstats.view.others` are granted to everyone, `playerstats.admin` to operators.

## Configuration

`config.yml` is validated at startup. Invalid values are reported by key and the plugin disables itself instead of running with a guess.

| Key | Default | Range | Meaning |
| --- | --- | --- | --- |
| `storage.file` | `stats.db` | plain file name | Database file inside the plugin folder |
| `storage.flush-interval-seconds` | `60` | 5 to 3600 | Interval of the asynchronous flush |
| `storage.queue-capacity` | `1024` | 64 to 65536 | Bounded storage queue size |
| `storage.shutdown-timeout-seconds` | `15` | 1 to 120 | Time allowed for the queue to drain on shutdown |
| `movement.min-step` | `0.1` | 0 to 5 | Steps shorter than this many blocks are ignored |
| `movement.max-step` | `8.0` | above min-step, up to 64 | Steps longer than this are treated as teleports |
| `leaderboard.page-size` | `10` | 1 to 50 | Entries per leaderboard page |

`labels.*` set the display name of each field and `messages.*` hold every
user-visible message as MiniMessage. Placeholders such as `<player>`, `<value>`,
`<label>`, `<page>` and `<pages>` are inserted as plain text, so player names
cannot inject formatting.

## Examples

```
/stats
/stats Notch
/stats top playtime
/stats top distance-walked 2
/adminstats Notch set deaths 0
/adminstats Notch set distance-walked 1500
/adminstats Notch reset
```

Custom message:

```yaml
messages:
  stats-line: "<gray><label>: <aqua><value>"
```

## Architecture

```
net.sadness.playerstats
  stats      field list, immutable value vectors, records, pagination
  movement   distance filtering and per-mode accumulation
  format     duration and distance formatting
  command    argument parsing into request objects
  config     validated settings
  storage    StatsRepository interface, bounded single-thread executor
  storage.sqlite   SQLite implementation, migrations, SQL text
  service    per-player sessions, flush and retry logic
  bukkit     the only package that depends on Paper: plugin, listeners, commands, messages
```

Everything outside `bukkit` has no dependency on Paper and is unit tested. The
SQLite driver is not shaded. It is declared in `plugin.yml` under `libraries`,
so Paper downloads it and loads it in the plugin's own class loader. The
repository opens connections through `SQLiteConfig.createConnection` rather than
`DriverManager`, which avoids driver registration problems with plugin class
loaders. This keeps the jar small and avoids relocation of native libraries;
the cost is that the first start needs access to Maven Central.

Storage layout: one `players` table keyed by UUID. Distances are stored in
centimeters and playtime in seconds. The schema version lives in
`PRAGMA user_version`; migrations run in transactions and a database written by
a newer version is refused.

## Security

- All SQL uses prepared statements. Column names come from a fixed enum, never from user input.
- Player names, field names, values and pages are validated before use; values are bounded.
- The database file name is restricted to a plain file name, so the config cannot point outside the plugin folder.
- Player-controlled text is inserted into messages as unparsed text, not as MiniMessage.
- Permissions are checked before any work is done; `playerstats.admin` is operator-only by default.

## Performance

- Event handlers only update in-memory counters. Movement events do one distance computation and no I/O.
- The database is accessed by one daemon thread through a bounded queue. The main thread never waits on it.
- Writes are batched into one transaction per flush and use upserts.
- Command results are produced asynchronously and delivered back on the main thread.
- On shutdown the plugin drains the queue within the configured timeout and then writes any remaining data synchronously. If the queue is full, flushes are retried later.

## Development

```
mvn -B verify
```

Requires JDK 17 and Maven 3.9. The Paper API is resolved from the PaperMC
Maven repository.

## Testing

`mvn -B verify` runs JUnit 5 tests covering accumulation and flush behavior
(including failures, retries, a full queue, shutdown and concurrent recording),
movement filtering, the SQLite repository against temporary database files
(insert, upsert, set, reset, top, pagination, migrations, corrupt rows, newer
schema, garbage files, concurrent increments), duration and distance formatting,
command argument parsing and config validation. There are no tests that start a
Paper server.

## License

MIT, see [LICENSE](LICENSE).
