package com.sportacus.backend.world.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.GeneratedPlan;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedBlock;
import com.sportacus.backend.world.core.GeneratedPlan.GeneratedSession;
import com.sportacus.backend.world.core.Prescription;

import tools.jackson.core.type.TypeReference;

/** Tablas plan, plan_session y plan_block. */
@Repository
public class PlanRepository {

    private static final TypeReference<List<Factor>> FACTORS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final Json json;

    public PlanRepository(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record PlanRow(UUID id, UUID userWorldId, String trackKey, String title, String summary, int weeks,
            LocalDate startsOn, Double score, List<Factor> rationale, String engineVersion, String inputHash,
            Map<String, Object> inputParts, String updateReason, java.time.OffsetDateTime createdAt) {
    }

    public record SessionRow(UUID id, int weekIndex, int dayIndex, Integer weekday, String title, String focus,
            String kind, Integer estMinutes, String notes) {
    }

    public record BlockRow(UUID id, UUID sessionId, int orderIndex, String blockType, String movementKey, String title,
            Prescription prescription, String progressionKey, Integer stepIndex, String notes) {
    }

    /**
     * Bloquea la inscripción (fila de user_world) hasta el fin de la transacción: serializa las regeneraciones del
     * mismo mundo (evita el choque con uq_plan_active cuando llegan dos a la vez).
     */
    public void lockUserWorld(UUID userWorldId) {
        jdbc.query("SELECT id FROM user_world WHERE id = ? FOR UPDATE", rs -> {
        }, userWorldId);
    }

    public UUID replaceActive(UUID userWorldId, String trackKey, GeneratedPlan plan, String engineVersion,
            String inputHash, Map<String, String> inputParts, String updateReason, LocalDate startsOn) {
        lockUserWorld(userWorldId);
        jdbc.update("UPDATE plan SET status = 'archivado' WHERE user_world_id = ? AND status = 'activo'", userWorldId);
        UUID planId = jdbc.queryForObject("""
                INSERT INTO plan (user_world_id, track_key, title, summary, engine_version, score, rationale, weeks,
                                  input_hash, input_parts, update_reason, starts_on)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?) RETURNING id
                """, UUID.class, userWorldId, trackKey, plan.title(), plan.summary(), engineVersion, plan.score(),
                json.write(plan.rationale() == null ? List.of() : plan.rationale()), Math.max(1, plan.weeks()),
                inputHash, json.write(inputParts == null ? Map.of() : inputParts), updateReason, startsOn);
        for (GeneratedSession s : plan.sessions()) {
            UUID sessionId = jdbc.queryForObject("""
                    INSERT INTO plan_session (plan_id, week_index, day_index, weekday, title, focus, kind, est_minutes,
                                              notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, UUID.class, planId, s.weekIndex(), s.dayIndex(), s.weekday(), s.title(), s.focus(), s.kind(),
                    s.estimatedMinutes(), s.notes());
            int order = 0;
            for (GeneratedBlock b : s.blocks()) {
                jdbc.update("""
                        INSERT INTO plan_block (plan_session_id, order_index, block_type, movement_key, title,
                                                prescription, progression_key, step_index, notes)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                        """, sessionId, order++, b.blockType(), b.movementKey(), b.title(),
                        json.write(b.prescription()), b.progressionKey(), b.stepIndex(), b.notes());
            }
        }
        return planId;
    }

    public Optional<PlanRow> active(UUID userWorldId) {
        return jdbc.query("""
                SELECT id, user_world_id, track_key, title, summary, weeks, starts_on, score, rationale::text,
                       engine_version, input_hash, input_parts::text, update_reason, created_at
                FROM plan WHERE user_world_id = ? AND status = 'activo'
                """, (rs, i) -> new PlanRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getInt(6), rs.getObject(7, LocalDate.class),
                rs.getObject(8) == null ? null : rs.getDouble(8), json.read(rs.getString(9), FACTORS),
                rs.getString(10), rs.getString(11), json.readMap(rs.getString(12)), rs.getString(13),
                rs.getObject(14, java.time.OffsetDateTime.class)), userWorldId).stream().findFirst();
    }

    public List<SessionRow> sessions(UUID planId) {
        return jdbc.query("""
                SELECT id, week_index, day_index, weekday, title, focus, kind, est_minutes, notes
                FROM plan_session WHERE plan_id = ? ORDER BY week_index, day_index
                """, (rs, i) -> new SessionRow((UUID) rs.getObject(1), rs.getInt(2), rs.getInt(3),
                rs.getObject(4) == null ? null : rs.getInt(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getObject(8) == null ? null : rs.getInt(8), rs.getString(9)), planId);
    }

    public Map<UUID, List<BlockRow>> blocksByPlan(UUID planId) {
        Map<UUID, List<BlockRow>> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT b.id, b.plan_session_id, b.order_index, b.block_type, b.movement_key, b.title,
                       b.prescription::text, b.progression_key, b.step_index, b.notes
                FROM plan_block b JOIN plan_session s ON s.id = b.plan_session_id
                WHERE s.plan_id = ? ORDER BY b.plan_session_id, b.order_index
                """, rs -> {
            BlockRow row = mapBlock(rs);
            out.computeIfAbsent(row.sessionId(), k -> new ArrayList<>()).add(row);
        }, planId);
        return out;
    }

    public Optional<BlockRow> block(UUID blockId) {
        return jdbc.query("""
                SELECT id, plan_session_id, order_index, block_type, movement_key, title, prescription::text,
                       progression_key, step_index, notes
                FROM plan_block WHERE id = ?
                """, (rs, i) -> mapBlock(rs), blockId).stream().findFirst();
    }

    /** Dueño de un bloque (para verificar que pertenece al usuario). */
    public Optional<UUID> userWorldOfBlock(UUID blockId) {
        return jdbc.query("""
                SELECT p.user_world_id FROM plan_block b JOIN plan_session s ON s.id = b.plan_session_id
                JOIN plan p ON p.id = s.plan_id WHERE b.id = ?
                """, (rs, i) -> (UUID) rs.getObject(1), blockId).stream().findFirst();
    }

    public Optional<UUID> userWorldOfSession(UUID sessionId) {
        return jdbc.query("""
                SELECT p.user_world_id FROM plan_session s JOIN plan p ON p.id = s.plan_id WHERE s.id = ?
                """, (rs, i) -> (UUID) rs.getObject(1), sessionId).stream().findFirst();
    }

    public Optional<SessionRow> session(UUID sessionId) {
        return jdbc.query("""
                SELECT id, week_index, day_index, weekday, title, focus, kind, est_minutes, notes
                FROM plan_session WHERE id = ?
                """, (rs, i) -> new SessionRow((UUID) rs.getObject(1), rs.getInt(2), rs.getInt(3),
                rs.getObject(4) == null ? null : rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getObject(8) == null ? null : rs.getInt(8), rs.getString(9)), sessionId).stream().findFirst();
    }

    public void swapMovement(UUID blockId, String movementKey, String title) {
        jdbc.update("UPDATE plan_block SET movement_key = ?, title = ? WHERE id = ?", movementKey, title, blockId);
    }

    /** Bloques del plan activo que usan un movimiento (para que un mundo ajuste cargas en sitio). */
    public List<BlockRow> activeBlocksOf(UUID userWorldId, String movementKey) {
        return jdbc.query("""
                SELECT b.id, b.plan_session_id, b.order_index, b.block_type, b.movement_key, b.title,
                       b.prescription::text, b.progression_key, b.step_index, b.notes
                FROM plan_block b JOIN plan_session s ON s.id = b.plan_session_id JOIN plan p ON p.id = s.plan_id
                WHERE p.user_world_id = ? AND p.status = 'activo' AND b.movement_key = ?
                """, (rs, i) -> mapBlock(rs), userWorldId, movementKey);
    }

    public void updatePrescription(UUID blockId, Prescription p) {
        jdbc.update("UPDATE plan_block SET prescription = ?::jsonb WHERE id = ?", json.write(p), blockId);
    }

    private BlockRow mapBlock(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BlockRow((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getInt(3), rs.getString(4),
                rs.getString(5), rs.getString(6), json.read(rs.getString(7), Prescription.class), rs.getString(8),
                rs.getObject(9) == null ? null : rs.getInt(9), rs.getString(10));
    }
}
