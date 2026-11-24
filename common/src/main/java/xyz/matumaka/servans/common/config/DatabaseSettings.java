package xyz.matumaka.servans.common.config;

public record DatabaseSettings(
    DatabaseType type,
    String file,
    String host,
    int port,
    String database,
    String username,
    String password,
    int poolSize,
    boolean useSsl
) {
    public DatabaseSettings {
        type = type == null ? DatabaseType.SQLITE : type;
        file = file == null || file.isBlank() ? "servans.db" : file;
        host = host == null || host.isBlank() ? "127.0.0.1" : host;
        port = port <= 0 || port > 65535 ? 3306 : port;
        database = database == null || database.isBlank() ? "servans" : database;
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        poolSize = Math.max(1, Math.min(32, poolSize));
    }
}
