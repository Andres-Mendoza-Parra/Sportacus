package com.sportacus.backend.questionnaire;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sportacus.backend.questionnaire.dto.OptionResponse;
import com.sportacus.backend.questionnaire.dto.QuestionResponse;

/**
 * Catálogo estático de plantillas de datos: título, grupo (para la pantalla de lista) y
 * campos (preguntas). Todas las preguntas son opcionales: el usuario puede omitir las que
 * quiera y completarlas después; cuantos más campos tenga contestados, más afinada será la
 * recomendación. Las claves de pregunta y de opción son las que usa
 * {@link QuestionnaireRepository} para guardar en las tablas reales.
 */
public final class QuestionnaireDefinitions {

    public record Definition(String key, String title, String description, String icon, String group,
            List<QuestionResponse> questions) {

        public List<String> questionIds() {
            return questions.stream().map(QuestionResponse::id).toList();
        }
    }

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    /**
     * Plantillas que no se muestran en la lista "Mis datos" ni cuentan para la fuerza del
     * perfil: sus campos ya pertenecen a otras plantillas (objetivo/experiencia/entorno) y se
     * contarían dos veces. Siguen siendo accesibles por key (GET/POST /api/questionnaires/{key}).
     */
    private static final java.util.Set<String> HIDDEN_KEYS = java.util.Set.of("enfoque");

    public static boolean isHidden(String key) {
        return HIDDEN_KEYS.contains(key);
    }

    private static OptionResponse opt(String id, String icon, String text, String sub) {
        return new OptionResponse(id, icon, text, sub);
    }

    private static QuestionResponse options(String id, String prompt, String hint, OptionResponse... opts) {
        return new QuestionResponse(id, prompt, hint, "options", List.of(opts), null, null, null, null);
    }

    private static QuestionResponse range(String id, String prompt, String hint, int min, int max, String unit) {
        return new QuestionResponse(id, prompt, hint, "range", null, min, max, unit, null);
    }

    private static QuestionResponse tags(String id, String prompt, String hint, String... tags) {
        return new QuestionResponse(id, prompt, hint, "tags", null, null, null, null, List.of(tags));
    }

    private static OptionResponse yes(String text) {
        return opt("si", "✅", text, null);
    }

    private static OptionResponse no(String text) {
        return opt("no", "❌", text, null);
    }

    static {
        // Onboarding obligatorio: lo mínimo para rankear disciplinas (objetivo, nivel, entorno)
        // antes de mostrar /onboarding/module. Oculta de "Mis datos" (ver HIDDEN_KEYS): sus
        // campos son los mismos que "objetivo"/"experiencia"/"entorno" ya cubren a fondo.
        register(new Definition("enfoque", "Tu enfoque inicial", "Objetivo, nivel y entorno", "🧭", "Esenciales",
                List.of(
                        options("q_primary_goal", "¿Cuál es tu objetivo principal?",
                                "Selecciona el que más se acerque a lo que buscas.",
                                opt("ganancia_muscular", "💪", "Ganar músculo", "Hipertrofia y fuerza"),
                                opt("perdida_grasa", "🔥", "Perder peso", "Cardio y déficit calórico"),
                                opt("rendimiento_deportivo", "🏃", "Rendimiento deportivo", "Mejorar en tu disciplina"),
                                opt("bienestar_general", "🧘", "Bienestar general", "Salud y hábitos"),
                                opt("flexibilidad", "🤸", "Flexibilidad", "Movilidad y amplitud"),
                                opt("rehabilitacion", "🩹", "Rehabilitación", "Recuperación funcional"),
                                opt("habito_activo", "✅", "Crear el hábito", "Moverme con constancia"),
                                opt("gestion_estres", "😌", "Gestionar el estrés", "Calma y descanso")),
                        options("q_experience_level", "¿Cuál es tu experiencia entrenando?",
                                "Es una de las variables que más pesa en tu rutina.",
                                opt("0", "🌱", "Nunca he entrenado", null),
                                opt("1", "🙂", "Principiante", "Menos de 1 año"),
                                opt("2", "💪", "Intermedio", "1 a 3 años"),
                                opt("3", "🔥", "Avanzado", "Más de 3 años"),
                                opt("4", "🏆", "Competitivo", "Entreno para competir")),
                        tags("q_environments", "¿Dónde entrenas o podrías entrenar?", "Selecciona todos los que apliquen.",
                                "Gimnasio", "Casa", "Parque", "Centro deportivo"))));

        register(new Definition("personal", "Datos personales", "Edad, peso, altura, composición", "👤", "Esenciales",
                List.of(
                        range("q_age", "¿Cuál es tu edad?",
                                "Esto ajusta las recomendaciones de intensidad y progresión.", 14, 80, "años"),
                        range("q_weight", "¿Cuánto pesas aproximadamente?",
                                "Usamos esto para calcular cargas y necesidades calóricas.", 35, 200, "kg"),
                        range("q_height", "¿Cuál es tu altura?", null, 140, 220, "cm"),
                        options("q_gender", "¿Con qué género te identificas?",
                                "Afecta parámetros fisiológicos de referencia.",
                                opt("masculino", "👨", "Masculino", null),
                                opt("femenino", "👩", "Femenino", null),
                                opt("prefiero_no_decir", "🧑", "Prefiero no decir", null)),
                        range("q_body_fat", "¿Cuál es tu porcentaje de grasa corporal aproximado?",
                                "Si no lo sabes, puedes omitirla.", 5, 60, "%"),
                        range("q_waist", "¿Cuánto mide tu cintura?",
                                "Medida a la altura del ombligo. Ayuda a estimar el riesgo cardiometabólico.", 50, 160, "cm"))));

        register(new Definition("objetivo", "Objetivo y motivación", "Qué quieres lograr y qué te mueve", "🎯", "Esenciales",
                List.of(
                        options("q_primary_goal", "¿Cuál es tu objetivo principal?",
                                "Selecciona el que más se acerque a lo que buscas.",
                                opt("ganancia_muscular", "💪", "Ganar músculo", "Hipertrofia y fuerza"),
                                opt("perdida_grasa", "🔥", "Perder peso", "Cardio y déficit calórico"),
                                opt("rendimiento_deportivo", "🏃", "Rendimiento deportivo", "Mejorar en tu disciplina"),
                                opt("bienestar_general", "🧘", "Bienestar general", "Salud y hábitos"),
                                opt("flexibilidad", "🤸", "Flexibilidad", "Movilidad y amplitud"),
                                opt("rehabilitacion", "🩹", "Rehabilitación", "Recuperación funcional"),
                                opt("habito_activo", "✅", "Crear el hábito", "Moverme con constancia"),
                                opt("gestion_estres", "😌", "Gestionar el estrés", "Calma y descanso")),
                        tags("q_secondary_goals", "¿Tienes objetivos secundarios?",
                                "Pueden ayudar a desempatar entre varias disciplinas.",
                                "Ganar músculo", "Perder grasa", "Rendimiento deportivo", "Bienestar general",
                                "Flexibilidad", "Rehabilitación", "Crear el hábito", "Gestionar el estrés"),
                        options("q_time_horizon", "¿En cuánto tiempo quieres ver resultados?",
                                "Esto nos ayuda a calibrar la progresión.",
                                opt("corto", "⚡", "1 a 3 meses", "Cambios iniciales visibles"),
                                opt("medio", "📅", "3 a 6 meses", "Progreso sólido y sostenible"),
                                opt("largo", "🌱", "Más de 6 meses", "Transformación a largo plazo")),
                        range("q_target_weight", "¿Tienes un peso objetivo?", "Omítela si no aplica.", 35, 200, "kg"),
                        range("q_perceived_fitness", "¿Cómo calificas tu condición física actual?", null, 1, 10, "/10"),
                        range("q_motivation", "¿Qué tan motivado estás hoy?", null, 1, 10, "/10"),
                        range("q_confidence", "¿Qué tan seguro estás de lograrlo?", null, 1, 10, "/10"),
                        options("q_motivation_type", "¿Qué te motiva más?", null,
                                opt("salud", "❤️", "Salud", "Sentirme bien y prevenir"),
                                opt("estetica", "🪞", "Estética", "Verme mejor"),
                                opt("rendimiento", "🏆", "Rendimiento", "Superar marcas"),
                                opt("social", "👥", "Social", "Compartir con otros"),
                                opt("desafio", "⛰️", "Desafío personal", "Ponerme retos")))));

        register(new Definition("experiencia", "Experiencia y actividad", "Nivel, años entrenando, actividad diaria", "🏅", "Condición física",
                List.of(
                        options("q_experience_level", "¿Cuál es tu experiencia entrenando?",
                                "Es una de las variables que más pesa en tu rutina.",
                                opt("0", "🌱", "Nunca he entrenado", null),
                                opt("1", "🙂", "Principiante", "Menos de 1 año"),
                                opt("2", "💪", "Intermedio", "1 a 3 años"),
                                opt("3", "🔥", "Avanzado", "Más de 3 años"),
                                opt("4", "🏆", "Competitivo", "Entreno para competir")),
                        range("q_years_active", "¿Cuántos años llevas siendo activo?", null, 0, 30, "años"),
                        tags("q_disciplines", "¿Qué disciplinas practicas actualmente?", "Marca todas las que apliquen.",
                                "Gimnasio", "Calistenia", "Running", "Ciclismo", "Natación", "Yoga", "Pilates",
                                "Fútbol", "Baloncesto", "Boxeo", "Ninguna"),
                        range("q_daily_steps", "¿Cuántos pasos das en un día normal?",
                                "Aproximado, en miles de pasos.", 0, 25, "mil pasos"),
                        range("q_seated_hours", "¿Cuántas horas pasas sentado al día?", null, 0, 16, "horas"))));

        register(new Definition("salud_habitos", "Sueño y hábitos", "Descanso, estrés y rutina diaria", "😴", "Salud y hábitos",
                List.of(
                        range("q_sleep_hours", "¿Cuántas horas duermes en promedio?",
                                "El sueño es clave para la recuperación muscular.", 3, 12, "horas"),
                        options("q_sleep_quality", "¿Cómo calificarías la calidad de tu sueño?", null,
                                opt("1", "😩", "Muy mala", "Me cuesta descansar"),
                                opt("2", "😐", "Mala", "Interrumpido con frecuencia"),
                                opt("3", "🙂", "Regular", "A veces interrumpido"),
                                opt("4", "😊", "Buena", "Generalmente bien"),
                                opt("5", "😴", "Muy buena", "Me despierto descansado")),
                        options("q_stress_level", "¿Cómo describirías tu nivel de estrés habitual?", null,
                                opt("1", "😌", "Muy bajo", null),
                                opt("2", "🙂", "Bajo", null),
                                opt("3", "😐", "Moderado", null),
                                opt("4", "😣", "Alto", null),
                                opt("5", "🥵", "Muy alto", null)),
                        options("q_work_activity", "¿Cómo es tu actividad en el trabajo o estudio?", null,
                                opt("sedentario", "🪑", "Sedentario", "Sentado la mayor parte del día"),
                                opt("leve", "🚶", "Leve", "Me muevo de vez en cuando"),
                                opt("moderado", "🚶‍♂️", "Moderado", "Combino estar sentado y de pie"),
                                opt("activo", "🏃", "Activo", "Me muevo gran parte del día"),
                                opt("muy_activo", "🏗️", "Muy activo", "Trabajo físico constante")))));

        register(new Definition("alimentacion", "Alimentación", "Dieta, restricciones, hidratación", "🥗", "Salud y hábitos",
                List.of(
                        options("q_diet_type", "¿Cómo describirías tu dieta actual?",
                                "No hay respuestas correctas o incorrectas.",
                                opt("omnivora", "🥩", "Omnívora", "Como de todo"),
                                opt("vegetariana", "🥦", "Vegetariana", "Sin carne"),
                                opt("vegana", "🌱", "Vegana", "Sin productos animales"),
                                opt("keto", "🥑", "Keto / baja en carbos", null),
                                opt("otro", "🍽️", "Otra / sin definir", null)),
                        range("q_water", "¿Cuántos litros de agua bebes al día?",
                                "La hidratación afecta el rendimiento y recuperación.", 0, 6, "litros"),
                        range("q_meal_count", "¿Cuántas comidas haces al día?", null, 1, 8, "comidas"),
                        options("q_intermittent_fasting", "¿Practicas ayuno intermitente?", null,
                                yes("Sí"), no("No")),
                        options("q_food_budget", "¿Cómo es tu presupuesto para alimentación?", null,
                                opt("bajo", "💵", "Ajustado", null),
                                opt("medio", "💵💵", "Medio", null),
                                opt("alto", "💵💵💵", "Holgado", null)),
                        tags("q_allergies", "¿Tienes alguna restricción alimentaria?", "Selecciona todas las que apliquen.",
                                "Sin gluten", "Sin lactosa", "Frutos secos", "Mariscos", "Ninguna"))));

        register(new Definition("lesiones", "Lesiones y condiciones", "Historial médico relevante", "🩹", "Salud y hábitos",
                List.of(
                        options("q_injury_status", "¿Tienes alguna lesión activa o reciente?",
                                "Esto excluye ejercicios que podrían agravarte.",
                                opt("sin_lesion", "✅", "No, estoy bien", null),
                                opt("lesion_articular", "🦴", "Lesión articular", "Rodilla, cadera, hombro..."),
                                opt("lesion_muscular", "💢", "Lesión muscular", "Desgarro, tensión..."),
                                opt("lesion_espalda", "🔴", "Lesión de espalda", "Lumbar, cervical...")),
                        options("q_injury_severity", "Si tienes una lesión, ¿qué tan severa es?", null,
                                opt("leve", "🟢", "Leve", "Molestia que no me limita"),
                                opt("moderada", "🟠", "Moderada", "Me limita algunos movimientos"),
                                opt("grave", "🔴", "Grave", "Me limita mucho")),
                        tags("q_conditions", "¿Tienes alguna condición de salud crónica?",
                                "Afecta la intensidad y tipo de ejercicio recomendado.",
                                "Hipertensión", "Diabetes", "Osteoporosis", "Asma", "Artritis", "Cardiopatía"),
                        options("q_recent_surgery", "¿Has sido operado recientemente (menos de 1 año)?", null,
                                opt("no", "✅", "No", null),
                                opt("si", "⚕️", "Sí, tengo restricciones", "Cuéntanos más en el chat")))));

        register(new Definition("entorno", "Espacio y entorno", "Casa, parque, gimnasio, clima", "🏠", "Entorno de entrenamiento",
                List.of(
                        tags("q_environments", "¿Dónde entrenas o podrías entrenar?", "Selecciona todos los que apliquen.",
                                "Gimnasio", "Casa", "Parque", "Centro deportivo"),
                        options("q_home_space", "¿Cuánto espacio tienes disponible en casa?", null,
                                opt("sin_espacio", "🚫", "Sin espacio", null),
                                opt("pequeno", "📦", "Pequeño", "Una habitación o menos"),
                                opt("mediano", "🏠", "Mediano", "Un garaje o sala amplia"),
                                opt("grande", "🏟️", "Grande", "Patio o espacio dedicado")),
                        options("q_budget_membership", "¿Cuánto puedes invertir en un gimnasio o club?", null,
                                opt("ninguno", "🚫", "Nada", null),
                                opt("bajo", "💵", "Bajo", null),
                                opt("medio", "💵💵", "Medio", null),
                                opt("alto", "💵💵💵", "Alto", null)),
                        range("q_gym_distance", "¿A cuántos minutos te queda el gimnasio más cercano?", null, 0, 60, "min"),
                        options("q_park_nearby", "¿Tienes un parque cerca para entrenar?", null,
                                yes("Sí"), no("No")),
                        options("q_outdoor_safe", "¿Te sientes seguro entrenando al aire libre?", null,
                                yes("Sí"), no("No")),
                        options("q_climate", "¿Cómo es el clima donde vives?", null,
                                opt("tropical", "🌴", "Cálido / tropical", null),
                                opt("templado", "🌤️", "Templado", null),
                                opt("frio", "❄️", "Frío", null),
                                opt("seco", "🏜️", "Seco", null)),
                        options("q_travels", "¿Viajas con frecuencia?", null, yes("Sí"), no("No")),
                        options("q_social_preference", "¿Prefieres entrenar solo o acompañado?", null,
                                opt("solo", "🧍", "Solo", null),
                                opt("grupo", "👥", "En grupo", null),
                                opt("mixto", "🔀", "Me da igual", null)))));

        register(new Definition("equipamiento", "Máquinas y equipamiento", "Qué tienes a tu disposición", "🏋️", "Entorno de entrenamiento",
                List.of(
                        tags("q_equipment", "¿Qué equipamiento tienes disponible?",
                                "Marca todo lo que tengas acceso real. (Lista cargada desde el catálogo)"),
                        options("q_budget_equipment", "¿Cuánto presupuesto tienes para equipamiento propio?", null,
                                opt("ninguno", "🚫", "Ninguno", null),
                                opt("bajo", "💵", "Bajo", null),
                                opt("medio", "💵💵", "Medio", null),
                                opt("alto", "💵💵💵", "Alto", null)))));

        register(new Definition("disponibilidad", "Tiempos disponibles", "Días y duración de sesiones", "⏱️", "Disponibilidad",
                List.of(
                        range("q_days_per_week", "¿Cuántos días a la semana puedes entrenar?",
                                "Sé realista con tu disponibilidad actual.", 1, 7, "días"),
                        tags("q_preferred_days", "¿Qué días prefieres entrenar?",
                                "Define los días de tu rutina semanal.",
                                "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"),
                        options("q_time_per_session", "¿Cuánto tiempo tienes por sesión?", null,
                                opt("menos_30", "⚡", "Menos de 30 min", "Entrenamiento express"),
                                opt("30_45", "⏱️", "30 a 45 min", "Sesión estándar"),
                                opt("45_60", "🕐", "45 a 60 min", "Sesión completa"),
                                opt("60_90", "🕑", "60 a 90 min", "Tiempo extendido"),
                                opt("mas_90", "🕒", "Más de 90 min", null)),
                        options("q_time_of_day", "¿En qué momento del día prefieres entrenar?", null,
                                opt("manana", "🌅", "Mañana", "Antes del mediodía"),
                                opt("mediodia", "☀️", "Mediodía", "12 pm – 3 pm"),
                                opt("tarde", "🌆", "Tarde", "3 pm – 7 pm"),
                                opt("noche", "🌙", "Noche", "Después de las 7 pm")))));
    }

    private static void register(Definition definition) {
        DEFINITIONS.put(definition.key(), definition);
    }

    public static Map<String, Definition> all() {
        return DEFINITIONS;
    }

    public static Definition get(String key) {
        Definition definition = DEFINITIONS.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Cuestionario no encontrado: " + key);
        }
        return definition;
    }

    private QuestionnaireDefinitions() {
    }
}
