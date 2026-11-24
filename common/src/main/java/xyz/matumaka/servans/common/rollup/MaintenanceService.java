package xyz.matumaka.servans.common.rollup;

import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.RetentionSettings;
import xyz.matumaka.servans.common.db.Database;
import xyz.matumaka.servans.common.db.Dialect;
import xyz.matumaka.servans.common.db.Upsert;
import xyz.matumaka.servans.common.util.Time;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class MaintenanceService {
    private static final String MINUTE_WATERMARK = "rollup_minute";
    private static final String HOUR_WATERMARK = "rollup_hour";
    private static final long CHUNK = 6 * Time.HOUR;
    private static final long INITIAL_BACKFILL = 2 * Time.HOUR;
    private static final int CHUNKS_PER_RUN = 4;
    private static final long ONLINE_TTL = 10 * Time.MINUTE;

    private static final List<String> ROLLUP_COLUMNS = List.of(
        "server_id", "ts", "players_avg", "players_max", "tps1", "tps5", "tps15", "mspt",
        "ram_used", "cpu", "chunks", "entities", "joins", "leaves", "sample_count"
    );
    private static final List<String> ROLLUP_KEYS = List.of("server_id", "ts");
    private static final List<String> ROLLUP_UPDATES = List.of(
        "players_avg", "players_max", "tps1", "tps5", "tps15", "mspt",
        "ram_used", "cpu", "chunks", "entities", "joins", "leaves", "sample_count"
    );

    private final Database database;
    private final Dialect dialect;
    private final RetentionSettings retention;
    private final ServAnsLogger logger;

    public MaintenanceService(Database database, RetentionSettings retention, ServAnsLogger logger) {
        this.database = database;
        this.dialect = database.dialect();
        this.retention = retention;
        this.logger = logger;
    }

    public void runOnce(long now) {
        try {
            rollupMinutes(now);
            rollupHours(now);
            cleanup(now);
        } catch (Exception error) {
            logger.error("Maintenance job failed", error);
        }
    }

    private void rollupMinutes(long now) {
        long end = Time.minuteBucket(now);
        long start = database.read(connection -> watermark(connection, MINUTE_WATERMARK, end - INITIAL_BACKFILL));
        for (int i = 0; i < CHUNKS_PER_RUN && start < end; i++) {
            long from = start;
            long to = Math.min(end, from + CHUNK);
            int rows = database.read(connection -> count(connection, "samples", from, to)) > 0
                ? aggregateMinutes(from, to)
                : 0;
            database.write(connection -> setWatermark(connection, MINUTE_WATERMARK, to));
            start = to;
            if (rows > 0) {
                logger.debug("Rolled up " + rows + " minute buckets");
            }
        }
    }

    private int count(Connection connection, String table, long from, long to) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE ts >= ? AND ts < ?")) {
            statement.setLong(1, from);
            statement.setLong(2, to);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int aggregateMinutes(long from, long to) {
        List<RollupRow> rows = database.read(connection -> {
            List<RollupRow> collected = new ArrayList<>();
            String sql = "SELECT server_id, FLOOR(ts / 60000) * 60000 AS bucket, " +
                "AVG(players), MAX(players), AVG(tps1), AVG(tps5), AVG(tps15), AVG(mspt), " +
                "AVG(ram_used), AVG(cpu), AVG(chunks), AVG(entities), SUM(joins), SUM(leaves), COUNT(*) " +
                "FROM samples WHERE ts >= ? AND ts < ? GROUP BY server_id, bucket";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, from);
                statement.setLong(2, to);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        collected.add(readRollup(result));
                    }
                }
            }
            return collected;
        });
        if (rows.isEmpty()) {
            return 0;
        }
        database.write(connection -> {
            for (RollupRow row : rows) {
                Upsert.run(connection, dialect, "samples_minute", ROLLUP_KEYS, ROLLUP_COLUMNS, ROLLUP_UPDATES, row.values());
            }
        });
        return rows.size();
    }

    private void rollupHours(long now) {
        long end = Time.hourBucket(now);
        long start = database.read(connection -> watermark(connection, HOUR_WATERMARK, end - INITIAL_BACKFILL));
        for (int i = 0; i < CHUNKS_PER_RUN && start < end; i++) {
            long from = start;
            long to = Math.min(end, from + CHUNK);
            List<RollupRow> rows = database.read(connection -> {
                List<RollupRow> collected = new ArrayList<>();
                String sql = "SELECT server_id, FLOOR(ts / 3600000) * 3600000 AS bucket, " +
                    "AVG(players_avg), MAX(players_max), AVG(tps1), AVG(tps5), AVG(tps15), AVG(mspt), " +
                    "AVG(ram_used), AVG(cpu), AVG(chunks), AVG(entities), SUM(joins), SUM(leaves), SUM(sample_count) " +
                    "FROM samples_minute WHERE ts >= ? AND ts < ? GROUP BY server_id, bucket";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, from);
                    statement.setLong(2, to);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            collected.add(readRollup(result));
                        }
                    }
                }
                return collected;
            });
            database.write(connection -> {
                for (RollupRow row : rows) {
                    Upsert.run(connection, dialect, "samples_hour", ROLLUP_KEYS, ROLLUP_COLUMNS, ROLLUP_UPDATES, row.values());
                }
                setWatermark(connection, HOUR_WATERMARK, to);
            });
            start = to;
        }
    }

    private void cleanup(long now) {
        long rawCutoff = now - Time.days(retention.rawDays());
        long minuteCutoff = now - Time.days(retention.minuteDays());
        long hourCutoff = now - Time.days(retention.hourDays());
        int raw = delete("DELETE FROM samples WHERE ts < ?", rawCutoff);
        delete("DELETE FROM world_samples WHERE ts < ?", rawCutoff);
        int minutes = delete("DELETE FROM samples_minute WHERE ts < ?", minuteCutoff);
        int hours = delete("DELETE FROM samples_hour WHERE ts < ?", hourCutoff);
        delete("DELETE FROM online_players WHERE updated_at < ?", now - ONLINE_TTL);
        if (raw + minutes + hours > 0) {
            logger.info("Retention cleanup removed " + raw + " raw, " + minutes + " minute and " + hours + " hourly samples");
        }
    }

    private int delete(String sql, long cutoff) {
        return database.read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, cutoff);
                return statement.executeUpdate();
            }
        });
    }

    private long watermark(Connection connection, String name, long fallback) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM job_state WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : fallback;
            }
        }
    }

    private void setWatermark(Connection connection, String name, long value) throws java.sql.SQLException {
        Upsert.run(connection, dialect, "job_state", List.of("name"), List.of("name", "value"), List.of("value"), new Object[]{name, value});
    }

    private static RollupRow readRollup(ResultSet result) throws java.sql.SQLException {
        return new RollupRow(new Object[]{
            result.getString(1), result.getLong(2), result.getDouble(3), result.getInt(4), result.getDouble(5),
            result.getDouble(6), result.getDouble(7), result.getDouble(8), result.getDouble(9), result.getDouble(10),
            result.getDouble(11), result.getDouble(12), result.getLong(13), result.getLong(14), result.getLong(15)
        });
    }

    private record RollupRow(Object[] values) {
    }
}
