package com.sportacus.backend.content;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.sportacus.backend.common.JdbcArrays;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.content.ContentModels.Assessment;
import com.sportacus.backend.content.ContentModels.Contra;
import com.sportacus.backend.content.ContentModels.CoreFile;
import com.sportacus.backend.content.ContentModels.Metric;
import com.sportacus.backend.content.ContentModels.Movement;
import com.sportacus.backend.content.ContentModels.Progression;
import com.sportacus.backend.content.ContentModels.Step;
import com.sportacus.backend.content.ContentModels.Track;
import com.sportacus.backend.content.ContentModels.World;
import com.sportacus.backend.content.ContentModels.WorldFile;
import com.sportacus.backend.world.catalog.CatalogRepository;

/**
 * Carga el contenido como código ({@code resources/content/**.json}) en la BD al arrancar. Es
 * idempotente (upserts) y se omite si la huella SHA-256 del conjunto no cambió. Cualquier error de
 * contenido detiene el arranque con un mensaje claro: es preferible a servir un catálogo a medias.
 */
@Component
@Order(10)
public class ContentLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentLoader.class);
    private static final String VERSION_KEY = "content/**";

    private final JdbcTemplate jdbc;
    private final Json json;
    private final TransactionTemplate tx;
    private final CatalogRepository catalog;

    public ContentLoader(JdbcTemplate jdbc, Json json, TransactionTemplate tx, CatalogRepository catalog) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = tx;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource core = resolver.getResource("classpath:content/core.json");
        List<Resource> worldFiles = new ArrayList<>(List.of(resolver.getResources("classpath*:content/worlds/*.json")));
        worldFiles.sort(Comparator.comparing(Resource::getFilename));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String coreText = read(core, digest);
        List<String> worldTexts = new ArrayList<>();
        for (Resource r : worldFiles) worldTexts.add(read(r, digest));
        String hash = HexFormat.of().formatHex(digest.digest());

        List<String> stored = jdbc.queryForList("SELECT sha256 FROM content_version WHERE file_name = ?", String.class,
                VERSION_KEY);
        if (!stored.isEmpty() && stored.get(0).equals(hash)) {
            log.info("Contenido sin cambios ({} archivos de mundo)", worldFiles.size());
            return;
        }

        CoreFile coreFile = json.read(coreText, CoreFile.class);
        List<WorldFile> worlds = new ArrayList<>();
        for (int i = 0; i < worldTexts.size(); i++) {
            try {
                worlds.add(json.read(worldTexts.get(i), WorldFile.class));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Contenido inválido en " + worldFiles.get(i).getFilename(), e);
            }
        }

        tx.executeWithoutResult(status -> {
            loadCore(coreFile);
            for (WorldFile wf : worlds) loadMetrics(wf.metrics(), wf.world());
            // Pasada 1: definiciones de movimientos de todos los archivos (permite "ref" entre mundos).
            // Una misma clave definida en dos archivos es un error: la segunda pisaría a la primera en silencio.
            java.util.Map<String, String> definedIn = new java.util.HashMap<>();
            for (WorldFile wf : worlds) {
                for (Movement m : nz(wf.movements())) {
                    if (!m.isDefinition()) continue;
                    String previous = definedIn.put(m.key(), wf.world());
                    if (previous != null) {
                        throw new IllegalStateException("El movimiento '" + m.key() + "' está definido en " + previous
                                + " y en " + wf.world() + ": usa \"ref\" en uno de los dos");
                    }
                    upsertMovement(m);
                }
            }
            // Pasada 2: vínculos, progresiones y tests de cada mundo.
            for (WorldFile wf : worlds) loadWorld(wf);
            jdbc.update("""
                    INSERT INTO content_version (file_name, sha256) VALUES (?, ?)
                    ON CONFLICT (file_name) DO UPDATE SET sha256 = EXCLUDED.sha256, loaded_at = now()
                    """, VERSION_KEY, hash);
        });
        catalog.invalidate();
        log.info("Contenido cargado: {} mundos en core, {} archivos de mundo", nz(coreFile.worlds()).size(), worlds.size());
    }

    private void loadCore(CoreFile core) {
        for (var s : nz(core.sources())) {
            jdbc.update("""
                    INSERT INTO content_source (key, name, url, license, attribution, notes) VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE SET name = EXCLUDED.name, url = EXCLUDED.url,
                        license = EXCLUDED.license, attribution = EXCLUDED.attribution, notes = EXCLUDED.notes
                    """, s.key(), s.name(), s.url(), s.license(), s.attribution(), s.notes());
        }
        for (var t : nz(core.tags())) {
            jdbc.update("""
                    INSERT INTO contraindication_tag (key, label, kind, body_zone) VALUES (?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE SET label = EXCLUDED.label, kind = EXCLUDED.kind,
                        body_zone = EXCLUDED.body_zone
                    """, t.key(), t.label(), t.kind(), t.bodyZone());
        }
        for (World w : nz(core.worlds())) {
            jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO world (key, name, tagline, description, icon, color, status, sort_order, highlights)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (key) DO UPDATE SET name = EXCLUDED.name, tagline = EXCLUDED.tagline,
                            description = EXCLUDED.description, icon = EXCLUDED.icon, color = EXCLUDED.color,
                            status = EXCLUDED.status, sort_order = EXCLUDED.sort_order, highlights = EXCLUDED.highlights
                        """);
                ps.setString(1, w.key());
                ps.setString(2, w.name());
                ps.setString(3, w.tagline());
                ps.setString(4, w.description());
                ps.setString(5, w.icon());
                ps.setString(6, w.color());
                ps.setString(7, w.status() == null ? "activo" : w.status());
                ps.setInt(8, w.sortOrder() == null ? 0 : w.sortOrder());
                ps.setArray(9, JdbcArrays.textArray(con, nz(w.highlights())));
                return ps;
            });
            jdbc.update("UPDATE world_track SET is_active = FALSE WHERE world_key = ?", w.key());
            int order = 0;
            for (Track t : nz(w.tracks())) {
                jdbc.update("""
                        INSERT INTO world_track (world_key, key, name, description, icon, config, sort_order, is_active)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, TRUE)
                        ON CONFLICT (world_key, key) DO UPDATE SET name = EXCLUDED.name,
                            description = EXCLUDED.description, icon = EXCLUDED.icon, config = EXCLUDED.config,
                            sort_order = EXCLUDED.sort_order, is_active = TRUE
                        """, w.key(), t.key(), t.name(), t.description(), t.icon(),
                        json.write(t.config() == null ? Map.of() : t.config()),
                        t.sortOrder() == null ? order : t.sortOrder());
                order++;
            }
        }
        loadMetrics(core.metrics(), null);
    }

    private void loadMetrics(List<Metric> metrics, String defaultWorld) {
        for (Metric m : nz(metrics)) {
            String world = m.world() != null ? m.world() : defaultWorld;
            jdbc.update("""
                    INSERT INTO metric_definition (key, world_key, name, unit, value_type, direction, description)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE SET world_key = EXCLUDED.world_key, name = EXCLUDED.name,
                        unit = EXCLUDED.unit, value_type = EXCLUDED.value_type, direction = EXCLUDED.direction,
                        description = EXCLUDED.description
                    """, m.key(), world, m.name(), m.unit(), m.valueType() == null ? "number" : m.valueType(),
                    m.direction() == null ? "up" : m.direction(), m.description());
        }
    }

    private void upsertMovement(Movement m) {
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO movement (key, name, description, steps, cues, common_mistakes, body_regions, muscles,
                        equipment, environments, impact, media, source_key, license, author, is_active)
                    VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, TRUE)
                    ON CONFLICT (key) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
                        steps = EXCLUDED.steps, cues = EXCLUDED.cues, common_mistakes = EXCLUDED.common_mistakes,
                        body_regions = EXCLUDED.body_regions, muscles = EXCLUDED.muscles,
                        equipment = EXCLUDED.equipment, environments = EXCLUDED.environments,
                        impact = EXCLUDED.impact, media = EXCLUDED.media, source_key = EXCLUDED.source_key,
                        license = EXCLUDED.license, author = EXCLUDED.author, is_active = TRUE
                    """);
            ps.setString(1, m.key());
            ps.setString(2, m.name());
            ps.setString(3, m.description());
            ps.setString(4, json.write(nz(m.steps())));
            ps.setString(5, json.write(nz(m.cues())));
            ps.setString(6, json.write(nz(m.mistakes())));
            ps.setArray(7, JdbcArrays.textArray(con, nz(m.regions())));
            ps.setArray(8, JdbcArrays.textArray(con, nz(m.muscles())));
            ps.setArray(9, JdbcArrays.textArray(con, nz(m.equipment())));
            ps.setArray(10, JdbcArrays.textArray(con, nz(m.environments())));
            ps.setString(11, m.impact() == null ? "bajo" : m.impact());
            ps.setString(12, json.write(m.media() == null ? Map.of() : m.media()));
            ps.setString(13, m.source());
            ps.setString(14, m.license());
            ps.setString(15, m.author());
            return ps;
        });
        jdbc.update("DELETE FROM movement_contraindication WHERE movement_key = ?", m.key());
        for (Contra c : nz(m.contraindications())) {
            jdbc.update("""
                    INSERT INTO movement_contraindication (movement_key, tag_key, level) VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, m.key(), c.tag(), c.level() == null ? "evitar" : c.level());
        }
    }

    private void loadWorld(WorldFile wf) {
        String world = wf.world();
        jdbc.update("DELETE FROM world_movement WHERE world_key = ?", world);
        for (Movement m : nz(wf.movements())) {
            jdbc.update("""
                    INSERT INTO world_movement (world_key, movement_key, role, level, pattern, attrs)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (world_key, movement_key) DO UPDATE SET role = EXCLUDED.role, level = EXCLUDED.level,
                        pattern = EXCLUDED.pattern, attrs = EXCLUDED.attrs
                    """, world, m.effectiveKey(), m.role() == null ? "principal" : m.role(),
                    m.level() == null ? 1 : m.level(), m.pattern(), json.write(m.attrs() == null ? Map.of() : m.attrs()));
        }
        int order = 0;
        for (Progression p : nz(wf.progressions())) {
            jdbc.update("""
                    INSERT INTO progression (key, world_key, name, description, family, icon, sort_order, source_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE SET world_key = EXCLUDED.world_key, name = EXCLUDED.name,
                        description = EXCLUDED.description, family = EXCLUDED.family, icon = EXCLUDED.icon,
                        sort_order = EXCLUDED.sort_order, source_key = EXCLUDED.source_key
                    """, p.key(), world, p.name(), p.description(), p.family(), p.icon(),
                    p.sortOrder() == null ? order : p.sortOrder(), p.source());
            jdbc.update("DELETE FROM progression_step WHERE progression_key = ?", p.key());
            int idx = 0;
            for (Step s : nz(p.steps())) {
                jdbc.update("""
                        INSERT INTO progression_step (progression_key, step_index, name, movement_key, target,
                            unlock_criteria) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                        """, p.key(), idx++, s.name(), s.movement(),
                        json.write(s.target() == null ? Map.of() : s.target()),
                        json.write(s.unlock() == null ? Map.of() : s.unlock()));
            }
            order++;
        }
        order = 0;
        for (Assessment a : nz(wf.assessments())) {
            jdbc.update("""
                    INSERT INTO assessment_definition (key, world_key, name, description, instructions, kind,
                        duration_s, metric_key, unit, retest_days, norms, safety_note, source_key, sort_order)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE SET world_key = EXCLUDED.world_key, name = EXCLUDED.name,
                        description = EXCLUDED.description, instructions = EXCLUDED.instructions, kind = EXCLUDED.kind,
                        duration_s = EXCLUDED.duration_s, metric_key = EXCLUDED.metric_key, unit = EXCLUDED.unit,
                        retest_days = EXCLUDED.retest_days, norms = EXCLUDED.norms,
                        safety_note = EXCLUDED.safety_note, source_key = EXCLUDED.source_key,
                        sort_order = EXCLUDED.sort_order
                    """, a.key(), world, a.name(), a.description(), json.write(nz(a.instructions())), a.kind(),
                    a.durationS(), a.metric(), a.unit(), a.retestDays() == null ? 28 : a.retestDays(),
                    json.write(a.norms() == null ? Map.of() : a.norms()), a.safetyNote(), a.source(),
                    a.sortOrder() == null ? order : a.sortOrder());
            order++;
        }
    }

    private static String read(Resource r, MessageDigest digest) throws Exception {
        try (InputStream in = r.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            digest.update(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
