package xyz.matumaka.servans.common.config;

public enum DatabaseType {
    SQLITE,
    MYSQL;

    public static DatabaseType parse(String value) {
        if (value == null) {
            return SQLITE;
        }
        return switch (value.trim().toLowerCase()) {
            case "mysql", "mariadb" -> MYSQL;
            default -> SQLITE;
        };
    }
}
