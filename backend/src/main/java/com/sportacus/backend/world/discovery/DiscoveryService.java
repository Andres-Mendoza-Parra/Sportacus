package com.sportacus.backend.world.discovery;

import com.sportacus.backend.world.form.Forms;
import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.single;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.common.Json;
import com.sportacus.backend.world.catalog.CatalogModels.WorldInfo;
import com.sportacus.backend.world.catalog.CatalogRepository;
import com.sportacus.backend.world.core.DiscoveryInput;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.WorldModule;
import com.sportacus.backend.world.core.WorldRegistry;
import com.sportacus.backend.world.form.FormSpec;

/**
 * "Ayúdame a elegir": test corto de gustos y motivaciones. Cada mundo aporta sus propios factores
 * explicables (base 40, recorte 0–100) y se devuelve un ranking con el porqué de cada posición.
 */
@Service
public class DiscoveryService {

    public static final double BASE_SCORE = 40;

    private final WorldRegistry registry;
    private final CatalogRepository catalog;
    private final JdbcTemplate jdbc;
    private final Json json;

    public DiscoveryService(WorldRegistry registry, CatalogRepository catalog, JdbcTemplate jdbc, Json json) {
        this.registry = registry;
        this.catalog = catalog;
        this.jdbc = jdbc;
        this.json = json;
    }

    public record RankedWorld(String worldKey, String name, String icon, String color, String tagline, int score,
            List<String> reasons, List<String> cautions, List<Factor> factors) {
    }

    public record DiscoveryResult(List<RankedWorld> ranking, List<String> notices) {
    }

    public FormSpec form() {
        return Forms.form("descubrir", "Encontremos tu mundo", "No hay respuestas correctas: elige lo que más te represente.",
                multi("d_motivation", "¿Qué te gustaría lograr?", "Elige hasta 2.", 2,
                        opt("fuerza_fisico", "💪", "Verme y sentirme más fuerte"),
                        opt("habilidades", "🤸", "Dominar mi cuerpo y aprender habilidades"),
                        opt("resistencia", "🏃", "Ganar resistencia y despejarme al aire libre"),
                        opt("salud", "❤️‍🩹", "Recuperarme de una lesión o cuidar mi salud"),
                        opt("equipo", "⚽", "Jugar, competir y compartir con otros"),
                        opt("calma", "🧘", "Calma, flexibilidad y bienestar")),
                multi("d_enjoy", "¿Qué tipo de actividad disfrutas o te llama la atención?", "Elige las que quieras.",
                        opt("pesas", "🏋️", "Levantar pesas y máquinas"),
                        opt("peso_corporal", "🤸", "Ejercicios con mi propio peso"),
                        opt("correr", "👟", "Correr o caminar rápido"),
                        opt("balon", "⚽", "Deportes con balón"),
                        opt("suave", "🌿", "Movimientos suaves y controlados"),
                        opt("aire_libre", "🌳", "Estar al aire libre")),
                single("d_social", "¿Cómo prefieres entrenar?", null,
                        opt("solo", "🎧", "Solo, a mi ritmo"),
                        opt("acompanado", "👯", "Con alguien o en grupo pequeño"),
                        opt("equipo", "👥", "En equipo")),
                single("d_intensity", "¿Qué intensidad te apetece?", null,
                        opt("suave", "🌱", "Suave", "Moverme sin terminar agotado"),
                        opt("moderada", "🌤️", "Moderada", "Sudar, pero con control"),
                        opt("intensa", "🔥", "Intensa", "Me gusta exigirme al máximo")),
                single("d_motivator", "¿Qué te motiva a seguir?", null,
                        opt("numeros", "📈", "Ver números: cargas, tiempos, marcas"),
                        opt("retos", "🏆", "Superar retos y desbloquear habilidades"),
                        opt("sensaciones", "😌", "Sentirme mejor y con menos molestias"),
                        opt("juego", "🎯", "El juego y la competencia")),
                multi("d_experience", "¿Qué has practicado antes?", "Aunque haya sido hace tiempo.",
                        opt("gimnasio", "🏋️", "Gimnasio"), opt("calistenia", "🤸", "Calistenia"),
                        opt("running", "🏃", "Running"), opt("futbol", "⚽", "Fútbol"),
                        opt("yoga", "🧘", "Yoga o pilates"), opt("rehabilitacion", "🩺", "Fisioterapia o rehabilitación"),
                        opt("ninguno", "🌱", "Nada todavía")));
    }

    public DiscoveryResult discover(UserContext user, Answers answers) {
        List<RankedWorld> ranking = rank(user, answers);
        List<String> notices = new ArrayList<>();
        if (user.health().mustRefer()) {
            notices.add("Marcaste señales que un profesional debe revisar: empieza por Salud y Rehabilitación y consulta antes de hacer actividad intensa.");
        }
        jdbc.update("INSERT INTO world_discovery (user_id, answers, ranking) VALUES (?, ?::jsonb, ?::jsonb)",
                user.userId(), json.write(answers.asMap()), json.write(ranking));
        return new DiscoveryResult(ranking, notices);
    }

    /**
     * Ranking sin guardar nada. Con respuestas vacías sirve para las sugerencias de Mi Base: entonces pesan solo el
     * perfil común, la salud y el Perfil 360.
     */
    public List<RankedWorld> rank(UserContext user, Answers answers) {
        DiscoveryInput input = new DiscoveryInput(answers, user);
        List<RankedWorld> ranking = new ArrayList<>();
        for (WorldInfo w : catalog.worlds()) {
            if (!w.selectable()) continue;
            WorldModule module = registry.find(w.key()).orElse(null);
            if (module == null) continue;
            List<Factor> factors = new ArrayList<>(module.discoveryFactors(input));
            factors.addAll(ProfileDiscoveryFactors.of(w.key(), user));
            double total = BASE_SCORE + factors.stream().mapToDouble(Factor::points).sum();
            int score = (int) Math.round(Math.max(0, Math.min(100, total)));
            List<String> reasons = factors.stream().filter(Factor::positive)
                    .sorted(Comparator.comparingDouble(Factor::points).reversed()).limit(3).map(Factor::text).toList();
            List<String> cautions = factors.stream().filter(f -> f.points() < 0)
                    .sorted(Comparator.comparingDouble(Factor::points)).limit(2).map(Factor::text).toList();
            ranking.add(new RankedWorld(w.key(), w.name(), w.icon(), w.color(), w.tagline(), score, reasons, cautions,
                    factors));
        }
        ranking.sort(Comparator.comparingInt(RankedWorld::score).reversed());
        return ranking;
    }

    public void markChosen(java.util.UUID userId, String worldKey) {
        jdbc.update("""
                UPDATE world_discovery SET chosen_world_key = ?
                WHERE id = (SELECT id FROM world_discovery WHERE user_id = ? ORDER BY created_at DESC LIMIT 1)
                """, worldKey, userId);
    }
}
