package com.sportacus.backend.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.sportacus.backend.chat.dto.ChatRequest;
import com.sportacus.backend.chat.dto.ChatResponse;
import com.sportacus.backend.recommendation.RecommendationRepository;
import com.sportacus.backend.recommendation.RecommendationRepository.ExistingRoute;
import com.sportacus.backend.recommendation.RecommendationRepository.UserContext;
import com.sportacus.backend.user.User;
import com.sportacus.backend.user.UserNameRepository;

/**
 * Asistente de fitness basado en reglas (Fase 3.1 del roadmap, versión sin LLM):
 * elige el tema que mejor coincide con las palabras clave de la pregunta y
 * responde con una base de conocimiento fija. Si el usuario cargó su perfil,
 * añade una nota personalizada con su objetivo, entorno y restricciones.
 * Es reemplazable por el agente RAG de n8n sin tocar el contrato REST.
 */
@Service
public class ChatService {

    private static final Map<String, String> GOAL_LABELS = Map.of(
            "ganancia_muscular", "ganar músculo",
            "perdida_grasa", "perder grasa",
            "bienestar_general", "mejorar tu bienestar general",
            "rehabilitacion", "rehabilitarte",
            "habito_activo", "crear un hábito activo",
            "rendimiento_deportivo", "mejorar tu rendimiento deportivo",
            "flexibilidad", "ganar flexibilidad",
            "gestion_estres", "gestionar el estrés");

    private record Topic(String id, List<String> keywords, String answer) {
    }

    // Las palabras clave van sin tildes y en minúscula (la pregunta se normaliza igual).
    private static final List<Topic> TOPICS = List.of(
            new Topic("descanso", List.of("descans", "recuper", "pausa", "entre series"),
                    "Entre series descansa 60–90 s en ejercicios de resistencia/hipertrofia y 2–3 min en los pesados de fuerza. "
                            + "Entre sesiones deja 48 h antes de volver a trabajar el mismo grupo muscular y duerme 7–9 horas: ahí es donde te recuperas."),
            new Topic("calentamiento", List.of("calent", "warm", "antes de entrenar", "movilidad previa"),
                    "Dedica 5–10 min: 3 min de cardio suave (caminata, bici o saltar cuerda), movilidad articular de hombros, caderas y tobillos, "
                            + "y 1–2 series ligeras del primer ejercicio con ~50 % del peso de trabajo."),
            new Topic("nutricion", List.of("comer", "comida", "aliment", "nutric", "desayun", "dieta", "proteina"),
                    "Antes de entrenar (1–2 h): carbohidratos de fácil digestión + algo de proteína (avena con yogur, fruta con pan integral). "
                            + "Después: proteína (20–30 g) y carbohidratos para recuperar. Si buscas ganar músculo apunta a ~1.6–2 g de proteína por kg al día."),
            new Topic("progresion", List.of("progres", "carga", "aumentar peso", "subir peso", "estanc", "sobrecarga"),
                    "Usa progresión gradual: cuando completes todas las series en el techo del rango de repeticiones con buena técnica, sube 2–5 % de carga "
                            + "(o 1–2 reps). Si no puedes, mantén el peso y mejora el control. Cada 4–6 semanas programa una semana más ligera."),
            new Topic("casa", List.of("casa", "sin equipo", "sin material", "en el hogar"),
                    "En casa funcionan muy bien: flexiones (pecho/tríceps), sentadillas y zancadas (piernas), remo con mochila o toalla (espalda), "
                            + "plancha (core) y puente de glúteo. Progresa con más repeticiones, tempo lento o variantes unilaterales."),
            new Topic("perder_peso", List.of("perder peso", "bajar de peso", "adelgaz", "grasa", "quemar", "definir"),
                    "Para perder grasa combina un ligero déficit calórico (300–500 kcal), entrenamiento de fuerza 2–3 días para conservar músculo "
                            + "y cardio moderado 2–3 días. Evita recortes extremos: la constancia pesa más que la intensidad."),
            new Topic("ganar_musculo", List.of("ganar musculo", "hipertrof", "masa muscular", "volumen", "musculo"),
                    "Para hipertrofia entrena cada grupo 2 veces por semana con 10–20 series semanales, 6–12 repeticiones cerca del fallo (1–3 reps en reserva), "
                            + "come en ligero superávit con suficiente proteína y duerme bien."),
            new Topic("sueno", List.of("sueno", "dormir", "insomn"),
                    "Intenta dormir 7–9 h con horarios regulares, evita pantallas y cafeína 2–3 h antes de acostarte y no entrenes muy intenso justo antes de dormir. "
                            + "Dormir poco reduce rendimiento, recuperación y control del apetito."),
            new Topic("hidratacion", List.of("agua", "hidrat", "beber", "sed"),
                    "Una referencia útil: 30–35 ml de agua por kg al día, más 400–800 ml por hora de ejercicio intenso o con calor. "
                            + "Si tu orina es amarillo claro, vas bien."),
            new Topic("cardio", List.of("cardio", "correr", "running", "resistencia", "aerob", "trotar"),
                    "Empieza con 20–30 min a un ritmo en el que puedas hablar (zona 2), 2–3 veces por semana, y aumenta el tiempo ~10 % semanal. "
                            + "Añade intervalos cortos solo cuando tengas una base de 4–6 semanas."),
            new Topic("estiramiento", List.of("estir", "flexib", "movilidad", "rigidez", "yoga"),
                    "Estira al final de la sesión (o en días de descanso) manteniendo cada posición 30–45 s sin dolor agudo, y trabaja movilidad activa "
                            + "(círculos de cadera, gato-camello, rotación torácica) 5–10 min al día."),
            new Topic("lesion", List.of("lesion", "dolor", "molest", "duele", "tendin", "operad", "rodilla", "hombro", "lumbar"),
                    "Si sientes dolor agudo, punzante o que persiste, detén el ejercicio y evita cargar la zona. Las agujetas son normales, "
                            + "el dolor articular no. Consulta con un profesional de la salud antes de retomar; esta app no sustituye un diagnóstico médico."),
            new Topic("principiante", List.of("principiante", "empezar", "comenzar", "iniciar", "nuevo en", "primera vez", "por donde"),
                    "Empieza con 2–3 días por semana, sesiones de 30–40 min, ejercicios básicos (sentadilla, empuje, remo, plancha) y técnica antes que peso. "
                            + "Completa tus cuestionarios en la pestaña Perfil y la app te armará una rutina adecuada a tu nivel."),
            new Topic("frecuencia", List.of("cuantos dias", "frecuencia", "cada cuanto", "veces por semana"),
                    "Para la mayoría, 3–4 días por semana es suficiente. Lo importante es que puedas sostenerlo: es mejor 3 días constantes que 6 días durante dos semanas."),
            new Topic("rutina_app", List.of("mi rutina", "rutina recomend", "por que me recomend", "score", "afinidad"),
                    "Tu rutina sale de puntuar cada disciplina según tu objetivo, entorno, equipamiento, experiencia y lesiones. "
                            + "El score de afinidad indica qué tan bien encaja con tu perfil; mejora tus cuestionarios y se recalcula al día siguiente."),
            new Topic("saludo", List.of("hola", "buenas", "buenos dias", "buenas tardes", "hey", "que tal"),
                    "¡Hola! Pregúntame sobre ejercicio, descanso, nutrición básica o cómo progresar. Si pulsas «Cargar mis datos» personalizo las respuestas con tu perfil."));

    private static final String FALLBACK =
            "No tengo una respuesta específica para eso todavía. Prueba preguntando por descanso, calentamiento, progresión de carga, "
                    + "alimentación antes de entrenar, sueño, cardio o ejercicios en casa.";

    private static final String NO_PROFILE_DATA =
            "Aún no tengo datos tuyos: completa los cuestionarios del perfil para personalizar mis respuestas.";

    private final RecommendationRepository recommendationRepository;
    private final UserNameRepository userNameRepository;

    public ChatService(RecommendationRepository recommendationRepository, UserNameRepository userNameRepository) {
        this.recommendationRepository = recommendationRepository;
        this.userNameRepository = userNameRepository;
    }

    public ChatResponse answer(User user, ChatRequest request) {
        Topic topic = bestTopic(normalize(request.message()));
        String reply = topic == null ? FALLBACK : topic.answer();
        boolean personalized = false;

        if (Boolean.TRUE.equals(request.includeProfile())) {
            String note = personalNote(user, topic);
            reply = reply + "\n\n" + (note != null ? note : NO_PROFILE_DATA);
            personalized = note != null;
        }
        return new ChatResponse(reply, personalized);
    }

    private Topic bestTopic(String normalized) {
        Topic best = null;
        int bestHits = 0;
        for (Topic topic : TOPICS) {
            int hits = 0;
            for (String keyword : topic.keywords()) {
                if (normalized.contains(keyword)) {
                    hits += keyword.length(); // las palabras más largas/específicas pesan más
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = topic;
            }
        }
        return best;
    }

    private String personalNote(User user, Topic topic) {
        UserContext ctx = recommendationRepository.loadUserContext(user.getId());
        String routeName = recommendationRepository.findCurrentRoute(user.getId())
                .map(ExistingRoute::activityName).orElse(null);

        List<String> facts = new java.util.ArrayList<>();
        if (ctx.primaryGoal() != null) {
            facts.add("tu objetivo es " + GOAL_LABELS.getOrDefault(ctx.primaryGoal(), "mejorar tu condición"));
        }
        if (!ctx.environments().isEmpty()) {
            facts.add("entrenas en " + String.join("/", ctx.environments()));
        }
        if (routeName != null) {
            facts.add("tu ruta actual es " + routeName);
        }
        if (facts.isEmpty()) {
            return null;
        }

        StringBuilder note = new StringBuilder("🎯 Con tu perfil, ")
                .append(userNameRepository.displayNameOf(user)).append(": ")
                .append(String.join(", ", facts)).append(".");
        if (ctx.hasActiveInjury()) {
            note.append(" Registraste una lesión activa: reduce la carga y evita movimientos que te generen dolor.");
        }
        if (topic != null && "progresion".equals(topic.id()) && ctx.experienceLevel() <= 1) {
            note.append(" Por tu nivel, prioriza técnica y sube la carga en saltos pequeños.");
        }
        return note.toString();
    }

    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }
}
