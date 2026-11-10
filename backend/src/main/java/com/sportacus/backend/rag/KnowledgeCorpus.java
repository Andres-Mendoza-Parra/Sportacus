package com.sportacus.backend.rag;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.sportacus.backend.legal.LegalService;
import com.sportacus.backend.world.catalog.CatalogModels.AssessmentInfo;
import com.sportacus.backend.world.catalog.CatalogModels.MetricInfo;
import com.sportacus.backend.world.catalog.CatalogModels.ProgressionInfo;
import com.sportacus.backend.world.catalog.CatalogModels.TrackInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.CatalogRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Todo lo que el asistente puede consultar, convertido en fragmentos: el catálogo por mundos (mundos, rutas,
 * movimientos, progresiones, tests y métricas), las guías curadas de {@code content/knowledge/guias.json} y las
 * secciones de la política de datos y los términos. No incluye datos de usuarios: esos llegan al prompt solo si la
 * persona activa "usar mis datos".
 */
@Component
public class KnowledgeCorpus {

    static final String CATALOG_REFERENCE = "Catálogo de Sportacus";

    private final CatalogRepository catalog;
    private final LegalService legal;
    private final JsonMapper json = JsonMapper.builder().build();

    public KnowledgeCorpus(CatalogRepository catalog, LegalService legal) {
        this.catalog = catalog;
        this.legal = legal;
    }

    public List<KnowledgeDoc> build() {
        List<KnowledgeDoc> docs = new ArrayList<>();
        catalog.invalidate();
        for (WorldInfo w : catalog.worlds()) {
            docs.add(doc("world", w.key(), w.key(), "Mundo " + w.name(), w.tagline() + ". " + w.description()
                    + (w.highlights().isEmpty() ? "" : ". Destacados: " + String.join("; ", w.highlights()))));
            for (TrackInfo t : w.tracks()) {
                docs.add(doc("track", w.key() + ":" + t.key(), w.key(), "Ruta " + t.name() + " (" + w.name() + ")",
                        "Ruta del mundo " + w.name() + ". " + t.description()));
            }
            for (WorldMovement m : catalog.movements(w.key())) {
                docs.add(doc("movement", w.key() + ":" + m.key(), w.key(), m.movement().name() + " (" + w.name() + ")",
                        movementText(w, m)));
            }
            for (ProgressionInfo p : catalog.progressions(w.key())) {
                StringBuilder sb = new StringBuilder();
                if (p.description() != null) sb.append(p.description()).append(". ");
                sb.append("Escalones en orden: ");
                p.steps().forEach(s -> sb.append(s.index() + 1).append(") ").append(s.name()).append("; "));
                docs.add(doc("progression", p.key(), w.key(), "Progresión " + p.name() + " (" + w.name() + ")",
                        sb.toString()));
            }
            for (AssessmentInfo a : catalog.assessments(w.key())) {
                docs.add(doc("assessment", a.key(), w.key(), "Test " + a.name() + " (" + w.name() + ")",
                        a.description() + ". Instrucciones: " + String.join(" ", a.instructions())
                                + (a.safetyNote() == null ? "" : ". Seguridad: " + a.safetyNote())));
            }
        }
        for (MetricInfo m : catalog.metrics().values()) {
            if (m.description() == null || m.description().isBlank()) continue;
            docs.add(doc("metric", m.key(), m.worldKey(), "Métrica " + m.name(),
                    m.description() + (m.unit() == null ? "" : ". Se mide en " + m.unit())));
        }
        docs.addAll(guides());
        for (LegalService.LegalDocument d : legal.documents()) {
            docs.add(new KnowledgeDoc("legal", d.key() + ":resumen", null, d.title() + " (resumen)", d.summary(),
                    d.title() + " v" + d.version()));
            int i = 0;
            for (LegalService.Section s : d.sections()) {
                docs.add(new KnowledgeDoc("legal", d.key() + ":" + (i++), null, d.title() + " · " + s.title(),
                        s.body(), d.title() + " v" + d.version()));
            }
        }
        return docs;
    }

    /** Guías generales curadas (descanso, nutrición, hidratación, uso de la app…), cada una con su fuente. */
    List<KnowledgeDoc> guides() {
        try (InputStream in = new ClassPathResource("content/knowledge/guias.json").getInputStream()) {
            List<KnowledgeDoc> out = new ArrayList<>();
            for (JsonNode g : json.readTree(in).path("guias")) {
                String world = g.path("world").isString() ? g.path("world").asString() : null;
                out.add(new KnowledgeDoc("guide", g.path("id").asString(), world, g.path("title").asString(),
                        g.path("text").asString(), g.path("source").asString()));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer content/knowledge/guias.json", e);
        }
    }

    private String movementText(WorldInfo w, WorldMovement wm) {
        var m = wm.movement();
        StringBuilder sb = new StringBuilder("Movimiento del mundo ").append(w.name());
        append(sb, "Descripción", m.description());
        append(sb, "Músculos", String.join(", ", m.muscles()));
        append(sb, "Equipo", m.equipment().isEmpty() ? "sin equipo" : String.join(", ", m.equipment()));
        append(sb, "Nivel (1-5)", String.valueOf(wm.level()));
        append(sb, "Patrón", wm.pattern());
        append(sb, "Pasos", String.join(" ", m.steps()));
        append(sb, "Claves técnicas", String.join(" ", m.cues()));
        append(sb, "Errores comunes", String.join("; ", m.commonMistakes()));
        if (!m.contraindications().isEmpty()) {
            append(sb, "Precauciones", String.join(", ", m.contraindications().stream()
                    .map(c -> catalog.tagLabels().getOrDefault(c.tag(), c.tag()) + " (" + c.level() + ")").toList()));
        }
        return sb.toString();
    }

    private static KnowledgeDoc doc(String type, String id, String world, String title, String content) {
        return new KnowledgeDoc(type, id, world, title, content, CATALOG_REFERENCE);
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(". ").append(label).append(": ").append(value.trim());
    }
}
