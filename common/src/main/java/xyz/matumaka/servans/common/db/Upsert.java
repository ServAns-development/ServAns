package xyz.matumaka.servans.common.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class Upsert {
    private Upsert() {
    }

    public static void run(Connection connection, Dialect dialect, String table, List<String> keys, List<String> columns, List<String> updateColumns, Object[] values) throws SQLException {
        if (columns.size() != values.length) {
            throw new IllegalArgumentException("column/value mismatch for " + table);
        }
        StringBuilder sql = new StringBuilder(160);
        sql.append("INSERT INTO ").append(table).append(" (").append(String.join(", ", columns)).append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(')');
        if (dialect == Dialect.SQLITE) {
            sql.append(" ON CONFLICT (").append(String.join(", ", keys)).append(')');
            if (updateColumns.isEmpty()) {
                sql.append(" DO NOTHING");
            } else {
                sql.append(" DO UPDATE SET ");
                for (int i = 0; i < updateColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append(updateColumns.get(i)).append(" = excluded.").append(updateColumns.get(i));
                }
            }
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            if (updateColumns.isEmpty()) {
                sql.append(keys.get(0)).append(" = ").append(keys.get(0));
            } else {
                for (int i = 0; i < updateColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append(updateColumns.get(i)).append(" = VALUES(").append(updateColumns.get(i)).append(')');
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }
}
