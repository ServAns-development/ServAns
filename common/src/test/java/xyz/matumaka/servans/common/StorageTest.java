package xyz.matumaka.servans.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.matumaka.servans.common.config.DatabaseSettings;
import xyz.matumaka.servans.common.config.DatabaseType;
import xyz.matumaka.servans.common.config.RetentionSettings;
import xyz.matumaka.servans.common.db.Database;
import xyz.matumaka.servans.common.db.Migrations;
import xyz.matumaka.servans.common.model.OnlinePlayer;
import xyz.matumaka.servans.common.model.PluginEntry;
import xyz.matumaka.servans.common.model.SampleBundle;
import xyz.matumaka.servans.common.model.ServerDescriptor;
import xyz.matumaka.servans.common.model.WorldSample;
import xyz.matumaka.servans.common.rollup.MaintenanceService;
import xyz.matumaka.servans.common.store.DataStore;
import xyz.matumaka.servans.common.util.Time;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StorageTest {
    private static final ServAnsLogger LOGGER = new ServAnsLogger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message, Throwable error) {
        }
    };

    @TempDir
    Path temp;

    private Database database;
    private DataStore store;

    @BeforeEach
    void open() {
        database = Database.open(new DatabaseSettings(DatabaseType.SQLITE, "test.db", "127.0.0.1", 3306, "servans", "", "", 4, false), temp, LOGGER);
        Migrations.migrate(database, LOGGER);
        Migrations.migrate(database, LOGGER);
        store = new DataStore(database);
    }

    @AfterEach
    void close() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void storesSamplesWorldsPluginsAndOnlinePlayers() {
        long now = System.currentTimeMillis();
        ServerDescriptor server = new ServerDescriptor("abc12345", "Test Server", "Paper", "1.21.8", 50);
        store.ensureServer(server, now);
        UUID uuid = UUID.randomUUID();
        SampleBundle bundle = new SampleBundle(
            now, server, 2, 19.95, 19.8, 19.6, 8.4,
            900L * 1024 * 1024, 4096L * 1024 * 1024, 17.25, 90_000L,
            List.of(new WorldSample("world", 120, 450, 2)),
            List.of(new PluginEntry("EssentialsX", "2.21.0")),
            List.of(new OnlinePlayer(uuid.toString(), "Alex", "world", 37)),
            3, 1
        );
        store.recordSample(bundle);
        assertEquals(1, scalar("SELECT COUNT(*) FROM samples"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM world_samples"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM plugins"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM online_players"));
        assertEquals(120, scalar("SELECT chunks FROM samples"));
        assertEquals(450, scalar("SELECT entities FROM samples"));
        assertEquals(3, scalar("SELECT joins FROM samples"));

        SampleBundle next = new SampleBundle(
            now + Time.MINUTE, server, 2, 20.0, 20.0, 20.0, 4.2,
            800L * 1024 * 1024, 4096L * 1024 * 1024, 11.0, 150_000L,
            List.of(new WorldSample("world", 130, 460, 2)),
            null,
            List.of(),
            0, 0
        );
        store.recordSample(next);
        assertEquals(2, scalar("SELECT COUNT(*) FROM samples"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM plugins"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM online_players"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM world_samples"));
    }

    @Test
    void tracksSessionsStatsWorldsAndDeletion() {
        long now = System.currentTimeMillis();
        ServerDescriptor server = new ServerDescriptor("abc12345", "Test Server", "Paper", "1.21.8", 50);
        store.ensureServer(server, now);
        UUID uuid = UUID.randomUUID();
        String session = UUID.randomUUID().toString();
        long joined = now - 3 * Time.MINUTE;
        store.openSession(session, server.id(), uuid, "Alex", "world", joined);
        store.addWorldTime(server.id(), uuid, "world", 60, now);
        store.recordKill(server.id(), uuid, "Alex", session, now);
        store.recordDeath(server.id(), uuid, "Alex", session, now);
        store.closeSession(session, server.id(), uuid, "Alex", "world", now, 180, 120);

        assertEquals(180, scalar("SELECT playtime FROM player_stats WHERE uuid = ?", uuid.toString()));
        assertEquals(1, scalar("SELECT kills FROM player_stats WHERE uuid = ?", uuid.toString()));
        assertEquals(1, scalar("SELECT deaths FROM player_stats WHERE uuid = ?", uuid.toString()));
        assertEquals(1, scalar("SELECT joins_count FROM player_stats WHERE uuid = ?", uuid.toString()));
        assertEquals(180, scalar("SELECT seconds FROM player_worlds WHERE uuid = ?", uuid.toString()));
        assertEquals(180, scalar("SELECT duration FROM player_sessions WHERE session_id = ?", session));
        assertEquals(1, scalar("SELECT kills FROM player_sessions WHERE session_id = ?", session));
        assertEquals(1, scalar("SELECT deaths FROM player_sessions WHERE session_id = ?", session));
        assertNotNull(column("SELECT left_at FROM player_sessions WHERE session_id = ?", session));

        store.deletePlayer(uuid);
        assertEquals(0, scalar("SELECT COUNT(*) FROM player_stats WHERE uuid = ?", uuid.toString()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM player_sessions WHERE uuid = ?", uuid.toString()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM player_worlds WHERE uuid = ?", uuid.toString()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM players WHERE uuid = ?", uuid.toString()));
    }

    @Test
    void rollsUpAndCleansSamples() {
        long now = System.currentTimeMillis();
        ServerDescriptor server = new ServerDescriptor("abc12345", "Test Server", "Paper", "1.21.8", 50);
        store.ensureServer(server, now);
        long oldTs = Time.minuteBucket(now) - 10 * Time.MINUTE;
        for (int i = 0; i < 3; i++) {
            store.recordSample(sample(server, oldTs + i * 10_000L, 20.0, 10.0));
        }
        store.recordSample(sample(server, now - 2 * Time.DAY, 1.0, 100.0));
        MaintenanceService maintenance = new MaintenanceService(database, new RetentionSettings(1, 30, 365), LOGGER);
        maintenance.runOnce(now);
        assertEquals(1, scalar("SELECT COUNT(*) FROM samples_minute"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM samples"));
        assertEquals(4, scalar("SELECT players_avg FROM samples_minute WHERE ts = ?", Time.minuteBucket(oldTs)));
        assertEquals(4, scalar("SELECT players_max FROM samples_minute WHERE ts = ?", Time.minuteBucket(oldTs)));
        maintenance.runOnce(now + Time.MINUTE);
        assertEquals(1, scalar("SELECT COUNT(*) FROM samples_minute"));
        assertEquals(3, scalar("SELECT COUNT(*) FROM samples"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM world_samples WHERE ts < ?", now - Time.DAY));
    }

    private SampleBundle sample(ServerDescriptor server, long ts, double tps, double mspt) {
        return new SampleBundle(ts, server, 4, tps, tps, tps, mspt, 1024, 2048, 5.0, 1000,
            List.of(new WorldSample("world", 10, 20, 4)), null, List.of(), 0, 0);
    }

    private long scalar(String sql, Object... parameters) {
        Object value = column(sql, parameters);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private Object column(String sql, Object... parameters) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getObject(1) : null;
                }
            }
        });
    }
}
