package xyz.matumaka.servans.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import xyz.matumaka.servans.common.ServAnsLogger;
import xyz.matumaka.servans.common.config.DatabaseSettings;
import xyz.matumaka.servans.common.config.DatabaseType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final Dialect dialect;

    private Database(HikariDataSource dataSource, Dialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public static Database open(DatabaseSettings settings, Path dataDirectory, ServAnsLogger logger) {
        Dialect dialect = settings.type() == DatabaseType.MYSQL ? Dialect.MYSQL : Dialect.SQLITE;
        HikariConfig config = new HikariConfig();
        config.setPoolName("ServAns");
        config.setConnectionTimeout(15_000);
        config.setValidationTimeout(5_000);
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(dialect == Dialect.SQLITE ? 1 : Math.min(2, settings.poolSize()));
        if (dialect == Dialect.SQLITE) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (Exception error) {
                throw new DatabaseException("could not create data directory " + dataDirectory, error);
            }
            Path file = dataDirectory.resolve(settings.file()).toAbsolutePath().normalize();
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + file);
            config.setConnectionInitSql("PRAGMA busy_timeout=8000");
            config.setMaxLifetime(0);
            config.setIdleTimeout(0);
        } else {
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database()
                + "?useSsl=" + settings.useSsl() + "&useUnicode=true&characterEncoding=utf8&connectTimeout=10000");
            config.setUsername(settings.username());
            config.setPassword(settings.password());
            config.setMaxLifetime(1_800_000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        HikariDataSource source;
        try {
            source = new HikariDataSource(config);
        } catch (Exception error) {
            throw new DatabaseException("could not open database", error);
        }
        Database database = new Database(source, dialect);
        if (dialect == Dialect.SQLITE) {
            try {
                try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA synchronous=NORMAL");
                }
            } catch (SQLException error) {
                source.close();
                throw new DatabaseException("could not initialise sqlite", error);
            }
        }
        logger.info("Connected to " + dialect.name().toLowerCase() + " database");
        return database;
    }

    public Dialect dialect() {
        return dialect;
    }

    public <T> T read(SqlFunction<T> function) {
        try (Connection connection = dataSource.getConnection()) {
            return function.apply(connection);
        } catch (SQLException error) {
            throw new DatabaseException("database read failed", error);
        }
    }

    public void write(SqlConsumer consumer) {
        transaction(consumer);
    }

    public void transaction(SqlConsumer consumer) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                consumer.accept(connection);
                connection.commit();
            } catch (Exception error) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw error;
            } finally {
                connection.setAutoCommit(previous);
            }
        } catch (DatabaseException error) {
            throw error;
        } catch (Exception error) {
            throw new DatabaseException("database write failed", error);
        }
    }

    public void execute(String sql) {
        write(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
