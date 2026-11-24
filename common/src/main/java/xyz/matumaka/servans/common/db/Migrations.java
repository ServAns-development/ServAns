package xyz.matumaka.servans.common.db;

import xyz.matumaka.servans.common.ServAnsLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Migrations {
    private static final String SUFFIX_MYSQL = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    private Migrations() {
    }

    public static void migrate(Database database, ServAnsLogger logger) {
        database.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL PRIMARY KEY, applied_at BIGINT NOT NULL)");
        int current = database.read(connection -> {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                result.next();
                return result.getInt(1);
            }
        });
        List<List<String>> migrations = all(database.dialect());
        for (int version = current + 1; version <= migrations.size(); version++) {
            final int target = version;
            List<String> statements = migrations.get(version - 1);
            database.write(connection -> {
                try (Statement statement = connection.createStatement()) {
                    for (String sql : statements) {
                        statement.execute(sql);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                    statement.setInt(1, target);
                    statement.setLong(2, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            });
            logger.info("Applied database migration " + version);
        }
    }

    private static List<List<String>> all(Dialect dialect) {
        List<List<String>> migrations = new ArrayList<>();
        migrations.add(version1(dialect));
        return migrations;
    }

    private static List<String> version1(Dialect dialect) {
        List<String> sql = new ArrayList<>();
        String suffix = dialect.mysql() ? SUFFIX_MYSQL : "";
        String autoId = dialect.mysql() ? "id BIGINT NOT NULL AUTO_INCREMENT" : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        String autoKey = dialect.mysql() ? ", PRIMARY KEY (id)" : "";

        sql.add("CREATE TABLE servers (" +
            "id VARCHAR(64) NOT NULL, " +
            "name VARCHAR(128) NOT NULL, " +
            "software VARCHAR(64) NOT NULL, " +
            "mc_version VARCHAR(64) NOT NULL, " +
            "max_players INT NOT NULL, " +
            "first_seen BIGINT NOT NULL, " +
            "last_seen BIGINT NOT NULL, " +
            "PRIMARY KEY (id))" + suffix);

        sql.add("CREATE TABLE samples (" +
            autoId + ", " +
            "server_id VARCHAR(64) NOT NULL, " +
            "ts BIGINT NOT NULL, " +
            "players INT NOT NULL, " +
            "max_players INT NOT NULL, " +
            "tps1 DOUBLE NOT NULL, " +
            "tps5 DOUBLE NOT NULL, " +
            "tps15 DOUBLE NOT NULL, " +
            "mspt DOUBLE NOT NULL, " +
            "ram_used BIGINT NOT NULL, " +
            "ram_max BIGINT NOT NULL, " +
            "cpu DOUBLE NOT NULL, " +
            "uptime BIGINT NOT NULL, " +
            "chunks INT NOT NULL, " +
            "entities INT NOT NULL, " +
            "joins INT NOT NULL, " +
            "leaves INT NOT NULL" + autoKey + ")" + suffix);
        sql.add("CREATE INDEX idx_samples_server_ts ON samples (server_id, ts)");

        sql.add("CREATE TABLE samples_minute (" +
            "server_id VARCHAR(64) NOT NULL, " +
            "ts BIGINT NOT NULL, " +
            "players_avg DOUBLE NOT NULL, " +
            "players_max INT NOT NULL, " +
            "tps1 DOUBLE NOT NULL, " +
            "tps5 DOUBLE NOT NULL, " +
            "tps15 DOUBLE NOT NULL, " +
            "mspt DOUBLE NOT NULL, " +
            "ram_used DOUBLE NOT NULL, " +
            "cpu DOUBLE NOT NULL, " +
            "chunks DOUBLE NOT NULL, " +
            "entities DOUBLE NOT NULL, " +
            "joins INT NOT NULL, " +
            "leaves INT NOT NULL, " +
            "sample_count INT NOT NULL, " +
            "PRIMARY KEY (server_id, ts))" + suffix);

        sql.add("CREATE TABLE samples_hour (" +
            "server_id VARCHAR(64) NOT NULL, " +
            "ts BIGINT NOT NULL, " +
            "players_avg DOUBLE NOT NULL, " +
            "players_max INT NOT NULL, " +
            "tps1 DOUBLE NOT NULL, " +
            "tps5 DOUBLE NOT NULL, " +
            "tps15 DOUBLE NOT NULL, " +
            "mspt DOUBLE NOT NULL, " +
            "ram_used DOUBLE NOT NULL, " +
            "cpu DOUBLE NOT NULL, " +
            "chunks DOUBLE NOT NULL, " +
            "entities DOUBLE NOT NULL, " +
            "joins INT NOT NULL, " +
            "leaves INT NOT NULL, " +
            "sample_count INT NOT NULL, " +
            "PRIMARY KEY (server_id, ts))" + suffix);

        sql.add("CREATE TABLE world_samples (" +
            "server_id VARCHAR(64) NOT NULL, " +
            "ts BIGINT NOT NULL, " +
            "world VARCHAR(128) NOT NULL, " +
            "chunks INT NOT NULL, " +
            "entities INT NOT NULL, " +
            "players INT NOT NULL, " +
            "PRIMARY KEY (server_id, ts, world))" + suffix);

        sql.add("CREATE TABLE plugins (" +
            "server_id VARCHAR(64) NOT NULL, " +
            "name VARCHAR(128) NOT NULL, " +
            "version VARCHAR(64) NOT NULL, " +
            "updated_at BIGINT NOT NULL, " +
            "PRIMARY KEY (server_id, name))" + suffix);

        sql.add("CREATE TABLE online_players (" +
            "server_id VARCHAR(64) NOT NULL, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "name VARCHAR(16) NOT NULL, " +
            "world VARCHAR(128) NOT NULL, " +
            "ping INT NOT NULL, " +
            "updated_at BIGINT NOT NULL, " +
            "PRIMARY KEY (server_id, uuid))" + suffix);

        sql.add("CREATE TABLE player_sessions (" +
            "session_id VARCHAR(36) NOT NULL, " +
            "server_id VARCHAR(64) NOT NULL, " +
            "uuid VARCHAR(36) NOT NULL, " +
            "name VARCHAR(16) NOT NULL, " +
            "world VARCHAR(128) NOT NULL, " +
            "joined_at BIGINT NOT NULL, " +
            "left_at BIGINT NULL, " +
            "duration BIGINT NOT NULL, " +
            "kills INT NOT NULL, " +
            "deaths INT NOT NULL, " +
            "PRIMARY KEY (session_id))" + suffix);
        sql.add("CREATE INDEX idx_sessions_server_joined ON player_sessions (server_id, joined_at)");
        sql.add("CREATE INDEX idx_sessions_uuid_joined ON player_sessions (uuid, joined_at)");
        sql.add("CREATE INDEX idx_sessions_server_uuid ON player_sessions (server_id, uuid)");

        sql.add("CREATE TABLE players (" +
            "uuid VARCHAR(36) NOT NULL, " +
            "name VARCHAR(16) NOT NULL, " +
            "first_seen BIGINT NOT NULL, " +
            "last_seen BIGINT NOT NULL, " +
            "PRIMARY KEY (uuid))" + suffix);
        sql.add("CREATE INDEX idx_players_last_seen ON players (last_seen)");
        sql.add("CREATE INDEX idx_players_name ON players (name)");

        sql.add("CREATE TABLE player_stats (" +
            "uuid VARCHAR(36) NOT NULL, " +
            "server_id VARCHAR(64) NOT NULL, " +
            "name VARCHAR(16) NOT NULL, " +
            "playtime BIGINT NOT NULL, " +
            "kills INT NOT NULL, " +
            "deaths INT NOT NULL, " +
            "joins_count INT NOT NULL, " +
            "last_seen BIGINT NOT NULL, " +
            "PRIMARY KEY (uuid, server_id))" + suffix);
        sql.add("CREATE INDEX idx_player_stats_server ON player_stats (server_id)");
        sql.add("CREATE INDEX idx_player_stats_playtime ON player_stats (server_id, playtime)");

        sql.add("CREATE TABLE player_worlds (" +
            "uuid VARCHAR(36) NOT NULL, " +
            "server_id VARCHAR(64) NOT NULL, " +
            "world VARCHAR(128) NOT NULL, " +
            "seconds BIGINT NOT NULL, " +
            "last_seen BIGINT NOT NULL, " +
            "PRIMARY KEY (uuid, server_id, world))" + suffix);

        sql.add("CREATE TABLE job_state (" +
            "name VARCHAR(64) NOT NULL, " +
            "value BIGINT NOT NULL, " +
            "PRIMARY KEY (name))" + suffix);

        return sql;
    }
}
