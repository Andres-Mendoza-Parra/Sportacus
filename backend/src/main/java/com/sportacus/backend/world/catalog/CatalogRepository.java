package com.sportacus.backend.world.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sportacus.backend.common.JdbcArrays;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.catalog.CatalogModels.AssessmentInfo;
import com.sportacus.backend.world.catalog.CatalogModels.Contraindication;
import com.sportacus.backend.world.catalog.CatalogModels.MetricInfo;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.catalog.CatalogModels.ProgressionInfo;
import com.sportacus.backend.world.catalog.CatalogModels.SourceInfo;
import com.sportacus.backend.world.catalog.CatalogModels.StepInfo;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.core.Prescription;

/**
 * Lectura del catálogo. El contenido solo cambia al arrancar (ContentLoader), así que se cachea en
 * memoria y {@link #invalidate()} lo descarta tras cada carga.
 */
@Repository
public class CatalogRepository {

    private final JdbcTemplate jdbc;
    private final Json json;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    public CatalogRepository(JdbcTemplate jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void invalidate() {
        cache.clear();
    }

    /**
     * Caché simple. No usa computeIfAbsent porque los cargadores se llaman entre sí (p. ej. movements() usa
     * movementIndex()) y ConcurrentHashMap lanza "Recursive update" con cálculos anidados. Si dos hilos cargan a la
     * vez, ambos calculan lo mismo y gana el primero: es inocuo porque el catálogo es de solo lectura.
     */
    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.util.function.Supplier<T> loader) {
        Object hit = cache.get(key);
        if (hit != null) return (T) hit;
        T value = loader.get();
        Object previous = cache.putIfAbsent(key, value);
        return previous != null ? (T) previous : value;
    }

    // ── Mundos y rutas ────────────────────────────────────────

    public List<WorldInfo> worlds() {
        return cached("worlds", () -> {
            List<TrackInfo> tracks = jdbc.query("""
                    SELECT world_key, key, name, description, icon, config::text, sort_order
                    FROM world_track WHERE is_active ORDER BY world_key, sort_order
                    """, (rs, i) -> new TrackInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), json.readMap(rs.getString(6)), rs.getInt(7)));
            return jdbc.query("""
                    SELECT key, name, tagline, description, icon, color, status, sort_order, highlights
                    FROM world WHERE status <> 'oculto' ORDER BY sort_order
                    """, (rs, i) -> {
                String key = rs.getString("key");
                return new WorldInfo(key, rs.getString("name"), rs.getString("tagline"), rs.getString("description"),
                        rs.getString("icon"), rs.getString("color"), rs.getString("status"), rs.getInt("sort_order"),
                        JdbcArrays.strings(rs, "highlights"),
                        tracks.stream().filter(t -> t.worldKey().equals(key)).toList());
            });
        });
    }

    public Optional<WorldInfo> world(String key) {
        return worlds().stream().filter(w -> w.key().equals(key)).findFirst();
    }

    public Optional<TrackInfo> track(String worldKey, String trackKey) {
        return world(worldKey).flatMap(w -> w.tracks().stream().filter(t -> t.key().equals(trackKey)).findFirst());
    }

    // ── Movimientos ───────────────────────────────────────────

    private Map<String, MovementInfo> movementIndex() {
        return cached("movements", () -> {
            Map<String, List<Contraindication>> contra = new LinkedHashMap<>();
            jdbc.query("SELECT movement_key, tag_key, level FROM movement_contraindication", rs -> {
                contra.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new Contraindication(rs.getString(2), rs.getString(3)));
            });
            Map<String, MovementInfo> out = new LinkedHashMap<>();
            jdbc.query("""
                    SELECT key, name, description, steps::text, cues::text, common_mistakes::text, body_regions,
                           muscles, equipment, environments, impact, media::text, source_key, license, author
                    FROM movement WHERE is_active
                    """, rs -> {
                String key = rs.getString("key");
                out.put(key, new MovementInfo(key, rs.getString("name"), rs.getString("description"),
                        strings(rs.getString("steps")), strings(rs.getString("cues")),
                        strings(rs.getString("common_mistakes")), JdbcArrays.strings(rs, "body_regions"),
                        JdbcArrays.strings(rs, "muscles"), JdbcArrays.strings(rs, "equipment"),
                        JdbcArrays.strings(rs, "environments"), rs.getString("impact"),
                        json.readMap(rs.getString("media")), rs.getString("source_key"), rs.getString("license"),
                        rs.getString("author"), contra.getOrDefault(key, List.of())));
            });
            return out;
        });
    }

    public Optional<MovementInfo> movement(String key) {
        return Optional.ofNullable(key == null ? null : movementIndex().get(key));
    }

    /** Todos los movimientos de un mundo (sin filtrar por seguridad; usar {@link SafetyFilter}). */
    public List<WorldMovement> movements(String worldKey) {
        return cached("wm:" + worldKey, () -> {
            Map<String, MovementInfo> index = movementIndex();
            List<WorldMovement> out = new ArrayList<>();
            jdbc.query("""
                    SELECT movement_key, role, level, pattern, attrs::text FROM world_movement WHERE world_key = ?
                    ORDER BY level, movement_key
                    """, rs -> {
                MovementInfo m = index.get(rs.getString(1));
                if (m != null) {
                    out.add(new WorldMovement(m, worldKey, rs.getString(2), rs.getInt(3), rs.getString(4),
                            json.readMap(rs.getString(5))));
                }
            }, worldKey);
            return out;
        });
    }

    public Optional<WorldMovement> worldMovement(String worldKey, String movementKey) {
        return movements(worldKey).stream().filter(m -> m.key().equals(movementKey)).findFirst();
    }

    // ── Progresiones ──────────────────────────────────────────

    public List<ProgressionInfo> progressions(String worldKey) {
        return cached("prog:" + worldKey, () -> {
            Map<String, List<StepInfo>> steps = new LinkedHashMap<>();
            jdbc.query("""
                    SELECT s.progression_key, s.step_index, s.name, s.movement_key, s.target::text,
                           s.unlock_criteria::text
                    FROM progression_step s JOIN progression p ON p.key = s.progression_key
                    WHERE p.world_key = ? ORDER BY s.progression_key, s.step_index
                    """, rs -> {
                String target = rs.getString(5);
                Prescription p = target == null || target.equals("{}") ? null : json.read(target, Prescription.class);
                steps.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new StepInfo(rs.getInt(2),
                        rs.getString(3), rs.getString(4), p, json.readMap(rs.getString(6))));
            }, worldKey);
            return jdbc.query("""
                    SELECT key, world_key, name, description, family, icon, sort_order, source_key
                    FROM progression WHERE world_key = ? ORDER BY sort_order, key
                    """, (rs, i) -> new ProgressionInfo(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getString(8),
                    steps.getOrDefault(rs.getString(1), List.of())), worldKey)
                    .stream().filter(p -> !p.steps().isEmpty()).toList();
        });
    }

    public Optional<ProgressionInfo> progression(String worldKey, String key) {
        return progressions(worldKey).stream().filter(p -> p.key().equals(key)).findFirst();
    }

    // ── Tests, métricas y fuentes ─────────────────────────────

    public List<AssessmentInfo> assessments(String worldKey) {
        return cached("assess:" + worldKey, () -> jdbc.query("""
                SELECT key, world_key, name, description, instructions::text, kind, duration_s, metric_key, unit,
                       retest_days, norms::text, safety_note, source_key, sort_order
                FROM assessment_definition WHERE world_key = ? ORDER BY sort_order, key
                """, (rs, i) -> new AssessmentInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                strings(rs.getString(5)), rs.getString(6), (Integer) rs.getObject(7), rs.getString(8),
                rs.getString(9), rs.getInt(10), json.readMap(rs.getString(11)), rs.getString(12), rs.getString(13),
                rs.getInt(14)), worldKey));
    }

    public Optional<AssessmentInfo> assessment(String key) {
        List<AssessmentInfo> rows = jdbc.query("SELECT world_key FROM assessment_definition WHERE key = ?",
                (rs, i) -> rs.getString(1), key).stream()
                .flatMap(w -> assessments(w).stream()).filter(a -> a.key().equals(key)).toList();
        return rows.stream().findFirst();
    }

    public Map<String, MetricInfo> metrics() {
        return cached("metrics", () -> {
            Map<String, MetricInfo> out = new LinkedHashMap<>();
            jdbc.query("SELECT key, world_key, name, unit, value_type, direction, description FROM metric_definition",
                    rs -> {
                        out.put(rs.getString(1), new MetricInfo(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
                    });
            return out;
        });
    }

    public Optional<MetricInfo> metric(String key) {
        return Optional.ofNullable(metrics().get(key));
    }

    public List<SourceInfo> sources() {
        return cached("sources", () -> jdbc.query("SELECT key, name, url, license, attribution FROM content_source ORDER BY key",
                (rs, i) -> new SourceInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5))));
    }

    public Map<String, String> tagLabels() {
        return cached("tags", () -> {
            Map<String, String> out = new LinkedHashMap<>();
            jdbc.query("SELECT key, label FROM contraindication_tag", rs -> {
                out.put(rs.getString(1), rs.getString(2));
            });
            return out;
        });
    }

    private List<String> strings(String raw) {
        List<String> out = new ArrayList<>();
        for (Object o : json.readList(raw)) {
            if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }

    public static Comparator<WorldMovement> byLevel() {
        return Comparator.comparingInt(WorldMovement::level);
    }
}
