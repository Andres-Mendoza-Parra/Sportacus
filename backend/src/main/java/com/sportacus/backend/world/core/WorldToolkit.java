package com.sportacus.backend.world.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.catalog.SafetyFilter;

/**
 * Utilidades compartidas por los módulos de mundo para no repetir lógica: movimientos utilizables
 * (seguros + con equipamiento disponible), días de la semana sugeridos, gustos del usuario y un
 * azar estable por usuario (el mismo usuario obtiene el mismo plan si nada cambia).
 */
@Component
public class WorldToolkit {

    private static final String[] DAY_NAMES = { "", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado",
            "Domingo" };

    private final CatalogRepository catalog;
    private final SafetyFilter safety;
    private final JdbcTemplate jdbc;

    public WorldToolkit(CatalogRepository catalog, SafetyFilter safety, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.safety = safety;
        this.jdbc = jdbc;
    }

    public CatalogRepository catalog() {
        return catalog;
    }

    public SafetyFilter safety() {
        return safety;
    }

    /** Movimientos del mundo que el usuario puede hacer: sin contraindicaciones y con el equipamiento a mano. */
    public List<WorldMovement> usableMovements(String worldKey, UserContext user) {
        Set<String> available = SafetyFilter.availableEquipment(user.profile());
        return safety.filter(catalog.movements(worldKey), user).stream()
                .filter(m -> SafetyFilter.hasEquipment(m.movement(), available)).toList();
    }

    /** Igual que {@link #usableMovements} pero con un conjunto de equipamiento explícito. */
    public List<WorldMovement> usableMovements(String worldKey, UserContext user, Set<String> equipment) {
        return safety.filter(catalog.movements(worldKey), user).stream()
                .filter(m -> SafetyFilter.hasEquipment(m.movement(), equipment)).toList();
    }

    /** Días sugeridos (1=lunes … 7=domingo): los preferidos del usuario o un reparto con descansos. */
    public List<Integer> weekdays(UserProfileData profile, int sessions) {
        int n = Math.max(1, Math.min(7, sessions));
        List<Integer> preferred = profile.preferredDays() == null ? List.of() : profile.preferredDays();
        if (preferred.size() >= n) {
            return preferred.stream().sorted().limit(n).toList();
        }
        return switch (n) {
            case 1 -> List.of(3);
            case 2 -> List.of(2, 5);
            case 3 -> List.of(1, 3, 5);
            case 4 -> List.of(1, 2, 4, 5);
            case 5 -> List.of(1, 2, 3, 5, 6);
            case 6 -> List.of(1, 2, 3, 4, 5, 6);
            default -> List.of(1, 2, 3, 4, 5, 6, 7);
        };
    }

    public static String dayName(Integer weekday) {
        return weekday == null || weekday < 1 || weekday > 7 ? "" : DAY_NAMES[weekday];
    }

    /** Azar estable por usuario y "sal" (p. ej. clave del mundo + semana). */
    public static Random stableRandom(UUID userId, String salt) {
        return new Random(userId.getMostSignificantBits() ^ userId.getLeastSignificantBits() ^ salt.hashCode());
    }

    public Set<String> liked(UUID userId, String worldKey) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT movement_key FROM movement_feedback WHERE user_id = ? AND world_key = ? AND reaction = 'like'
                """, String.class, userId, worldKey));
    }

    public Set<String> disliked(UUID userId, String worldKey) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT movement_key FROM movement_feedback WHERE user_id = ? AND world_key = ?
                  AND reaction IN ('dislike','swap') AND created_at > now() - interval '60 days'
                """, String.class, userId, worldKey));
    }

    /**
     * Elige el mejor candidato que no esté en {@code used}, según {@code score} (mayor es mejor) más un
     * pequeño desempate aleatorio estable. Devuelve null si no hay candidatos.
     */
    public static WorldMovement pick(List<WorldMovement> candidates, Set<String> used,
            java.util.function.ToDoubleFunction<WorldMovement> score, Random rnd) {
        List<WorldMovement> pool = new ArrayList<>(candidates.stream().filter(m -> !used.contains(m.key())).toList());
        if (pool.isEmpty()) return null;
        java.util.Map<String, Double> jitter = new java.util.HashMap<>();
        for (WorldMovement m : pool) jitter.put(m.key(), rnd.nextDouble() * 0.5);
        pool.sort(Comparator.comparingDouble((WorldMovement m) -> score.applyAsDouble(m) + jitter.get(m.key())).reversed());
        return pool.get(0);
    }
}
