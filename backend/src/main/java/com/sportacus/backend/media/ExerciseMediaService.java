package com.sportacus.backend.media;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enriquecimiento de ejercicios con imágenes, video y pasos detallados desde la API libre de
 * wger (https://wger.de/api/v2, sin clave). Al arrancar descarga en segundo plano el catálogo en
 * español (~940 ejercicios, ~10 peticiones) y lo indexa en memoria; luego empareja cada ejercicio
 * del catálogo propio por nombre normalizado. Tolerante a fallos: sin internet o ante cualquier
 * error devuelve el material que tenga (como mínimo las imágenes de músculos si se alcanzaron)
 * y reintenta más tarde. Ninguna llamada espera más de {@link #MAX_WAIT_MS}.
 */
@Service
public class ExerciseMediaService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseMediaService.class);

    private static final String BASE = "https://wger.de/api/v2/";
    private static final int SPANISH = 4;
    private static final long MAX_WAIT_MS = 2500;
    private static final Duration INDEX_TTL = Duration.ofHours(24);
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);
    private static final Duration MEDIA_TTL = Duration.ofHours(12);
    private static final Duration SHORT_TTL = Duration.ofSeconds(30);
    private static final double MATCH_THRESHOLD = 0.6;

    private static final Set<String> STOPWORDS = Set.of(
            "de", "del", "la", "el", "los", "las", "con", "en", "a", "al", "y", "o", "un", "una", "sobre",
            "para", "por", "desde", "hacia", "tu", "su");

    /** Grupo muscular del catálogo propio → ids de músculo de wger (fallback de imágenes). */
    private static final Map<String, List<Integer>> MUSCLE_FALLBACK = Map.ofEntries(
            Map.entry("pecho", List.of(4)),
            Map.entry("espalda", List.of(12, 9)),
            Map.entry("hombros", List.of(2)),
            Map.entry("biceps", List.of(1, 13)),
            Map.entry("triceps", List.of(5)),
            Map.entry("core", List.of(6, 14)),
            Map.entry("gluteos", List.of(8)),
            Map.entry("cuadriceps", List.of(10)),
            Map.entry("isquiotibiales", List.of(11)),
            Map.entry("pantorrillas", List.of(7, 15)),
            Map.entry("gemelos", List.of(7, 15)),
            Map.entry("hombro", List.of(2)),
            Map.entry("abdomen", List.of(6, 14)),
            Map.entry("antebrazos", List.of(13)),
            Map.entry("dorsales", List.of(12)));

    private static final String BODY_FRONT = "https://wger.de/static/images/muscles/muscular_system_front.svg";
    private static final String BODY_BACK = "https://wger.de/static/images/muscles/muscular_system_back.svg";

    private static final Pattern LI = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private record Muscle(int id, boolean front, String mainUrl, String secondaryUrl) {
    }

    private record Entry(int id, String name, Set<String> stems, String firstStem, String descriptionHtml,
            List<String> imageUrls, String thumbUrl, String videoUrl, List<Integer> muscles,
            List<Integer> musclesSecondary, List<String> equipment) {
    }

    private record Index(List<Entry> entries, Map<Integer, Muscle> muscles) {
    }

    private record Cached(ExerciseMedia media, long expiresAt) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private volatile CompletableFuture<Index> loading;
    private volatile Index index;
    private volatile long indexLoadedAt;
    private volatile long lastFailureAt;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        startLoadIfNeeded();
    }

    /** Material de un ejercicio del catálogo propio. Nunca lanza ni bloquea más de ~2.5 s. */
    public ExerciseMedia lookup(String exerciseName, String muscleGroupName, List<String> secondaryGroupNames) {
        if (exerciseName == null || exerciseName.isBlank()) {
            return ExerciseMedia.empty();
        }
        String cacheKey = normalize(exerciseName) + "|" + normalize(muscleGroupName == null ? "" : muscleGroupName);
        Cached cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return cached.media();
        }

        try {
            Index idx = awaitIndex();
            if (idx == null) {
                ExerciseMedia fallback = ExerciseMedia.empty();
                cache.put(cacheKey, new Cached(fallback, now + SHORT_TTL.toMillis()));
                return fallback;
            }
            ExerciseMedia media = build(idx, exerciseName, muscleGroupName);
            cache.put(cacheKey, new Cached(media, now + MEDIA_TTL.toMillis()));
            return media;
        } catch (Exception e) {
            log.debug("Sin material wger para '{}': {}", exerciseName, e.toString());
            return ExerciseMedia.empty();
        }
    }

    /**
     * Diagrama muscular (silueta de wger con los músculos resaltados) para cualquier movimiento a partir de sus
     * músculos del catálogo propio. Sirve cuando no hay foto. Vacío si wger no responde o ningún músculo se reconoce.
     */
    public List<ExerciseMedia.MuscleView> muscleViewsFor(List<String> muscles) {
        if (muscles == null || muscles.isEmpty()) return List.of();
        try {
            Index idx = awaitIndex();
            if (idx == null) return List.of();
            List<Muscle> primary = new ArrayList<>();
            List<Muscle> secondary = new ArrayList<>();
            for (int i = 0; i < muscles.size(); i++) {
                List<Muscle> found = fallbackMuscles(idx, muscles.get(i));
                (i < 2 ? primary : secondary).addAll(found);
            }
            return muscleViews(primary, secondary);
        } catch (Exception e) {
            log.debug("Sin diagrama muscular: {}", e.toString());
            return List.of();
        }
    }

    // ───────────────────────── Emparejamiento ─────────────────────────

    private ExerciseMedia build(Index idx, String exerciseName, String muscleGroupName) {
        Set<String> stems = stems(exerciseName);
        String firstStem = firstStem(exerciseName);
        Entry best = null;
        double bestScore = 0;
        for (Entry entry : idx.entries()) {
            double score = similarity(stems, firstStem, entry);
            if (!entry.imageUrls().isEmpty()) {
                score += 0.03;
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }

        if (best != null && bestScore >= MATCH_THRESHOLD) {
            List<Muscle> primary = musclesOf(idx, best.muscles());
            List<Muscle> secondary = musclesOf(idx, best.musclesSecondary());
            if (primary.isEmpty() && secondary.isEmpty()) {
                primary = fallbackMuscles(idx, muscleGroupName);
            }
            Text text = describe(best.descriptionHtml());
            return new ExerciseMedia(best.imageUrls(), best.thumbUrl(), best.videoUrl(),
                    overlayUrls(primary, secondary), muscleViews(primary, secondary),
                    text.steps(), text.description(),
                    best.equipment().isEmpty() ? null : String.join(", ", best.equipment()), "wger.de");
        }

        List<Muscle> fallback = fallbackMuscles(idx, muscleGroupName);
        return new ExerciseMedia(List.of(), null, null, overlayUrls(fallback, List.of()),
                muscleViews(fallback, List.of()), List.of(), null, null,
                fallback.isEmpty() ? null : "wger.de");
    }

    private List<Muscle> musclesOf(Index idx, List<Integer> ids) {
        List<Muscle> muscles = new ArrayList<>();
        for (Integer id : ids) {
            Muscle m = idx.muscles().get(id);
            if (m != null) muscles.add(m);
        }
        return muscles;
    }

    private List<Muscle> fallbackMuscles(Index idx, String muscleGroupName) {
        List<Integer> ids = MUSCLE_FALLBACK.get(normalize(muscleGroupName == null ? "" : muscleGroupName));
        return ids == null ? List.of() : musclesOf(idx, ids);
    }

    private List<String> overlayUrls(List<Muscle> primary, List<Muscle> secondary) {
        List<String> urls = new ArrayList<>();
        primary.forEach(m -> { if (m.mainUrl() != null) urls.add(m.mainUrl()); });
        secondary.forEach(m -> { if (m.secondaryUrl() != null) urls.add(m.secondaryUrl()); });
        return urls;
    }

    /** Las imágenes de wger son capas transparentes: se agrupan por vista (frente/espalda) sobre la silueta. */
    private List<ExerciseMedia.MuscleView> muscleViews(List<Muscle> primary, List<Muscle> secondary) {
        List<String> front = new ArrayList<>();
        List<String> back = new ArrayList<>();
        primary.forEach(m -> addOverlay(m.front() ? front : back, m.mainUrl()));
        secondary.forEach(m -> addOverlay(m.front() ? front : back, m.secondaryUrl()));
        List<ExerciseMedia.MuscleView> views = new ArrayList<>();
        if (!front.isEmpty()) views.add(new ExerciseMedia.MuscleView(BODY_FRONT, front));
        if (!back.isEmpty()) views.add(new ExerciseMedia.MuscleView(BODY_BACK, back));
        return views;
    }

    private void addOverlay(List<String> target, String url) {
        if (url != null && !target.contains(url)) target.add(url);
    }

    private double similarity(Set<String> stems, String firstStem, Entry entry) {
        if (stems.isEmpty() || entry.stems().isEmpty() || firstStem == null || !entry.stems().contains(firstStem)) {
            return 0;
        }
        int inter = 0;
        for (String s : stems) {
            if (entry.stems().contains(s)) inter++;
        }
        double byMax = inter / (double) Math.max(stems.size(), entry.stems().size());
        double byMin = inter / (double) Math.min(stems.size(), entry.stems().size());
        return 0.6 * byMax + 0.4 * byMin;
    }

    // ───────────────────────── Descarga del catálogo ─────────────────────────

    private Index awaitIndex() throws Exception {
        long now = System.currentTimeMillis();
        Index current = index;
        if (current != null && now - indexLoadedAt < INDEX_TTL.toMillis()) {
            return current;
        }
        CompletableFuture<Index> future = startLoadIfNeeded();
        if (future == null) {
            return current; // en espera tras un fallo: se usa lo que haya (puede ser null)
        }
        try {
            Index loaded = future.get(MAX_WAIT_MS, TimeUnit.MILLISECONDS);
            return loaded != null ? loaded : current;
        } catch (java.util.concurrent.TimeoutException timeout) {
            return current;
        }
    }

    private synchronized CompletableFuture<Index> startLoadIfNeeded() {
        long now = System.currentTimeMillis();
        if (loading != null && !loading.isDone()) {
            return loading;
        }
        boolean fresh = index != null && now - indexLoadedAt < INDEX_TTL.toMillis();
        if (fresh) {
            return null;
        }
        if (lastFailureAt > 0 && now - lastFailureAt < RETRY_AFTER_FAILURE.toMillis()) {
            return null;
        }
        loading = CompletableFuture.supplyAsync(this::fetchAll);
        return loading;
    }

    private Index fetchAll() {
        try {
            Map<Integer, Muscle> muscles = fetchMuscles();
            List<Entry> entries = new ArrayList<>();
            String url = BASE + "exerciseinfo/?language=" + SPANISH + "&limit=100&offset=0";
            int guard = 0;
            while (url != null && guard++ < 30) {
                JsonNode page = getJson(url, Duration.ofSeconds(25));
                for (JsonNode item : page.path("results")) {
                    Entry entry = toEntry(item);
                    if (entry != null) entries.add(entry);
                }
                JsonNode next = page.path("next");
                url = next.isString() ? next.asString() : null;
            }
            Index loaded = new Index(List.copyOf(entries), muscles);
            index = loaded;
            indexLoadedAt = System.currentTimeMillis();
            lastFailureAt = 0;
            log.info("Catálogo wger cargado: {} ejercicios en español, {} músculos", entries.size(), muscles.size());
            return loaded;
        } catch (Exception e) {
            lastFailureAt = System.currentTimeMillis();
            log.warn("No se pudo cargar el catálogo de wger (se reintentará): {}", e.toString());
            return null;
        }
    }

    private Map<Integer, Muscle> fetchMuscles() {
        Map<Integer, Muscle> muscles = new HashMap<>();
        JsonNode page = getJson(BASE + "muscle/?limit=100", Duration.ofSeconds(10));
        for (JsonNode m : page.path("results")) {
            int id = m.path("id").asInt();
            muscles.put(id, new Muscle(id, m.path("is_front").asBoolean(true), textOrNull(m.path("image_url_main")), textOrNull(m.path("image_url_secondary"))));
        }
        return muscles;
    }

    private Entry toEntry(JsonNode item) {
        JsonNode spanish = null;
        for (JsonNode t : item.path("translations")) {
            if (t.path("language").asInt() == SPANISH && !t.path("name").asString("").isBlank()) {
                spanish = t;
                break;
            }
        }
        if (spanish == null) {
            return null;
        }
        String name = spanish.path("name").asString("");
        List<String> images = new ArrayList<>();
        String thumb = null;
        for (JsonNode img : item.path("images")) {
            String full = textOrNull(img.path("image"));
            if (full == null) continue;
            boolean main = img.path("is_main").asBoolean(false);
            if (main) {
                images.add(0, full);
                String medium = textOrNull(img.path("thumbnails").path("medium"));
                thumb = medium != null ? medium : full;
            } else {
                images.add(full);
            }
        }
        if (thumb == null && !images.isEmpty()) {
            thumb = images.get(0);
        }
        String video = null;
        for (JsonNode v : item.path("videos")) {
            video = textOrNull(v.path("video"));
            if (video != null) break;
        }
        List<Integer> muscles = new ArrayList<>();
        item.path("muscles").forEach(m -> muscles.add(m.path("id").asInt()));
        List<Integer> secondary = new ArrayList<>();
        item.path("muscles_secondary").forEach(m -> secondary.add(m.path("id").asInt()));
        List<String> equipment = new ArrayList<>();
        item.path("equipment").forEach(e -> {
            String eqName = e.path("name").asString("").trim();
            if (!eqName.isEmpty()) equipment.add(eqName);
        });

        return new Entry(item.path("id").asInt(), name, stems(name), firstStem(name),
                spanish.path("description").asString(""), List.copyOf(images), thumb, video,
                muscles, secondary, equipment);
    }

    private JsonNode getJson(String url, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("wger respondió " + response.statusCode() + " en " + url);
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String textOrNull(JsonNode node) {
        if (node == null || !node.isString()) return null;
        String value = node.asString();
        return value.isBlank() ? null : value;
    }

    // ───────────────────────── Texto ─────────────────────────

    static String normalize(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    static Set<String> stems(String name) {
        Set<String> stems = new LinkedHashSet<>();
        for (String token : normalize(name).split(" ")) {
            if (token.isBlank() || STOPWORDS.contains(token)) continue;
            stems.add(token.length() > 5 ? token.substring(0, 5) : token);
        }
        return stems;
    }

    static String firstStem(String name) {
        Set<String> stems = stems(name);
        return stems.isEmpty() ? null : stems.iterator().next();
    }

    /** Descripción corta y pasos de ejecución extraídos de la descripción HTML de wger. */
    record Text(String description, List<String> steps) {
    }

    private static final Pattern BLOCK_END = Pattern.compile(
            "</(p|li|div|h[1-6]|ul|ol)>|<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    /** Líneas de metadatos que wger mezcla con el texto: "Músculos implicados: bíceps", "Equipo: TRX"... */
    private static final Pattern META_LINE = Pattern.compile(
            "^(m[uú]sculos?( implicados?| trabajados?| principales?)?|equipo|material|equipamiento)\\s*:.*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Separa la descripción en bloques (párrafos / viñetas) para no pegar frases ajenas, descarta
     * metadatos y notas editoriales entre paréntesis, y reparte: primera frase → descripción,
     * resto → pasos. Si ya venía como lista de viñetas, todo son pasos.
     */
    static Text describe(String html) {
        List<String> blocks = new ArrayList<>();
        if (html != null) {
            for (String chunk : BLOCK_END.split(html)) {
                String text = plainText(chunk);
                if (text == null) continue;
                if (META_LINE.matcher(text).matches()) continue;
                if (text.startsWith("(") && text.endsWith(")")) continue;
                if (isHeading(text)) continue;
                if (text.length() > 3) blocks.add(text);
            }
        }
        if (blocks.isEmpty()) {
            return new Text(null, List.of());
        }
        boolean listed = LI.matcher(html).find();
        List<String> steps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (listed || blocks.size() > 1) {
            blocks.forEach(b -> { if (seen.add(b)) steps.add(b); });
        } else {
            for (String sentence : blocks.get(0).split("(?<=[.!?])\\s+")) {
                String trimmed = sentence.trim();
                if (trimmed.length() > 3 && seen.add(trimmed)) steps.add(trimmed);
                if (steps.size() >= 8) break;
            }
        }
        if (listed || steps.size() < 2) {
            return new Text(null, steps);
        }
        return new Text(steps.get(0), steps.subList(1, steps.size()));
    }

    /** Encabezados sueltos como "Instrucciones" o "Ejecución": una o dos palabras, sin puntuación final. */
    static boolean isHeading(String text) {
        return text.length() <= 25 && text.split(" ").length <= 2 && !text.matches(".*[.:;!?)]$");
    }

    static List<String> steps(String html) {
        return describe(html).steps();
    }

    static String plainText(String html) {
        if (html == null) return null;
        String text = TAGS.matcher(html).replaceAll(" ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        text = text.replaceAll("\\s+", " ").trim();
        return text.isEmpty() ? null : text;
    }
}
