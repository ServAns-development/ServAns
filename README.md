# ServAns

Self-hosted Minecraft server analytics with a central web dashboard. The dashboard runs on your own
machine or VPS; the plugin connects **out** to it with a server key. Nothing is hosted inside the
Minecraft server and no inbound website port is opened.

Current release: **Milestone 1** (collection core + dashboard push)

## How it works

1. Run the ServAns website (`ServAns-website`).
2. Sign in and create a server on the site.
3. Copy the generated key into `plugins/ServAns/config.yml`.
4. Restart the Minecraft server.
5. Samples arrive at the dashboard within about a minute.

Collection, retention, maintenance, privacy, logging and theme are configured per server on the
dashboard and fetched by the plugin at startup and every five minutes. The local `config.yml`
only needs the dashboard connection and local storage settings.

The plugin still stores samples locally, so a dashboard outage never affects gameplay. Uploads run on
a separate background thread with a bounded queue and retry once.

## Requirements

- Java 21
- Paper 1.21.x (Purpur and Folia are supported)
- SQLite by default; MySQL 8 or MariaDB 10.6+ optional for local storage
- An HTTPS URL for the ServAns website
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
2. Start the server once. ServAns generates `plugins/ServAns/config.yml`.
3. Create a server on the website and paste the key into `cloud.key`.
4. Restart the server.

The local database is created at `plugins/ServAns/servans.db` by default and is not required for the
dashboard to work.

## Configuration

```yaml
server:
  id: ''
  name: 'Minecraft Server'
cloud:
  enabled: true
  url: 'https://servans.matumaka.com'
  key: ''
  timeout-seconds: 10
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
```

ServAns merges new keys into an existing `config.yml` on startup, so upgrades do not require deleting
the file. Older collection, retention, maintenance, privacy, logging and theme sections are removed
automatically because those settings now live on the dashboard.

- `server.id` is the local id used for the plugin's own database. It is generated once and can be
  changed to a URL-safe slug using lowercase letters, digits, `_` and `-` (2 to 64 characters).
  The dashboard identifies the server by its key, not this id.
- `cloud.enabled` turns dashboard uploads and settings sync on or off.
- `cloud.url` is the base URL of the ServAns website, for example `https://servans.matumaka.com`.
- `cloud.key` is the key shown once when you create or rotate a server on the website.
- `cloud.timeout-seconds` is the HTTP timeout, clamped to 1 to 60 seconds.
- `database.pool-size` applies to SQLite and MySQL. The legacy `database.mysql.pool-size` key is
  migrated automatically.

### Dashboard settings

Open the server on the dashboard and click **Settings**. The plugin fetches the result from
`GET <cloud.url>/api/plugin/settings` with the same bearer key and applies it at runtime:

- `collection.interval-seconds` (10–3600) controls how often samples are collected.
- `collection.world-stats` disables per-world chunk and entity sampling. It is automatically skipped on Folia.
- `collection.online-players` controls whether the detailed online player list is sent.
- `collection.plugins` and `collection.plugin-scan-minutes` control the plugin inventory refresh.
- `retention.raw-days`, `retention.minute-days` and `retention.hour-days` control local history cleanup.
- `maintenance.interval-seconds` (30–3600) controls rollup and retention frequency.
- `privacy.store-ips` is reserved for the web/auth milestone. No player IP addresses are stored.
- `logging.debug` enables verbose ServAns diagnostics, including successful dashboard uploads.
- `theme.title`, `theme.accent-color` and `theme.logo-url` are delivered to the plugin for branding.

Changes take effect on the next five-minute settings refresh. Invalid values are clamped to safe limits.

If `cloud.key` is empty, the plugin logs a warning with the dashboard URL and keeps collecting
locally. If the dashboard rejects the key, you will see a clear warning in the console; create a new
server or rotate the key on the site.

### Upload payload

Every sample is sent as JSON to `POST <cloud.url>/api/ingest` with:

```http
Authorization: Bearer <cloud.key>
Content-Type: application/json
```

The payload includes:

- timestamp, online players, joins and leaves
- TPS 1m/5m/15m, MSPT, memory used/max, CPU and uptime
- server name, software, Minecraft version and max players
- per-world chunks, entities and players
- plugin list when the scan runs
- online player names, worlds and ping

### MySQL

Set `database.type: mysql` and fill in the `mysql` block. Schema migrations run automatically on
startup. Use a dedicated database and user.

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

All database access happens on a dedicated single-thread executor with batched transactions. The tick
thread only performs cheap state reads. Dashboard uploads use a separate executor.

Sessions are tracked from join/quit/world-change events. Kills and deaths are recorded from death
events. Playtime, worlds visited, session duration, join count and last seen are persisted locally per
player and per server.

No chat, commands or IP addresses are stored.

## Storage and retention

| Data | Default retention |
| --- | --- |
| Raw samples and world samples | 7 days |
| Minute rollups | 30 days |
| Hourly rollups | 365 days |

A background maintenance job runs every 60 seconds. It rolls completed minutes into `samples_minute`,
completed hours into `samples_hour`, and applies retention cleanup. Rollup state is checkpointed in
`job_state`, so restarts do not reprocess old windows.

### Tables

- `servers`, `plugins`, `online_players`
- `samples`, `world_samples`
- `samples_minute`, `samples_hour`
- `players`, `player_sessions`, `player_stats`, `player_worlds`
- `schema_version`, `job_state`

## Folia notes

Collection is scheduled through the Folia global region scheduler when Folia is detected, and through
the Bukkit scheduler on Paper/Purpur. Async database and dashboard work always uses dedicated
executors. Per-world chunk and entity scans are skipped on Folia because those world-wide reads are not
region-safe.

## Privacy

- No player IPs are stored.
- A player data deletion API exists in `DataStore#deletePlayer(UUID)` and will be exposed through an
  admin command in milestone 2.
- World names, player names, UUIDs, sessions and aggregate server metrics are stored.

## Roadmap

1. Common module, database layer, migrations, Paper metrics collector ✅
2. Central dashboard, API keys, server limits ✅
3. In-game commands and auth
4. Player pages, query, settings, badges
5. Velocity and Bungee modules, network linking
6. Documentation, reverse proxy guides, release packaging
