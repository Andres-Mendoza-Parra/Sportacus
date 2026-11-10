package com.sportacus.backend.world.library;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.media.ExerciseEnrichmentService;
import com.sportacus.backend.media.ExerciseEnrichmentService.Enrichment;
import com.sportacus.backend.media.ExerciseMedia;
import com.sportacus.backend.media.ExerciseMediaService;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.catalog.CatalogModels.SourceInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.catalog.SafetyFilter;
import com.sportacus.backend.world.core.WorldContext;

/** Biblioteca de movimientos del mundo activo y ficha detallada de cada uno. */
@Service
public class MovementService {

    private final CatalogRepository catalog;
    private final SafetyFilter safety;
    private final ExerciseMediaService media;
    private final ExerciseEnrichmentService enrichment;
    private final JdbcTemplate jdbc;

    public MovementService(CatalogRepository catalog, SafetyFilter safety, ExerciseMediaService media,
            ExerciseEnrichmentService enrichment, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.safety = safety;
        this.media = media;
        this.enrichment = enrichment;
        this.jdbc = jdbc;
    }

    public record MovementCard(String key, String name, String role, int level, String pattern, List<String> muscles,
            List<String> equipment, String imageUrl, boolean favorite, boolean equipmentOk) {
    }

    public record MovementDetail(String key, String name, String description, List<String> steps, List<String> cues,
            List<String> commonMistakes, List<String> muscles, List<String> equipment, String impact, Integer level,
            String pattern, List<String> imageUrls, String videoUrl, List<ExerciseMedia.MuscleView> muscleViews,
            List<String> cautions, boolean allowed, boolean favorite, String sourceName, String sourceUrl,
            String license, String author) {
    }

    public List<MovementCard> library(WorldContext ctx, String q, String pattern, boolean favoritesOnly) {
        Set<String> favorites = favorites(ctx.user().userId());
        Set<String> available = SafetyFilter.availableEquipment(ctx.user().profile());
        String query = q == null ? null : q.toLowerCase(Locale.ROOT).trim();
        return safety.filter(catalog.movements(ctx.world().worldKey()), ctx.user()).stream()
                .filter(m -> query == null || query.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(query))
                .filter(m -> pattern == null || pattern.isBlank() || pattern.equals(m.pattern()))
                .filter(m -> !favoritesOnly || favorites.contains(m.key()))
                .map(m -> new MovementCard(m.key(), m.name(), m.role(), m.level(), m.pattern(), m.movement().muscles(),
                        m.movement().equipment(), m.movement().firstImage(), favorites.contains(m.key()),
                        SafetyFilter.hasEquipment(m.movement(), available)))
                .toList();
    }

    public List<String> patterns(WorldContext ctx) {
        return catalog.movements(ctx.world().worldKey()).stream().map(WorldMovement::pattern)
                .filter(p -> p != null && !p.isBlank()).distinct().sorted().toList();
    }

    public MovementDetail detail(WorldContext ctx, String key) {
        MovementInfo m = catalog.movement(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado"));
        WorldMovement wm = catalog.worldMovement(ctx.world().worldKey(), key).orElse(null);
        // wger solo se consulta para movimientos de fuerza con nombre emparejable (media.wger = true)
        ExerciseMedia wger = Boolean.TRUE.equals(m.media().get("wger"))
                ? media.lookup(String.valueOf(m.media().getOrDefault("wgerName", m.name())), m.firstMuscle(), List.of())
                : ExerciseMedia.empty();
        List<String> images = new java.util.ArrayList<>();
        if (m.media().get("images") instanceof List<?> l) l.forEach(o -> images.add(String.valueOf(o)));
        images.addAll(wger.imageUrls());
        String video = m.media().get("videoId") != null ? "https://www.youtube.com/watch?v=" + m.media().get("videoId")
                : wger.videoUrl();
        List<String> steps = m.steps();
        String description = m.description();
        List<String> mistakes = m.commonMistakes();
        if (steps.isEmpty() || description == null) {
            Enrichment e = enrichment.lookup(m.key(), m.name(), m.firstMuscle(), String.join(", ", m.equipment()),
                    String.join(" ", wger.instructions()));
            if (steps.isEmpty()) steps = !e.steps().isEmpty() ? e.steps() : wger.instructions();
            if (description == null) description = e.description() != null ? e.description() : wger.description();
            if (mistakes.isEmpty()) mistakes = e.commonMistakes();
        }
        SourceInfo src = m.sourceKey() == null ? null
                : catalog.sources().stream().filter(s -> s.key().equals(m.sourceKey())).findFirst().orElse(null);
        List<String> cautions = safety.cautions(m, ctx.user()).stream()
                .map(t -> "Precaución por " + catalog.tagLabels().getOrDefault(t, t).toLowerCase(Locale.ROOT)).toList();
        return new MovementDetail(m.key(), m.name(), description, steps, m.cues(), mistakes, m.muscles(), m.equipment(),
                m.impact(), wm == null ? null : wm.level(), wm == null ? null : wm.pattern(), images, video,
                wger.muscleViews().isEmpty() ? media.muscleViewsFor(m.muscles()) : wger.muscleViews(), cautions,
                safety.allowed(m, ctx.user()), favorites(ctx.user().userId()).contains(key),
                src == null ? null : src.name(), src == null ? null : src.url(),
                m.license() != null ? m.license() : src == null ? null : src.license(), m.author());
    }

    public void setFavorite(UUID userId, String key, boolean favorite) {
        if (catalog.movement(key).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado");
        if (favorite) {
            jdbc.update("INSERT INTO favorite_movement (user_id, movement_key) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    userId, key);
        } else {
            jdbc.update("DELETE FROM favorite_movement WHERE user_id = ? AND movement_key = ?", userId, key);
        }
    }

    public void feedback(WorldContext ctx, String key, String reaction) {
        if (!Set.of("like", "dislike").contains(reaction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reacción no válida");
        }
        if (catalog.movement(key).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado");
        jdbc.update("INSERT INTO movement_feedback (user_id, world_key, movement_key, reaction) VALUES (?, ?, ?, ?)",
                ctx.user().userId(), ctx.world().worldKey(), key, reaction);
    }

    private Set<String> favorites(UUID userId) {
        return Set.copyOf(jdbc.queryForList("SELECT movement_key FROM favorite_movement WHERE user_id = ?",
                String.class, userId));
    }
}
