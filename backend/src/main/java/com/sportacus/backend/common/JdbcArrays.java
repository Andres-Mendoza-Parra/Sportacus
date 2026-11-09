package com.sportacus.backend.common;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Conversión entre arreglos de PostgreSQL y listas de Java (antes estaba copiada en 3 repositorios). */
public final class JdbcArrays {

    private JdbcArrays() {
    }

    public static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        List<String> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (Object o : (Object[]) array.getArray()) {
            if (o != null) out.add(o.toString());
        }
        return out;
    }

    public static List<Integer> ints(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        List<Integer> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (Object o : (Object[]) array.getArray()) {
            if (o instanceof Number n) out.add(n.intValue());
        }
        return out;
    }

    public static Array textArray(Connection connection, Collection<String> values) throws SQLException {
        return connection.createArrayOf("text", values == null ? new String[0] : values.toArray(String[]::new));
    }

    public static Array smallintArray(Connection connection, Collection<Integer> values) throws SQLException {
        return connection.createArrayOf("int2", values == null ? new Integer[0] : values.toArray(Integer[]::new));
    }
}
