package xyz.matumaka.servans.common.store;

import xyz.matumaka.servans.common.db.Database;
import xyz.matumaka.servans.common.db.Dialect;
import xyz.matumaka.servans.common.db.Upsert;
import xyz.matumaka.servans.common.model.OnlinePlayer;
import xyz.matumaka.servans.common.model.PluginEntry;
import xyz.matumaka.servans.common.model.SampleBundle;
import xyz.matumaka.servans.common.model.ServerDescriptor;
import xyz.matumaka.servans.common.model.WorldSample;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class DataStore {
    private static final List<String> SERVER_COLUMNS = List.of("id", "name", "software", "mc_version", "max_players", "first_seen", "last_seen");
    private static final List<String> SERVER_UPDATES = List.of("name", "software", "mc_version", "max_players", "last_seen");

    private final Database database;
    private final Dialect dialect;

    public DataStore(Database database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    public Database database() {
        return database;
    }

    public Dialect dialect() {
        return dialect;
    }

    public void ensureServer(ServerDescriptor server, long now) {
        database.write(connection -> upsertServer(connection, server, now));
    }

    public void recordSample(SampleBundle bundle) {
        int chunks = bundle.worlds().stream().mapToInt(WorldSample::chunks).sum();
        int entities = bundle.worlds().stream().mapToInt(WorldSample::entities).sum();
        database.write(connection -> {
            upsertServer(connection, bundle.server(), bundle.ts());
            insertSample(connection, bundle, chunks, entities);
            replaceWorlds(connection, bundle);
            if (bundle.plugins() != null) {
                replacePlugins(connection, bundle);
            }
            replaceOnline(connection, bundle);
        });
    }

    public void openSession(String sessionId, String serverId, UUID uuid, String name, String world, long joinedAt) {
        database.write(connection -> {
            touchPlayer(connection, uuid, name, joinedAt);
            stats(connection, uuid, serverId, name, 0L, 0, 0, 1, joinedAt);
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_sessions (session_id, server_id, uuid, name, world, joined_at, left_at, duration, kills, deaths) VALUES (?, ?, ?, ?, ?, ?, NULL, 0, 0, 0)")) {
                statement.setString(1, sessionId);
                statement.setString(2, serverId);
                statement.setString(3, uuid.toString());
                statement.setString(4, name);
                statement.setString(5, world);
                statement.setLong(6, joinedAt);
                statement.executeUpdate();
            }
        });
    }

    public void closeSession(String sessionId, String serverId, UUID uuid, String name, String world, long leftAt, long playSeconds, long worldSeconds) {
        long playtime = Math.max(0L, playSeconds);
        long worldTime = Math.max(0L, worldSeconds);
        database.write(connection -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE player_sessions SET left_at = ?, duration = ? WHERE session_id = ? AND left_at IS NULL")) {
                statement.setLong(1, leftAt);
                statement.setLong(2, playtime);
                statement.setString(3, sessionId);
                updated = statement.executeUpdate();
            }
            if (updated == 0) {
                return;
            }
            touchPlayer(connection, uuid, name, leftAt);
            stats(connection, uuid, serverId, name, playtime, 0, 0, 0, leftAt);
            if (worldTime > 0) {
                worldTime(connection, serverId, uuid, world, worldTime, leftAt);
            }
        });
    }

    public void addWorldTime(String serverId, UUID uuid, String world, long seconds, long lastSeen) {
        long worldTime = Math.max(0L, seconds);
        if (worldTime == 0) {
            return;
        }
        database.write(connection -> worldTime(connection, serverId, uuid, world, worldTime, lastSeen));
    }

    public void recordKill(String serverId, UUID uuid, String name, String sessionId, long timestamp) {
        database.write(connection -> {
            incrementSession(connection, sessionId, "kills");
            touchPlayer(connection, uuid, name, timestamp);
            stats(connection, uuid, serverId, name, 0L, 1, 0, 0, timestamp);
        });
    }

    public void recordDeath(String serverId, UUID uuid, String name, String sessionId, long timestamp) {
        database.write(connection -> {
            incrementSession(connection, sessionId, "deaths");
            touchPlayer(connection, uuid, name, timestamp);
            stats(connection, uuid, serverId, name, 0L, 0, 1, 0, timestamp);
        });
    }

    public void clearOnline(String serverId) {
        database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM online_players WHERE server_id = ?")) {
                statement.setString(1, serverId);
                statement.executeUpdate();
            }
        });
    }

    public void closeStaleSessions(String serverId, long now) {
        database.write(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE player_sessions SET left_at = ?, duration = CASE WHEN ? > joined_at THEN ? - joined_at ELSE 0 END WHERE server_id = ? AND left_at IS NULL")) {
                statement.setLong(1, now);
                statement.setLong(2, now);
                statement.setLong(3, now);
                statement.setString(4, serverId);
                statement.executeUpdate();
            }
        });
    }

    public void deletePlayer(UUID uuid) {
        String id = uuid.toString();
        database.write(connection -> {
            for (String table : List.of("player_sessions", "player_stats", "player_worlds", "online_players", "players")) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                    statement.setString(1, id);
                    statement.executeUpdate();
                }
            }
        });
    }

    private void upsertServer(Connection connection, ServerDescriptor server, long now) throws SQLException {
        Upsert.run(connection, dialect, "servers", List.of("id"), SERVER_COLUMNS, SERVER_UPDATES,
            new Object[]{server.id(), server.name(), server.software(), server.version(), server.maxPlayers(), now, now});
    }

    private void insertSample(Connection connection, SampleBundle bundle, int chunks, int entities) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO samples (server_id, ts, players, max_players, tps1, tps5, tps15, mspt, ram_used, ram_max, cpu, uptime, chunks, entities, joins, leaves) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, bundle.server().id());
            statement.setLong(2, bundle.ts());
            statement.setInt(3, bundle.players());
            statement.setInt(4, bundle.server().maxPlayers());
            statement.setDouble(5, bundle.tps1());
            statement.setDouble(6, bundle.tps5());
            statement.setDouble(7, bundle.tps15());
            statement.setDouble(8, bundle.mspt());
            statement.setLong(9, bundle.ramUsed());
            statement.setLong(10, bundle.ramMax());
            statement.setDouble(11, bundle.cpu());
            statement.setLong(12, bundle.uptime());
            statement.setInt(13, chunks);
            statement.setInt(14, entities);
            statement.setInt(15, bundle.joins());
            statement.setInt(16, bundle.leaves());
            statement.executeUpdate();
        }
    }

    private void replaceWorlds(Connection connection, SampleBundle bundle) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM world_samples WHERE server_id = ? AND ts = ?")) {
            delete.setString(1, bundle.server().id());
            delete.setLong(2, bundle.ts());
            delete.executeUpdate();
        }
        if (bundle.worlds().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO world_samples (server_id, ts, world, chunks, entities, players) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (WorldSample world : bundle.worlds()) {
                insert.setString(1, bundle.server().id());
                insert.setLong(2, bundle.ts());
                insert.setString(3, world.world());
                insert.setInt(4, world.chunks());
                insert.setInt(5, world.entities());
                insert.setInt(6, world.players());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replacePlugins(Connection connection, SampleBundle bundle) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM plugins WHERE server_id = ?")) {
            delete.setString(1, bundle.server().id());
            delete.executeUpdate();
        }
        if (bundle.plugins().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO plugins (server_id, name, version, updated_at) VALUES (?, ?, ?, ?)")) {
            for (PluginEntry plugin : bundle.plugins()) {
                insert.setString(1, bundle.server().id());
                insert.setString(2, plugin.name());
                insert.setString(3, plugin.version());
                insert.setLong(4, bundle.ts());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceOnline(Connection connection, SampleBundle bundle) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM online_players WHERE server_id = ?")) {
            delete.setString(1, bundle.server().id());
            delete.executeUpdate();
        }
        if (bundle.online().isEmpty()) {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO online_players (server_id, uuid, name, world, ping, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (OnlinePlayer player : bundle.online()) {
                insert.setString(1, bundle.server().id());
                insert.setString(2, player.uuid());
                insert.setString(3, player.name());
                insert.setString(4, player.world());
                insert.setInt(5, player.ping());
                insert.setLong(6, bundle.ts());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void touchPlayer(Connection connection, UUID uuid, String name, long timestamp) throws SQLException {
        Upsert.run(connection, dialect, "players", List.of("uuid"), List.of("uuid", "name", "first_seen", "last_seen"), List.of("name", "last_seen"),
            new Object[]{uuid.toString(), name, timestamp, timestamp});
    }

    private void stats(Connection connection, UUID uuid, String serverId, String name, long playtime, int kills, int deaths, int joins, long timestamp) throws SQLException {
        String sql = dialect == Dialect.SQLITE
            ? "INSERT INTO player_stats (uuid, server_id, name, playtime, kills, deaths, joins_count, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
              "ON CONFLICT (uuid, server_id) DO UPDATE SET name = excluded.name, " +
              "playtime = player_stats.playtime + excluded.playtime, kills = player_stats.kills + excluded.kills, " +
              "deaths = player_stats.deaths + excluded.deaths, joins_count = player_stats.joins_count + excluded.joins_count, last_seen = excluded.last_seen"
            : "INSERT INTO player_stats (uuid, server_id, name, playtime, kills, deaths, joins_count, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
              "ON DUPLICATE KEY UPDATE name = VALUES(name), playtime = player_stats.playtime + VALUES(playtime), " +
              "kills = player_stats.kills + VALUES(kills), deaths = player_stats.deaths + VALUES(deaths), " +
              "joins_count = player_stats.joins_count + VALUES(joins_count), last_seen = VALUES(last_seen)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, serverId);
            statement.setString(3, name);
            statement.setLong(4, playtime);
            statement.setInt(5, kills);
            statement.setInt(6, deaths);
            statement.setInt(7, joins);
            statement.setLong(8, timestamp);
            statement.executeUpdate();
        }
    }

    private void worldTime(Connection connection, String serverId, UUID uuid, String world, long seconds, long lastSeen) throws SQLException {
        String sql = dialect == Dialect.SQLITE
            ? "INSERT INTO player_worlds (uuid, server_id, world, seconds, last_seen) VALUES (?, ?, ?, ?, ?) " +
              "ON CONFLICT (uuid, server_id, world) DO UPDATE SET seconds = player_worlds.seconds + excluded.seconds, last_seen = excluded.last_seen"
            : "INSERT INTO player_worlds (uuid, server_id, world, seconds, last_seen) VALUES (?, ?, ?, ?, ?) " +
              "ON DUPLICATE KEY UPDATE seconds = player_worlds.seconds + VALUES(seconds), last_seen = VALUES(last_seen)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, serverId);
            statement.setString(3, world);
            statement.setLong(4, seconds);
            statement.setLong(5, lastSeen);
            statement.executeUpdate();
        }
    }

    private void incrementSession(Connection connection, String sessionId, String column) throws SQLException {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE player_sessions SET " + column + " = " + column + " + 1 WHERE session_id = ?")) {
            statement.setString(1, sessionId);
            statement.executeUpdate();
        }
    }
}
