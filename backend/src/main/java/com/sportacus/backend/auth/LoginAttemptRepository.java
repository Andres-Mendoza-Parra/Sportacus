package com.sportacus.backend.auth;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla {@code login_attempt} (V5). Se escribe fuera de transacciones para que el conteo persista aunque falle el login. */
@Repository
public class LoginAttemptRepository {

    private final JdbcTemplate jdbc;

    public LoginAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public LoginThrottle.State find(String email) {
        List<LoginThrottle.State> rows = jdbc.query(
                "SELECT failures, last_failure_at, locked_until FROM login_attempt WHERE email = ?",
                (rs, i) -> new LoginThrottle.State(rs.getInt(1), at(rs.getTimestamp(2)), at(rs.getTimestamp(3))), email);
        return rows.isEmpty() ? LoginThrottle.State.EMPTY : rows.get(0);
    }

    public void save(String email, LoginThrottle.State s) {
        jdbc.update("""
                INSERT INTO login_attempt (email, failures, last_failure_at, locked_until) VALUES (?, ?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET failures = EXCLUDED.failures,
                    last_failure_at = EXCLUDED.last_failure_at, locked_until = EXCLUDED.locked_until
                """, email, s.failures(), s.lastFailureAt(), s.lockedUntil());
    }

    public void reset(String email) {
        jdbc.update("DELETE FROM login_attempt WHERE email = ?", email);
    }

    private static OffsetDateTime at(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
