# ServAns

Self-hosted Minecraft server analytics with a bundled web dashboard. ServAns runs entirely inside your Minecraft server or proxy: no external backend, no cloud account.

Current release: **Milestone 1** (collection core)

## Milestone 1 scope

- `common` module: configuration model, SQLite/MySQL schema migrations, HikariCP database layer, all persistence repositories, retention, rollup and cleanup jobs
- `paper` module: Paper/Purpur/Folia plugin bootstrap, server ID generation, async metric collection, session tracking, join/leave, kills/deaths, world statistics, plugin inventory, online players, graceful shutdown
- Validated, auto-upgrading YAML config with web bind/port, theme, collection toggles, maintenance cadence and debug logging
- Automated SQLite integration tests, configuration tests and a small JSON encoder used by later API milestones

Web dashboard, auth, commands, badges and proxy modules arrive in milestones 2 to 6.

## Requirements

- Java 21
- Paper 1.21.x (Purpur and Folia are supported)
- SQLite by default; MySQL 8 or MariaDB 10.6+ optional
- Gradle wrapper included; no local Gradle installation needed

## Build

```bash
./gradlew build
```

The distributable jar is written to `paper/build/libs/ServAns-1.0.1.jar`.

Run only the tests:

```bash
./gradlew :common:test
```

## Install

1. Copy `ServAns-1.0.1.jar` into `plugins/`.
2. Start the server once. ServAns generates an 8-character server ID and writes it to `plugins/ServAns/config.yml`.
3. Restart or reload the server after changing the database settings.

The database is created at `plugins/ServAns/servans.db` by default.

## Configuration

```yaml
server:
  id: ''
  name: 'Minecraft Server'
web:
  enabled: true
  bind-address: 0.0.0.0
  port: 8804
  public-base-url: ''
  trust-proxy-headers: false
  trusted-proxies: []
  cors-origins: []
  rate-limit:
    requests: 120
    window-seconds: 60
  https:
    enabled: false
    keystore: keystore.p12
    keystore-password: ''
theme:
  title: ServAns
  accent-color: '#ededed'
  logo-url: ''
database:
  type: sqlite
  pool-size: 6
  file: servans.db
  mysql:
    host: 127.0.0.1
    port: 3306
    database: servans
    username: servans
    password: ''
    use-ssl: false
retention:
  raw-days: 7
  minute-days: 30
  hour-days: 365
collection:
  interval-seconds: 60
  world-stats: true
  online-players: true
  plugins: true
  plugin-scan-minutes: 30
maintenance:
  interval-seconds: 60
privacy:
  store-ips: false
logging:
  debug: false
```

ServAns merges new keys into an existing `config.yml` on startup, so upgrades do not require deleting the file.

- `server.id` is generated once. It can be changed to a URL-safe slug using lowercase letters, digits, `_` and `-` (2 to 64 characters).
- `web.bind-address` and `web.port` select where the embedded dashboard listens. The default is `0.0.0.0:8804`. The server implementation is delivered in milestone 2; the values are already parsed and validated.
- `web.public-base-url` is the externally visible dashboard URL, for example `https://servans.matumaka.com`. It is used later for links, OG metadata and badge URLs.
- `web.trust-proxy-headers`, `web.trusted-proxies`, `web.cors-origins`, rate limits and the HTTPS keystore are ready for the web server milestone.
- `theme.*` controls the dashboard title, accent color and optional logo URL.
- `database.pool-size` applies to SQLite and MySQL. The legacy `database.mysql.pool-size` key is migrated automatically.
- `collection.interval-seconds` is clamped to 10 to 3600 seconds.
- `collection.world-stats` disables per-world chunk and entity sampling when set to `false`. It is automatically skipped on Folia.
- `collection.online-players` controls whether the detailed online player list is stored.
- `collection.plugins` and `collection.plugin-scan-minutes` control the plugin inventory refresh.
- `maintenance.interval-seconds` controls rollup and retention frequency, clamped to 30 to 3600 seconds.
- `logging.debug` enables verbose ServAns diagnostics.
- `privacy.store-ips` is reserved for the web/auth milestone. Milestone 1 stores no player IP addresses under any setting.

### MySQL

Set `database.type: mysql` and fill in the `mysql` block. Schema migrations run automatically on startup. Use a dedicated database and user.

## What is collected

Every sample interval, on the server thread:

- Online players with world and ping
- TPS (1m, 5m, 15m) and MSPT
- Used and maximum JVM memory, process CPU load, uptime
- Loaded chunks, entities and players per world (Paper/Purpur only)
- Join and leave counts for the interval
- Server software, Minecraft version and maximum players

Every 30 minutes:

- Installed plugin list

All database access happens on a dedicated single-thread executor with batched transactions. The tick thread only performs cheap state reads.

Sessions are tracked from join/quit/world-change events. Kills and deaths are recorded from death events. Playtime, worlds visited, session duration, join count and last seen are persisted per player and per server.

No chat, commands or IP addresses are stored.

## Storage and retention

| Data | Default retention |
| --- | --- |
| Raw samples and world samples | 7 days |
| Minute rollups | 30 days |
| Hourly rollups | 365 days |

A background maintenance job runs every 60 seconds. It rolls completed minutes into `samples_minute`, completed hours into `samples_hour`, and applies retention cleanup. Rollup state is checkpointed in `job_state`, so restarts do not reprocess old windows.

### Tables

- `servers`, `plugins`, `online_players`
- `samples`, `world_samples`
- `samples_minute`, `samples_hour`
- `players`, `player_sessions`, `player_stats`, `player_worlds`
- `schema_version`, `job_state`

## Folia notes

Collection is scheduled through the Folia global region scheduler when Folia is detected, and through the Bukkit scheduler on Paper/Purpur. Async database work always uses a dedicated executor. Per-world chunk and entity scans are skipped on Folia because those world-wide reads are not region-safe.

## Privacy

- No player IPs are stored in this milestone.
- A player data deletion API exists in `DataStore#deletePlayer(UUID)` and will be exposed through an admin command in milestone 2.
- World names, player names, UUIDs, sessions and aggregate server metrics are stored.

## Roadmap

1. Common module, database layer, migrations, Paper metrics collector
2. Embedded web server, REST API, in-game auth, commands
3. Frontend `/servers` and `/server/<id>` with charts
4. Player pages, query, settings, badges
5. Velocity and Bungee modules, network linking
6. Documentation, reverse proxy guides, release packaging
