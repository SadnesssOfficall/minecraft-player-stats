# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-10-05

### Added

- Tracking of playtime, joins, deaths, player kills, mob kills, blocks broken, blocks placed and distance walked, flown and swum
- SQLite storage with WAL, versioned migrations (`PRAGMA user_version`) and prepared statements
- Single-writer bounded storage queue with periodic asynchronous flush, flush on quit and a synchronous final flush on shutdown
- Movement filtering that ignores tiny steps, teleports and large jumps and re-anchors on world changes
- `/stats [player]`, `/stats top <field> [page]` and `/adminstats <player> reset|set <field> <value>|top <field> [page]`
- MiniMessage messages and field labels configurable in `config.yml`
- Configuration validation with a clear error report on startup
