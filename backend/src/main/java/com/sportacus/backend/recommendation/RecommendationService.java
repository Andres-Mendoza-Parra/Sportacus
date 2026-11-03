package com.sportacus.backend.recommendation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sportacus.backend.media.ExerciseEnrichmentService;
import com.sportacus.backend.media.ExerciseMedia;
import com.sportacus.backend.media.ExerciseMediaService;
import com.sportacus.backend.questionnaire.QuestionnaireService;
import com.sportacus.backend.recommendation.RecommendationRepository.ActivityRow;
import com.sportacus.backend.recommendation.RecommendationRepository.ExerciseDetailRow;
import com.sportacus.backend.recommendation.RecommendationRepository.ExerciseRow;
import com.sportacus.backend.recommendation.RecommendationRepository.ExistingRoute;
import com.sportacus.backend.recommendation.RecommendationRepository.Feedback;
import com.sportacus.backend.recommendation.RecommendationRepository.RecommendationRef;
import com.sportacus.backend.recommendation.RecommendationRepository.RecommendedExerciseRow;
import com.sportacus.backend.recommendation.RecommendationRepository.UserContext;
import com.sportacus.backend.recommendation.dto.ExerciseDetailResponse;
import com.sportacus.backend.recommendation.dto.LibraryDtos.LibraryExercise;
import com.sportacus.backend.recommendation.dto.LibraryDtos.LibraryFilters;
import com.sportacus.backend.recommendation.dto.RouteOptionResponse;
import com.sportacus.backend.recommendation.dto.ScoreFactorResponse;
import com.sportacus.backend.recommendation.dto.RoutineDayResponse;
import com.sportacus.backend.recommendation.dto.RoutineExerciseResponse;
import com.sportacus.backend.recommendation.dto.RoutineRequests.DoneResponse;
import com.sportacus.backend.recommendation.dto.RoutineResponse;

/**
 * Motor de recomendación v2, basado en reglas (no IA) y dinámico: puntúa cada actividad del
 * catálogo con todo lo que se sabe del usuario (objetivo, entorno, equipamiento, experiencia,
 * edad e IMC, lesiones y condiciones, sueño y estrés, tiempo disponible, disciplinas que ya
 * practica) y con su retroalimentación previa (rutas descartadas, ejercicios con like/dislike).
 *
 * La ruta ya no se recalcula "una vez al día": se conserva hasta que algo la invalida (guardar
 * cualquier cuestionario) o el usuario pide regenerarla, cambiar de disciplina o sustituir un
 * ejercicio. Cada puntuación se descompone en factores que alimentan el texto de justificación.
 */
@Service
public class RecommendationService {

    private static final List<String> WEEK = List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");
    private static final int MAX_OPTIONS = 6;
    private static final List<String> TIME_BASED_CATEGORIES = List.of("Flexibilidad", "Mente-Cuerpo", "Rehabilitación");

    private final RecommendationRepository repository;
    private final QuestionnaireService questionnaireService;
    private final ExerciseMediaService mediaService;
    private final ExerciseEnrichmentService enrichmentService;

    public RecommendationService(RecommendationRepository repository, QuestionnaireService questionnaireService,
            ExerciseMediaService mediaService, ExerciseEnrichmentService enrichmentService) {
        this.repository = repository;
        this.questionnaireService = questionnaireService;
        this.mediaService = mediaService;
        this.enrichmentService = enrichmentService;
    }

    // ───────────────────────── API pública ─────────────────────────

    public RoutineResponse getRoutine(UUID userId) {
        ExistingRoute route = currentOrGenerate(userId);
        return buildResponse(userId, route);
    }

    /** Disciplinas candidatas ordenadas por afinidad (la actual va marcada como seleccionada). */
    public List<RouteOptionResponse> getOptions(UUID userId) {
        ExistingRoute current = currentOrGenerate(userId);
        UserContext ctx = repository.loadUserContext(userId);
        Feedback feedback = repository.loadFeedback(userId);
        int strength = questionnaireService.profileStrengthPct(userId);

        return rank(ctx, feedback).stream()
                .limit(MAX_OPTIONS)
                .map(s -> new RouteOptionResponse(s.activity().id().toString(), s.activity().name(),
                        s.activity().description(), s.score(), rationale(ctx, s, strength),
                        intensityLabel(s.activity().intensityLevel()), s.activity().typicalSessionMin(),
                        environmentLabel(s.activity().environment()), s.activity().id().equals(current.activityId())))
                .toList();
    }

    public RoutineResponse selectActivity(UUID userId, UUID activityId) {
        boolean exists = repository.listActiveActivities().stream().anyMatch(a -> a.id().equals(activityId));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La disciplina no existe o no está disponible");
        }
        return buildResponse(userId, generate(userId, activityId, null, false));
    }

    /** mode "refresh": misma disciplina con otra selección de ejercicios; "another": la siguiente mejor disciplina. */
    public RoutineResponse regenerate(UUID userId, String mode) {
        ExistingRoute current = currentOrGenerate(userId);
        if ("another".equalsIgnoreCase(mode)) {
            repository.addDiscard(userId, current.activityId());
            return buildResponse(userId, generate(userId, null, current.activityId(), false));
        }
        if (mode == null || "refresh".equalsIgnoreCase(mode)) {
            return buildResponse(userId, generate(userId, current.activityId(), null, true));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo no válido: usa \"refresh\" o \"another\"");
    }

    /** Sustituye un ejercicio por otro similar (mismo grupo muscular o patrón) que aún no esté en la rutina. */
    public RoutineResponse swapExercise(UUID userId, UUID exerciseRecommendationId) {
        RecommendationRef ref = repository.findRecommendationRef(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        UserContext ctx = repository.loadUserContext(userId);
        Feedback feedback = repository.loadFeedback(userId);
        Set<UUID> inRoute = repository.routeExerciseIds(ref.routeId());

        int cap = difficultyCap(ctx);
        ExerciseRow best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ExerciseRow candidate : repository.listSwapCandidates(ref.categoryName(), ref.muscleGroupId(),
                ref.movementPattern())) {
            if (inRoute.contains(candidate.id()) || feedback.disliked().contains(candidate.id())) {
                continue;
            }
            if (candidate.difficultyLevel() != null && candidate.difficultyLevel() > cap) {
                continue;
            }
            int score = exerciseScore(candidate, ctx, feedback);
            if (candidate.activityId().equals(ref.activityId())) {
                score += 10; // misma disciplina: coherencia de la rutina
            }
            if (candidate.muscleGroupId() != null && candidate.muscleGroupId().equals(ref.muscleGroupId())) {
                score += 6;
            }
            if (isContraindicated(candidate, ctx)) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No hay un ejercicio similar disponible para sustituir este");
        }

        repository.addSwap(userId, ref.exerciseId());
        repository.replaceExercise(exerciseRecommendationId, best.id(), clampScore(bestScore));
        repository.clearDone(userId, exerciseRecommendationId); // ejercicio nuevo = pendiente
        return getRoutine(userId);
    }

    public void setFeedback(UUID userId, UUID exerciseRecommendationId, String reaction) {
        if (!"like".equals(reaction) && !"dislike".equals(reaction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reaction debe ser \"like\" o \"dislike\"");
        }
        RecommendationRef ref = repository.findRecommendationRef(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        repository.setReaction(userId, ref.exerciseId(), reaction);
    }

    public ExerciseDetailResponse getExerciseDetail(UUID userId, UUID exerciseRecommendationId) {
        ExerciseDetailRow row = repository.findExerciseDetail(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));

        boolean logged = repository.isExerciseLogged(exerciseRecommendationId);
        return buildDetail(userId, row, row.exerciseRecommendationId().toString(), logged,
                repository.doneRecommendationIds(userId).contains(exerciseRecommendationId));
    }

    /** Arma el detalle de un ejercicio (de la rutina o de la biblioteca) con el material de wger. */
    private ExerciseDetailResponse buildDetail(UUID userId, ExerciseDetailRow row, String idForClient,
            boolean logged, boolean done) {
        List<String> steps = splitSentences(row.coachingCues());
        List<String> musclesPrimary = row.muscleGroupPrimaryName() == null
                ? List.of() : List.of(row.muscleGroupPrimaryName());
        List<String> musclesSecondary = repository.muscleGroupNames(row.musclesSecondaryIds());

        ExerciseMedia media = mediaService.lookup(row.name(), row.muscleGroupPrimaryName(), musclesSecondary);
        List<String> instructions = media.instructions().isEmpty() ? steps : media.instructions();
        String baseDescription = row.description() != null && !row.description().isBlank()
                ? row.description() : media.description();
        String baseEquipment = blankToNull(row.equipmentNeeded()) != null ? row.equipmentNeeded() : media.equipment();

        // IA (Gemini): pule la descripción/pasos/equipo; se genera una vez por ejercicio y se guarda en BD.
        ExerciseEnrichmentService.Enrichment ai = enrichmentService.lookup(row.exerciseId(), row.name(),
                row.muscleGroupPrimaryName(), baseEquipment,
                String.join(" ", baseDescription == null ? "" : baseDescription, String.join(" ", instructions)));
        if (!ai.steps().isEmpty()) {
            instructions = ai.steps();
        }
        String videoUrl = media.videoUrl() != null ? media.videoUrl() : blankToNull(row.videoUrl());
        String equipment = ai.equipment() != null ? ai.equipment() : baseEquipment;
        String description = ai.description() != null ? ai.description() : baseDescription;
        instructions = withoutDescription(instructions, description);
        List<String> tips = new ArrayList<>(tips(row));
        ai.commonMistakes().forEach(m -> tips.add("Error común: " + m));

        return new ExerciseDetailResponse(
                idForClient, row.name(), description,
                environmentLabel(row.environment()), difficultyLabel(row.difficultyLevel()),
                row.muscleGroupPrimaryName(), detailLabel(row.sets(), row.reps(), row.durationSec()),
                steps, musclesPrimary, musclesSecondary, row.contraindications(), logged,
                media.imageUrls(), videoUrl, media.muscleImageUrls(), media.muscleViews(), instructions, tips,
                equipment, media.source(), repository.reactionOf(userId, row.exerciseId()), done);
    }

    // ───────────────────────── Biblioteca de ejercicios ─────────────────────────

    public List<LibraryExercise> searchLibrary(UUID userId, String query, String muscle, String equipment,
            Integer difficulty, boolean favoritesOnly, int limit, int offset) {
        UUID activityId = currentOrGenerate(userId).activityId();
        int safeLimit = Math.max(1, Math.min(60, limit));
        return repository.searchExercises(userId, activityId, query, muscle, equipment, difficulty, favoritesOnly,
                safeLimit, Math.max(0, offset))
                .stream().map(r -> {
                    ExerciseMedia media = mediaService.lookup(r.name(), r.muscleGroup(), List.of());
                    String image = media.thumbUrl() != null ? media.thumbUrl()
                            : (media.imageUrls().isEmpty() ? null : media.imageUrls().get(0));
                    return new LibraryExercise(r.id().toString(), r.name(),
                            r.muscleGroup() == null ? "General" : r.muscleGroup(), blankToNull(r.equipment()),
                            difficultyLabel(r.difficulty() == null ? null : String.valueOf(r.difficulty())),
                            environmentLabel(r.environment()), exerciseIcon(r.muscleGroup()), image, r.favorite(),
                            TIME_BASED_CATEGORIES.contains(r.category()));
                }).toList();
    }

    public void addFavorite(UUID userId, UUID exerciseId) {
        repository.addFavorite(userId, exerciseId);
    }

    public void removeFavorite(UUID userId, UUID exerciseId) {
        repository.removeFavorite(userId, exerciseId);
    }

    public LibraryFilters libraryFilters(UUID userId) {
        ExistingRoute route = currentOrGenerate(userId);
        return new LibraryFilters(repository.listMuscleNamesInUse(route.activityId()),
                List.of("Principiante", "Básico", "Intermedio", "Avanzado", "Experto"), route.activityName());
    }

    public ExerciseDetailResponse getLibraryDetail(UUID userId, UUID exerciseId) {
        ExerciseDetailRow row = repository.findExerciseDetailByExerciseId(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        return buildDetail(userId, row, exerciseId.toString(), false, false);
    }

    /** El usuario elige un ejercicio de la biblioteca y lo suma a su ruta vigente (día balanceado). */
    public RoutineResponse addToRoutine(UUID userId, UUID exerciseId) {
        ExerciseDetailRow row = repository.findExerciseDetailByExerciseId(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        ExistingRoute route = currentOrGenerate(userId);
        if (repository.routeContainsExercise(route.routeId(), exerciseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este ejercicio ya está en tu rutina");
        }
        boolean timed = TIME_BASED_CATEGORIES.contains(row.categoryName());
        int dayIndex = repository.countRouteExercises(route.routeId()) % route.dayLabels().size();
        repository.appendExerciseToRoute(route.routeId(), exerciseId, 80, 3, timed ? null : 10,
                timed ? 45 : null, 60, dayIndex);
        return buildResponse(userId, route);
    }

    // ───────────────────────── Taller de rutina ─────────────────────────

    private static final int MAX_DAYS = 14;

    public RoutineResponse addDay(UUID userId, String label) {
        if (label == null || label.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre del día es obligatorio");
        }
        ExistingRoute route = currentOrGenerate(userId);
        if (route.dayLabels().size() >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya tienes el máximo de días permitido");
        }
        List<String> next = new ArrayList<>(route.dayLabels());
        next.add(label.trim());
        repository.updateDayLabels(route.routeId(), next);
        return getRoutine(userId);
    }

    public RoutineResponse removeDay(UUID userId, int dayIndex) {
        ExistingRoute route = currentOrGenerate(userId);
        if (route.dayLabels().size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Debe quedar al menos un día en tu rutina");
        }
        if (dayIndex < 0 || dayIndex >= route.dayLabels().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Día no válido");
        }
        List<String> next = new ArrayList<>(route.dayLabels());
        next.remove(dayIndex);
        repository.removeDayExercises(route.routeId(), dayIndex);
        repository.updateDayLabels(route.routeId(), next);
        return getRoutine(userId);
    }

    /** El usuario elige exactamente qué ejercicio, en qué día y con qué series/reps (o duración). */
    public RoutineResponse addExerciseToDay(UUID userId, int dayIndex, UUID exerciseId, Integer sets, Integer reps,
            Integer durationSec) {
        ExerciseDetailRow row = repository.findExerciseDetailByExerciseId(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        ExistingRoute route = currentOrGenerate(userId);
        if (dayIndex < 0 || dayIndex >= route.dayLabels().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Día no válido");
        }
        if (repository.routeContainsExercise(route.routeId(), exerciseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este ejercicio ya está en tu rutina");
        }
        boolean timed = TIME_BASED_CATEGORIES.contains(row.categoryName());
        int safeSets = sets == null ? 3 : Math.max(1, Math.min(10, sets));
        Integer safeReps = timed ? null : Math.max(1, Math.min(100, reps == null ? 10 : reps));
        Integer safeDuration = timed ? Math.max(5, Math.min(900, durationSec == null ? 45 : durationSec)) : null;
        repository.appendExerciseToRoute(route.routeId(), exerciseId, 80, safeSets, safeReps, safeDuration, 60,
                dayIndex);
        return buildResponse(userId, route);
    }

    /** Edita series/reps/duración y, opcionalmente, mueve el ejercicio a otro día. */
    public RoutineResponse updateExercise(UUID userId, UUID exerciseRecommendationId, Integer sets, Integer reps,
            Integer durationSec, Integer dayIndex) {
        RecommendationRef ref = repository.findRecommendationRef(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        ExistingRoute route = currentOrGenerate(userId);
        int targetDay = dayIndex != null ? dayIndex : ref.dayIndex();
        if (targetDay < 0 || targetDay >= route.dayLabels().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Día no válido");
        }
        repository.updateExerciseRecommendation(exerciseRecommendationId, sets, reps, durationSec, targetDay);
        return buildResponse(userId, route);
    }

    /** Quita un ejercicio de la rutina por completo (distinto de "swap", que lo sustituye). */
    public RoutineResponse removeExercise(UUID userId, UUID exerciseRecommendationId) {
        repository.findRecommendationRef(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        repository.clearDone(userId, exerciseRecommendationId);
        repository.deleteExerciseRecommendation(exerciseRecommendationId);
        return getRoutine(userId);
    }

    /** Marca o desmarca un ejercicio del checklist (lista de tareas; no crea sesiones de entrenamiento). */
    public DoneResponse setDone(UUID userId, UUID exerciseRecommendationId, boolean done) {
        RecommendationRef ref = repository.findRecommendationRef(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        repository.setDone(userId, ref.id(), done);
        ExistingRoute route = currentOrGenerate(userId);
        Set<UUID> doneIds = repository.doneRecommendationIds(userId);
        List<RecommendedExerciseRow> exercises = repository.listExerciseRecommendations(route.routeId());
        int doneCount = (int) exercises.stream().filter(e -> doneIds.contains(e.exerciseRecommendationId())).count();
        return new DoneResponse(done, doneCount, exercises.size());
    }

    public void logExercise(UUID userId, UUID exerciseRecommendationId) {
        ExerciseDetailRow row = repository.findExerciseDetail(userId, exerciseRecommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ejercicio no encontrado"));
        String sessionType = sessionTypeForCategory(row.categoryName());
        repository.logExercise(userId, exerciseRecommendationId, row.exerciseId(), sessionType,
                row.sets(), row.reps(), row.durationSec(), row.restSec());
    }

    // ───────────────────────── Generación de la ruta ─────────────────────────

    private ExistingRoute currentOrGenerate(UUID userId) {
        return repository.findCurrentRoute(userId).orElseGet(() -> generate(userId, null, null, false));
    }

    /**
     * Genera y persiste una ruta nueva.
     *
     * @param forcedActivity disciplina impuesta (select / refresh); null = la mejor puntuada
     * @param excludeActivity disciplina que no debe repetirse ("otra opción")
     * @param varied introduce aleatoriedad controlada en la selección de ejercicios (refresh)
     */
    private ExistingRoute generate(UUID userId, UUID forcedActivity, UUID excludeActivity, boolean varied) {
        UserContext ctx = repository.loadUserContext(userId);
        Feedback feedback = repository.loadFeedback(userId);
        List<Scored> ranking = rank(ctx, feedback);
        if (ranking.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay actividades en el catálogo para recomendar. Corre sportacus_seed_data.sql.");
        }

        Scored chosen = null;
        int rank = 1;
        for (Scored candidate : ranking) {
            boolean match = forcedActivity != null
                    ? candidate.activity().id().equals(forcedActivity)
                    : (excludeActivity == null || !candidate.activity().id().equals(excludeActivity));
            if (match) {
                chosen = candidate;
                break;
            }
            rank++;
        }
        if (chosen == null) {
            chosen = ranking.get(0);
            rank = 1;
        }

        int strength = questionnaireService.profileStrengthPct(userId);
        repository.deleteExistingRoutes(userId);
        UUID routeId = repository.insertRoute(userId, ctx.goalId(), chosen.activity().id(), chosen.score(),
                rationale(ctx, chosen, strength), new LinkedHashMap<String, Object>(chosen.breakdown()), 1,
                dayLabels(ctx));

        persistExercises(routeId, chosen.activity(), ctx, feedback, userId, varied);
        return repository.findCurrentRoute(userId).orElseThrow();
    }

    private void persistExercises(UUID routeId, ActivityRow activity, UserContext ctx, Feedback feedback,
            UUID userId, boolean varied) {
        List<ExerciseRow> pool = new ArrayList<>(repository.listExercisesForActivity(activity.id()));
        int perDay = exercisesPerDay(ctx);
        int days = dayLabels(ctx).size();
        int target = perDay + (days - 1) * ((perDay + 1) / 2);

        // Al pedir variedad se admiten (pocos) ejercicios de disciplinas hermanas de la misma categoría.
        List<ExerciseRow> siblings = varied
                ? repository.listSiblingExercises(activity.categoryName(), activity.id()) : List.of();

        Random random = varied ? new Random() : new Random(userId.hashCode());
        List<ExerciseRow> chosen = pickExercises(pool, siblings, target, ctx, feedback, random, varied);

        boolean timeBased = TIME_BASED_CATEGORIES.contains(activity.categoryName());
        int[] setsReps = setsRepsFor(ctx);
        int restSec = timeBased ? 30 : restFor(ctx);

        int orderIndex = 0;
        for (ExerciseRow exercise : chosen) {
            int score = clampScore(exerciseScore(exercise, ctx, feedback));
            int dayIndex = orderIndex % days;
            if (timeBased) {
                repository.insertExerciseRecommendation(routeId, exercise.id(), orderIndex, score,
                        setsReps[0], null, 30, restSec, dayIndex);
            } else {
                repository.insertExerciseRecommendation(routeId, exercise.id(), orderIndex, score,
                        setsReps[0], setsReps[1], null, restSec, dayIndex);
            }
            orderIndex++;
        }
    }

    private List<ExerciseRow> pickExercises(List<ExerciseRow> pool, List<ExerciseRow> siblings, int target,
            UserContext ctx, Feedback feedback, Random random, boolean varied) {
        int cap = difficultyCap(ctx);

        List<Candidate> candidates = new ArrayList<>();
        for (ExerciseRow e : pool) {
            addCandidate(candidates, e, 0, ctx, feedback, cap, random, varied);
        }
        for (ExerciseRow e : siblings) {
            addCandidate(candidates, e, -15, ctx, feedback, cap, random, varied);
        }

        int count = Math.min(target, candidates.size());
        int siblingsChosen = 0;
        List<ExerciseRow> chosen = new ArrayList<>();
        Map<String, Integer> byGroup = new LinkedHashMap<>();
        Map<String, Integer> byPattern = new LinkedHashMap<>();
        while (chosen.size() < count) {
            Candidate best = null;
            double bestScore = -Double.MAX_VALUE;
            for (Candidate c : candidates) {
                if (chosen.contains(c.exercise())) continue;
                if (c.sibling() && siblingsChosen >= MAX_SIBLING_EXERCISES) continue;
                double adjusted = c.score()
                        - 8 * byGroup.getOrDefault(String.valueOf(c.exercise().muscleGroupId()), 0)
                        - 5 * byPattern.getOrDefault(String.valueOf(c.exercise().movementPattern()), 0);
                if (adjusted > bestScore) {
                    bestScore = adjusted;
                    best = c;
                }
            }
            if (best == null) break;
            chosen.add(best.exercise());
            if (best.sibling()) siblingsChosen++;
            byGroup.merge(String.valueOf(best.exercise().muscleGroupId()), 1, Integer::sum);
            byPattern.merge(String.valueOf(best.exercise().movementPattern()), 1, Integer::sum);
        }
        return chosen;
    }

    private static final int MAX_SIBLING_EXERCISES = 2;

    private record Candidate(ExerciseRow exercise, double score, boolean sibling) {
    }

    private void addCandidate(List<Candidate> candidates, ExerciseRow e, int offset, UserContext ctx,
            Feedback feedback, int cap, Random random, boolean varied) {
        if (feedback.disliked().contains(e.id())) {
            return; // el usuario ya dijo que no le gusta
        }
        double score = exerciseScore(e, ctx, feedback) + offset + random.nextInt(varied ? 25 : 4);
        if (e.difficultyLevel() != null && e.difficultyLevel() > cap) {
            score -= 30; // demasiado difícil para su nivel: solo entra si no hay más opciones
        }
        candidates.add(new Candidate(e, score, offset < 0));
    }

    // ───────────────────────── Puntuación de actividades ─────────────────────────

    private record Factor(String key, int points, String text) {
    }

    private record Scored(ActivityRow activity, int score, List<Factor> factors) {
        Map<String, Integer> breakdown() {
            Map<String, Integer> map = new LinkedHashMap<>();
            for (Factor f : factors) {
                map.merge(f.key(), f.points(), Integer::sum);
            }
            return map;
        }
    }

    private List<Scored> rank(UserContext ctx, Feedback feedback) {
        return repository.listActiveActivities().stream()
                .map(a -> scoreActivity(a, ctx, feedback))
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(s -> s.activity().name()))
                .toList();
    }

    private Scored scoreActivity(ActivityRow a, UserContext ctx, Feedback feedback) {
        List<Factor> f = new ArrayList<>();
        String impact = a.jointImpact();
        String intensity = a.intensityLevel();
        boolean highIntensity = "alto".equals(intensity) || "muy_alto".equals(intensity);
        boolean lowIntensity = "bajo".equals(intensity) || "muy_bajo".equals(intensity);
        String category = a.categoryName();

        // ── Objetivo (hasta 30) ──
        if (ctx.primaryGoal() == null) {
            f.add(new Factor("objetivo", 12, null));
        } else if (a.compatibleGoals().contains(ctx.primaryGoal())) {
            f.add(new Factor("objetivo", 30, "tu objetivo (" + goalLabel(ctx.primaryGoal()).toLowerCase() + ")"));
        } else if (ctx.secondaryGoals().stream().anyMatch(a.compatibleGoals()::contains)) {
            f.add(new Factor("objetivo", 14, "tus objetivos secundarios"));
        } else {
            f.add(new Factor("objetivo", 0, null));
        }
        if (ctx.motivation() != null && ctx.motivation() <= 3 && highIntensity) {
            f.add(new Factor("motivacion", -3, "tu motivación actual es baja para una disciplina intensa"));
        }

        // ── Entorno (hasta 20) ──
        List<String> envs = ctx.environments();
        String env = a.environment();
        if (envs.isEmpty()) {
            f.add(new Factor("entorno", 10, null));
        } else if ("cualquiera".equals(env)) {
            f.add(new Factor("entorno", 14, "se adapta a cualquier entorno"));
        } else if (envs.contains(env) || (envs.contains("centro_deportivo") && "gimnasio".equals(env))) {
            f.add(new Factor("entorno", 20, "tu entorno (" + env + ")"));
        } else {
            f.add(new Factor("entorno", 0, "no coincide con tu entorno disponible"));
        }
        if ("exterior".equals(env) && !ctx.outdoorSafe()) {
            f.add(new Factor("seguridad_exterior", -10, "no te sientes seguro entrenando al aire libre"));
        }
        if ("casa".equals(env) && "sin_espacio".equals(ctx.homeSpace())) {
            f.add(new Factor("espacio", -8, "tienes poco espacio en casa"));
        }
        if ("gimnasio".equals(env) && ctx.gymDistanceMin() != null && ctx.gymDistanceMin() > 45) {
            f.add(new Factor("distancia_gimnasio", -4, "el gimnasio te queda lejos"));
        }
        if ("gimnasio".equals(env) && "ninguno".equals(ctx.budgetMembership()) && !envs.contains("gimnasio")) {
            f.add(new Factor("presupuesto", -6, "sin presupuesto para gimnasio"));
        }

        // ── Experiencia (hasta 12) ──
        int diff = ctx.experienceLevel() - a.minEntryLevel();
        if (diff >= 3) {
            f.add(new Factor("experiencia", 8, "tu nivel supera la entrada de esta disciplina"));
        } else if (diff >= 0) {
            f.add(new Factor("experiencia", 12, "tu nivel de experiencia"));
        } else if (diff == -1) {
            f.add(new Factor("experiencia", 3, null));
        } else {
            f.add(new Factor("experiencia", -12, "requiere más experiencia de la que indicas"));
        }
        if (ctx.yearsActive() != null && ctx.yearsActive() >= 3 && a.minEntryLevel() >= 2) {
            f.add(new Factor("trayectoria", 2, "tus años de actividad"));
        }

        // ── Edad e IMC (hasta -16) ──
        if (ctx.age() == null && a.ageRangeMin() != null && a.ageRangeMin() >= 50) {
            f.add(new Factor("edad_desconocida", -6, null)); // disciplina orientada a mayores y no sabemos la edad
        }
        if (ctx.age() != null) {
            boolean outside = (a.ageRangeMin() != null && ctx.age() < a.ageRangeMin())
                    || (a.ageRangeMax() != null && ctx.age() > a.ageRangeMax());
            if (outside) {
                f.add(new Factor("edad", -8, "tu edad está fuera del rango habitual"));
            }
            if (ctx.age() >= 60 && "alto".equals(impact)) {
                f.add(new Factor("edad_impacto", -10, "tu edad aconseja evitar impacto articular alto"));
            } else if (ctx.age() >= 60 && highIntensity) {
                f.add(new Factor("edad_intensidad", -6, "tu edad aconseja moderar la intensidad"));
            }
        }
        if (ctx.bmi() != null && ctx.bmi() >= 30) {
            if ("alto".equals(impact)) {
                f.add(new Factor("imc", -8, "tu IMC sugiere priorizar bajo impacto"));
            } else if ("bajo".equals(impact)) {
                f.add(new Factor("imc", 4, "es de bajo impacto articular, ideal con tu IMC"));
            }
        }

        // ── Seguridad: lesiones y condiciones ──
        boolean hasRestrictions = false;
        if (ctx.hasActiveInjury()) {
            hasRestrictions = true;
            boolean severe = "grave".equals(ctx.injurySeverity());
            if ("bajo".equals(impact)) {
                f.add(new Factor("lesion", severe ? 10 : 8, "es de bajo impacto con tu lesión activa"));
            } else if ("alto".equals(impact)) {
                f.add(new Factor("lesion", severe ? -30 : -20, "su impacto articular es alto con tu lesión activa"));
            } else {
                f.add(new Factor("lesion", severe ? -8 : -4, null));
            }
            int hits = 0;
            for (String area : a.commonRestrictions()) {
                if (ctx.injuryAreas().contains(normalize(area))) hits++;
            }
            if (hits > 0) {
                f.add(new Factor("zona_lesionada", -15 * Math.min(2, hits), "puede cargar una zona lesionada"));
            }
        }
        for (String condition : ctx.conditions()) {
            String c = normalize(condition);
            if ((c.contains("hipertension") || c.contains("cardiopatia")) && highIntensity) {
                f.add(new Factor("condicion", -12, "tu condición cardiovascular pide intensidad moderada"));
                hasRestrictions = true;
            } else if ((c.contains("hipertension") || c.contains("cardiopatia")) && lowIntensity) {
                f.add(new Factor("condicion", 3, "su intensidad suave cuida tu condición cardiovascular"));
                hasRestrictions = true;
            } else if (c.contains("osteoporosis") && "alto".equals(impact)) {
                f.add(new Factor("condicion", -10, "el impacto alto no es recomendable con osteoporosis"));
                hasRestrictions = true;
            } else if (c.contains("artritis") && "alto".equals(impact)) {
                f.add(new Factor("condicion", -8, "el impacto alto puede agravar tu artritis"));
                hasRestrictions = true;
            } else if (c.contains("artritis") && "bajo".equals(impact)) {
                f.add(new Factor("condicion", 3, "su bajo impacto es amable con tus articulaciones"));
                hasRestrictions = true;
            } else if (c.contains("asma") && "Cardiovascular".equals(category) && highIntensity) {
                f.add(new Factor("condicion", -5, "el cardio intenso puede ser exigente con asma"));
                hasRestrictions = true;
            } else if (c.contains("post-operatorio") && !lowIntensity) {
                f.add(new Factor("postoperatorio", -10, "tu cirugía reciente pide intensidad baja"));
                hasRestrictions = true;
            }
        }
        if (!hasRestrictions) {
            f.add(new Factor("sin_restricciones", 5, null));
        }

        // ── Equipamiento (hasta 8) ──
        boolean noEquipmentNeeded = a.equipmentRequired() == null || a.equipmentRequired().isBlank()
                || "ninguno".equalsIgnoreCase(a.equipmentRequired());
        boolean hasMatchingEquipment = !noEquipmentNeeded && ctx.ownedEquipmentNames().stream()
                .anyMatch(owned -> a.equipmentRequired().toLowerCase().contains(owned.toLowerCase()));
        if (noEquipmentNeeded) {
            f.add(new Factor("equipamiento", 8, "no necesita equipamiento"));
        } else if (hasMatchingEquipment) {
            f.add(new Factor("equipamiento", 8, "tu equipamiento"));
        } else if ("gimnasio".equals(a.environment())) {
            f.add(new Factor("equipamiento", 6, null));
        } else {
            f.add(new Factor("equipamiento", -10, "necesita equipo que no indicaste tener"));
        }

        // ── Tiempo por sesión (hasta 6) ──
        Integer available = minutesOf(ctx.timePerSession());
        if (available != null && a.typicalSessionMin() != null) {
            int gap = Math.abs(available - a.typicalSessionMin());
            if (gap <= 10) {
                f.add(new Factor("tiempo", 6, "encaja con tu tiempo por sesión"));
            } else if (gap <= 25) {
                f.add(new Factor("tiempo", 2, null));
            } else if (a.typicalSessionMin() > available) {
                f.add(new Factor("tiempo", -6, "dura más de tu tiempo disponible por sesión"));
            } else {
                f.add(new Factor("tiempo", 0, null));
            }
        } else {
            f.add(new Factor("tiempo", 3, null));
        }

        // ── Recuperación: sueño y estrés ──
        boolean poorRecovery = (ctx.sleepQuality() != null && ctx.sleepQuality() <= 2)
                || (ctx.sleepHours() != null && ctx.sleepHours() < 6)
                || (ctx.stressLevel() != null && ctx.stressLevel() >= 4);
        if (poorRecovery && highIntensity) {
            f.add(new Factor("recuperacion", -6, "tu descanso o estrés actual sugiere bajar la intensidad"));
        } else if (poorRecovery && ("Mente-Cuerpo".equals(category) || "Flexibilidad".equals(category))) {
            f.add(new Factor("recuperacion", 5, "ayuda a recuperarte y gestionar el estrés"));
        } else if (!poorRecovery && ctx.sleepQuality() != null && ctx.sleepQuality() >= 4 && highIntensity) {
            f.add(new Factor("recuperacion", 2, "tu buen descanso respalda una intensidad alta"));
        }
        if (ctx.seatedHours() != null && ctx.seatedHours() >= 8
                && ("Cardiovascular".equals(category) || "Flexibilidad".equals(category))) {
            f.add(new Factor("sedentarismo", 2, "compensa las muchas horas que pasas sentado"));
        }

        // ── Preferencias y continuidad ──
        if ("grupo".equals(ctx.socialPreference()) && "Deportes".equals(category)) {
            f.add(new Factor("social", 3, "prefieres entrenar en grupo"));
        }
        String normalizedName = normalize(a.name());
        if (ctx.disciplines().stream().map(this::normalize).anyMatch(d -> !d.isBlank() && normalizedName.contains(d))) {
            f.add(new Factor("continuidad", 4, "ya practicas esta disciplina"));
        }

        // ── Retroalimentación previa ──
        int discards = feedback.discardedActivities().getOrDefault(a.id(), 0);
        if (discards > 0) {
            f.add(new Factor("descartada", -25 * Math.min(2, discards), "la descartaste antes"));
        }
        int likes = feedback.likesByActivity().getOrDefault(a.id(), 0);
        if (likes > 0) {
            f.add(new Factor("likes", Math.min(9, 3 * likes), "te gustaron ejercicios de esta disciplina"));
        }
        int dislikes = feedback.dislikesByActivity().getOrDefault(a.id(), 0);
        if (dislikes > 0) {
            f.add(new Factor("dislikes", -Math.min(8, 2 * dislikes), null));
        }

        int raw = f.stream().mapToInt(Factor::points).sum();
        return new Scored(a, Math.max(0, Math.min(100, raw)), f);
    }

    /** Justificación legible: lo que más sumó, lo que conviene vigilar y cuánto se conoce del perfil. */
    private String rationale(UserContext ctx, Scored scored, int profileStrengthPct) {
        List<String> positives = scored.factors().stream()
                .filter(x -> x.points() > 0 && x.text() != null)
                .sorted(Comparator.comparingInt(Factor::points).reversed())
                .map(Factor::text).distinct().limit(3).toList();
        List<String> warnings = scored.factors().stream()
                .filter(x -> x.points() < 0 && x.text() != null)
                .sorted(Comparator.comparingInt(Factor::points))
                .map(Factor::text).distinct().limit(2).toList();

        StringBuilder sb = new StringBuilder();
        if (positives.isEmpty()) {
            sb.append("Opción general mientras conocemos mejor tu perfil.");
        } else {
            sb.append("Encaja contigo por ").append(joinSpanish(positives)).append(".");
        }
        if (!warnings.isEmpty()) {
            sb.append(" A tener en cuenta: ").append(String.join("; ", warnings)).append(".");
        }
        if (profileStrengthPct < 50) {
            sb.append(" Completa más plantillas de tu perfil para afinar esta recomendación.");
        }
        return sb.toString();
    }

    private String joinSpanish(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    // ───────────────────────── Puntuación de ejercicios ─────────────────────────

    private int difficultyCap(UserContext ctx) {
        return Math.max(2, Math.min(5, ctx.experienceLevel() + 2));
    }

    private int exerciseScore(ExerciseRow e, UserContext ctx, Feedback feedback) {
        int score = 50;
        int target = Math.min(5, ctx.experienceLevel() + 1);
        if (e.difficultyLevel() != null) {
            int gap = target - e.difficultyLevel();
            score += gap == 0 ? 10 : (gap > 0 ? 4 : 6 * gap);
        }
        if (isContraindicated(e, ctx)) {
            score -= 40;
        }
        if (needsMissingEquipment(e, ctx)) {
            score -= 15;
        }
        if (feedback.liked().contains(e.id())) {
            score += 20;
        }
        score -= 10 * feedback.swaps().getOrDefault(e.id(), 0);
        return score;
    }

    private boolean isContraindicated(ExerciseRow e, UserContext ctx) {
        String text = normalize(e.contraindications());
        if (text.isBlank()) return false;
        for (String area : ctx.injuryAreas()) {
            if (text.contains(area)) return true;
        }
        for (String condition : ctx.conditions()) {
            String c = normalize(condition);
            String stem = c.length() > 6 ? c.substring(0, 6) : c;
            if (!stem.isBlank() && text.contains(stem)) return true;
        }
        return false;
    }

    private boolean needsMissingEquipment(ExerciseRow e, UserContext ctx) {
        String needed = e.equipmentNeeded();
        if (needed == null || needed.isBlank()) return false;
        if (ctx.environments().contains("gimnasio") || ctx.environments().contains("centro_deportivo")) return false;
        if (ctx.ownedEquipmentNames().isEmpty()) return false; // sin datos de equipo: no se penaliza
        for (String item : needed.split(",")) {
            String name = item.trim().toLowerCase();
            if (ctx.ownedEquipmentNames().stream().noneMatch(o -> o.toLowerCase().contains(name)
                    || name.contains(o.toLowerCase()))) {
                return true;
            }
        }
        return false;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    // ───────────────────────── Tamaño y formato de la rutina ─────────────────────────

    private Integer minutesOf(String timePerSession) {
        if (timePerSession == null) return null;
        return switch (timePerSession) {
            case "menos_30" -> 25;
            case "30_45" -> 40;
            case "45_60" -> 55;
            case "60_90" -> 75;
            case "mas_90" -> 100;
            default -> null;
        };
    }

    private int exercisesPerDay(UserContext ctx) {
        if (ctx.timePerSession() == null) return 5;
        return switch (ctx.timePerSession()) {
            case "menos_30" -> 4;
            case "30_45" -> 5;
            case "45_60" -> 6;
            case "60_90" -> 7;
            case "mas_90" -> 8;
            default -> 5;
        };
    }

    /** Días de la rutina: los preferidos del usuario o un patrón estándar según los días por semana. */
    private List<String> dayLabels(UserContext ctx) {
        List<String> preferred = WEEK.stream().filter(ctx.preferredDays()::contains).toList();
        if (!preferred.isEmpty()) {
            return preferred;
        }
        int days = ctx.daysPerWeek() == null ? 3 : Math.max(1, Math.min(7, ctx.daysPerWeek()));
        return switch (days) {
            case 1 -> List.of("Lun");
            case 2 -> List.of("Lun", "Jue");
            case 3 -> List.of("Lun", "Mié", "Vie");
            case 4 -> List.of("Lun", "Mar", "Jue", "Vie");
            case 5 -> List.of("Lun", "Mar", "Mié", "Vie", "Sáb");
            case 6 -> List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb");
            default -> WEEK;
        };
    }

    private int[] setsRepsFor(UserContext ctx) {
        int[] base = setsRepsForGoal(ctx.primaryGoal());
        if (ctx.experienceLevel() <= 1) {
            base[0] = Math.max(2, base[0] - 1); // principiantes: menos volumen
        }
        return base;
    }

    private int[] setsRepsForGoal(String goal) {
        if (goal == null) return new int[] { 3, 10 };
        return switch (goal) {
            case "ganancia_muscular", "rendimiento_deportivo" -> new int[] { 4, 9 };
            case "perdida_grasa", "habito_activo" -> new int[] { 3, 13 };
            default -> new int[] { 2, 12 };
        };
    }

    private int restFor(UserContext ctx) {
        if ("ganancia_muscular".equals(ctx.primaryGoal()) || "rendimiento_deportivo".equals(ctx.primaryGoal())) {
            return 90;
        }
        return 60;
    }

    // ───────────────────────── Armado de la respuesta ─────────────────────────

    private RoutineResponse buildResponse(UUID userId, ExistingRoute route) {
        List<String> labels = route.dayLabels().isEmpty() ? List.of("Día 1") : route.dayLabels();
        List<RecommendedExerciseRow> exercises = repository.listExerciseRecommendations(route.routeId());
        Set<UUID> doneIds = repository.doneRecommendationIds(userId);
        int doneCount = (int) exercises.stream().filter(e -> doneIds.contains(e.exerciseRecommendationId())).count();

        List<List<RecommendedExerciseRow>> byDay = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            byDay.add(new ArrayList<>());
        }
        for (RecommendedExerciseRow row : exercises) {
            int idx = Math.max(0, Math.min(row.dayIndex(), labels.size() - 1));
            byDay.get(idx).add(row);
        }

        List<RoutineDayResponse> days = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            days.add(new RoutineDayResponse(labels.get(i), byDay.get(i).stream()
                    .map(row -> toRoutineExercise(row, doneIds.contains(row.exerciseRecommendationId()))).toList()));
        }

        int alternatives = Math.min(MAX_OPTIONS, repository.countActiveActivities()) - 1;
        return new RoutineResponse(
                route.activityName(), route.description(), route.typicalSessionMin(),
                intensityLabel(route.intensityLevel()), goalLabel(route.primaryGoal()),
                route.score(), route.rationale(), days,
                route.routeId().toString(), route.activityId().toString(),
                route.createdAt() == null ? null : route.createdAt().toString(),
                questionnaireService.profileStrengthPct(userId), route.engineVersion(), Math.max(0, alternatives),
                doneCount, exercises.size(), breakdownFor(route.routeId()));
    }

    private static final Map<String, String> FACTOR_LABELS = Map.ofEntries(
            Map.entry("objetivo", "Tu objetivo"), Map.entry("entorno", "Tu entorno"),
            Map.entry("experiencia", "Tu nivel de experiencia"), Map.entry("equipamiento", "Tu equipamiento"),
            Map.entry("tiempo", "Tu tiempo disponible"), Map.entry("likes", "Ejercicios que te gustaron"),
            Map.entry("dislikes", "Ejercicios que descartaste"), Map.entry("sin_restricciones", "Sin restricciones"),
            Map.entry("lesion", "Tus lesiones"), Map.entry("zona_lesionada", "Zona lesionada"),
            Map.entry("edad", "Tu edad"), Map.entry("edad_impacto", "Tu edad e impacto articular"),
            Map.entry("edad_intensidad", "Tu edad e intensidad"), Map.entry("imc", "Tu IMC"),
            Map.entry("motivacion", "Tu motivación"), Map.entry("seguridad_exterior", "Seguridad al aire libre"),
            Map.entry("espacio", "Espacio en casa"), Map.entry("distancia_gimnasio", "Distancia al gimnasio"),
            Map.entry("presupuesto", "Presupuesto"), Map.entry("trayectoria", "Tu trayectoria"),
            Map.entry("condicion", "Tu condición cardiovascular"), Map.entry("descartes", "Rutas que descartaste"));

    /** Factores del score ordenados por peso absoluto; los que no aportan puntos no se muestran. */
    private List<ScoreFactorResponse> breakdownFor(UUID routeId) {
        return repository.findBreakdown(routeId).entrySet().stream()
                .filter(e -> e.getValue() != 0)
                .map(e -> new ScoreFactorResponse(e.getKey(),
                        FACTOR_LABELS.getOrDefault(e.getKey(), humanize(e.getKey())), e.getValue()))
                .sorted(Comparator.comparingInt((ScoreFactorResponse f) -> Math.abs(f.points())).reversed())
                .toList();
    }

    private String humanize(String key) {
        String text = key.replace('_', ' ');
        return text.isEmpty() ? key : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private RoutineExerciseResponse toRoutineExercise(RecommendedExerciseRow row, boolean done) {
        boolean logged = repository.isExerciseLogged(row.exerciseRecommendationId());
        ExerciseMedia media = mediaService.lookup(row.name(), row.muscleGroupName(), List.of());
        String image = media.thumbUrl() != null ? media.thumbUrl()
                : (media.imageUrls().isEmpty() ? null : media.imageUrls().get(0));
        return new RoutineExerciseResponse(
                row.exerciseRecommendationId().toString(), row.exerciseId().toString(), row.name(),
                exerciseIcon(row.muscleGroupName()), detailLabel(row.sets(), row.reps(), row.durationSec()),
                row.muscleGroupName() == null ? "General" : row.muscleGroupName(),
                difficultyLabel(row.difficultyLevel() == null ? null : String.valueOf(row.difficultyLevel())),
                logged, image, done, row.sets(), row.reps(), row.durationSec());
    }

    private List<String> tips(ExerciseDetailRow row) {
        List<String> tips = new ArrayList<>();
        if (row.restSec() != null) {
            tips.add("Descansa " + row.restSec() + " s entre series.");
        }
        if (row.reps() != null) {
            tips.add("Elige un peso con el que puedas completar " + row.reps()
                    + " repeticiones con buena técnica, dejando 1-2 en reserva.");
        } else if (row.durationSec() != null) {
            tips.add("Mantén una respiración continua y controlada durante los " + row.durationSec() + " segundos.");
        }
        tips.add("Calienta 5 minutos antes de empezar y prioriza la técnica sobre la carga.");
        if (row.contraindications() != null && !row.contraindications().isBlank()) {
            tips.add("Si lo notas incómodo, usa «Sustituir ejercicio» para recibir una alternativa similar.");
        }
        return tips;
    }

    /** Quita de los pasos lo que ya se muestra en la descripción, para no repetir el mismo párrafo. */
    private List<String> withoutDescription(List<String> steps, String description) {
        if (description == null || description.isBlank()) {
            return steps;
        }
        String desc = normalize(description).replaceAll("\s+", " ").trim();
        List<String> kept = steps.stream().filter(step -> {
            String n = normalize(step).replaceAll("\s+", " ").trim();
            return !n.isEmpty() && !desc.contains(n) && !n.contains(desc);
        }).toList();
        return kept.isEmpty() ? steps : kept;
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("(?<=[.;])\\s+")).stream().filter(s -> !s.isBlank()).toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private String sessionTypeForCategory(String categoryName) {
        // Mapea la categoría de la actividad al session_type que ya usa el dashboard
        // ('fuerza','cardio','movilidad','hiit','mixto') para que loguear un ejercicio
        // aquí se refleje directo en el radar y las barras de progreso.
        if (categoryName == null) return "mixto";
        return switch (categoryName) {
            case "Fuerza" -> "fuerza";
            case "Cardiovascular" -> "cardio";
            case "Flexibilidad", "Mente-Cuerpo", "Rehabilitación" -> "movilidad";
            default -> "mixto";
        };
    }

    private String intensityLabel(String level) {
        if (level == null) return "Moderada";
        return switch (level) {
            case "muy_bajo" -> "Muy baja";
            case "bajo" -> "Baja";
            case "moderado" -> "Moderada";
            case "alto" -> "Alta";
            case "muy_alto" -> "Muy alta";
            default -> "Moderada";
        };
    }

    private String environmentLabel(String env) {
        if (env == null) return "Cualquiera";
        return switch (env) {
            case "gimnasio" -> "Gimnasio";
            case "casa" -> "Casa";
            case "parque" -> "Parque";
            case "exterior" -> "Exterior";
            default -> "Cualquiera";
        };
    }

    private String difficultyLabel(String level) {
        if (level == null) return "General";
        return switch (level) {
            case "1" -> "Principiante";
            case "2" -> "Básico";
            case "3" -> "Intermedio";
            case "4" -> "Avanzado";
            case "5" -> "Experto";
            default -> "General";
        };
    }

    private String goalLabel(String goal) {
        if (goal == null) return "Bienestar general";
        return switch (goal) {
            case "perdida_grasa" -> "Pérdida de grasa";
            case "ganancia_muscular" -> "Ganancia muscular";
            case "bienestar_general" -> "Bienestar general";
            case "rehabilitacion" -> "Rehabilitación";
            case "habito_activo" -> "Hábito activo";
            case "rendimiento_deportivo" -> "Rendimiento deportivo";
            case "flexibilidad" -> "Flexibilidad";
            case "gestion_estres" -> "Gestión del estrés";
            default -> "Bienestar general";
        };
    }

    private String detailLabel(Integer sets, Integer reps, Integer durationSec) {
        if (sets == null) return "";
        if (durationSec != null) {
            return sets + " series × " + durationSec + " seg";
        }
        if (reps != null) {
            return sets + " series × " + reps + " reps";
        }
        return sets + " series";
    }

    private String exerciseIcon(String muscleGroupName) {
        if (muscleGroupName == null) return "💪";
        return switch (muscleGroupName) {
            case "Pecho" -> "🏋️";
            case "Espalda" -> "🤸";
            case "Hombros" -> "🏋️";
            case "Bíceps", "Tríceps", "Antebrazos" -> "💪";
            case "Core" -> "🧘";
            case "Glúteos", "Cuádriceps", "Isquiotibiales", "Pantorrillas", "Aductores" -> "🦵";
            case "Cuerpo completo" -> "🔥";
            case "Cardiovascular" -> "🏃";
            default -> "💪";
        };
    }
}
