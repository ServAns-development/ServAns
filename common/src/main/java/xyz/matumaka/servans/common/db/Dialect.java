package xyz.matumaka.servans.common.db;

public enum Dialect {
    SQLITE,
    MYSQL;

    public boolean mysql() {
        return this == MYSQL;
    }
}
