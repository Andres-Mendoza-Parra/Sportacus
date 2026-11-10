package com.sportacus.backend.health;

import com.sportacus.backend.world.form.Forms;
import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.no;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.showIf;
import static com.sportacus.backend.world.form.Forms.single;
import static com.sportacus.backend.world.form.Forms.yes;

import com.sportacus.backend.world.form.FormSpec;

/**
 * Chequeo de salud universal previo a la actividad. Redacción propia del equipo siguiendo los
 * criterios del algoritmo de tamizaje del ACSM (Riebe et al., Med Sci Sports Exerc 2015) y el marco
 * de banderas rojas de Finucane et al. (JOSPT 2020). NO es el PAR-Q+ (su licencia exige permiso
 * escrito para apps; ver docs/17 §2).
 */
public final class HealthScreeningForm {

    public static final String[] ZONES = { "rodilla", "cadera", "tobillo", "pie", "lumbar", "cervical", "dorsal",
            "hombro", "codo", "muneca" };

    private HealthScreeningForm() {
    }

    public static FormSpec form() {
        return Forms.form("salud", "Tu salud primero",
                "Unas preguntas rápidas para cuidarte. Tus respuestas se guardan cifradas y nunca salen de Sportacus.",
                single("s_active", "En los últimos 3 meses, ¿hiciste ejercicio planificado al menos 3 días por semana?",
                        "Unos 30 minutos a intensidad moderada (te cuesta un poco hablar mientras lo haces).",
                        yes("Sí, con regularidad"), no("No, o solo a veces")),
                multi("s_diagnosis", "¿Un profesional de la salud te ha diagnosticado alguna de estas condiciones?",
                        "Elige todas las que apliquen.",
                        opt("cardiopatia", "❤️", "Enfermedad del corazón"),
                        opt("hipertension", "🩺", "Presión arterial alta"),
                        opt("diabetes", "🩸", "Diabetes"),
                        opt("renal", "🫘", "Enfermedad renal"),
                        opt("asma", "🌬️", "Asma u otra enfermedad respiratoria"),
                        opt("osteoporosis", "🦴", "Osteoporosis"),
                        opt("artritis", "🦵", "Artritis o artrosis"),
                        opt("ninguna", "✅", "Ninguna")),
                multi("s_symptoms", "¿Has notado alguno de estos síntomas al hacer esfuerzo o en reposo?",
                        "Si marcas alguno, te pediremos que consultes a un médico antes de hacer actividad intensa.",
                        opt("dolor_pecho", "💢", "Dolor, presión o molestia en el pecho"),
                        opt("falta_aire", "😮‍💨", "Falta de aire desproporcionada al esfuerzo"),
                        opt("mareos", "💫", "Mareos o desmayos"),
                        opt("palpitaciones", "💓", "Palpitaciones o latidos irregulares"),
                        opt("tobillos_hinchados", "🦶", "Hinchazón de tobillos"),
                        opt("ninguno", "✅", "Ninguno")),
                multi("s_red_flags", "¿Te ocurre alguna de estas situaciones ahora mismo?",
                        "Son señales de alerta que un profesional debe revisar antes de entrenar.",
                        opt("dolor_nocturno", "🌙", "Dolor intenso por la noche que no mejora con el reposo"),
                        opt("perdida_fuerza", "🫳", "Pérdida de fuerza o sensibilidad que va en aumento"),
                        opt("esfinteres", "⚠️", "Cambios en el control de la vejiga o el intestino, o adormecimiento en la zona genital"),
                        opt("fiebre_dolor", "🤒", "Fiebre junto con dolor de espalda o de una articulación"),
                        opt("trauma_reciente", "🚑", "Un golpe o caída fuerte reciente con dolor que no cede"),
                        opt("peso_inexplicado", "⚖️", "Pérdida de peso importante sin explicación"),
                        opt("ninguna", "✅", "Ninguna")),
                multi("s_pain_zones", "¿Tienes dolor o una lesión activa en alguna zona?",
                        "Así evitamos los ejercicios que no te convienen.",
                        opt("rodilla", "🦵", "Rodilla"), opt("cadera", "🦴", "Cadera"),
                        opt("tobillo", "🦶", "Tobillo"), opt("pie", "👣", "Pie"),
                        opt("lumbar", "🔻", "Espalda baja"), opt("dorsal", "🔺", "Espalda alta"),
                        opt("cervical", "🧣", "Cuello"), opt("hombro", "💪", "Hombro"),
                        opt("codo", "🦾", "Codo"), opt("muneca", "✋", "Muñeca o mano"),
                        opt("ninguna", "✅", "Ninguna")),
                showIf(single("s_pain_severity", "¿Qué tanto te limita ese dolor en tu día a día?", null,
                        opt("leve", "🟢", "Poco: molesta pero hago todo"),
                        opt("moderada", "🟡", "Bastante: evito algunos movimientos"),
                        opt("grave", "🔴", "Mucho: me impide actividades normales")),
                        "s_pain_zones", ZONES),
                single("s_surgery", "¿Te han operado en los últimos 6 meses?", null, yes("Sí"), no("No")),
                showIf(single("s_clearance", "¿Tu médico o fisioterapeuta te autorizó a hacer ejercicio?", null,
                        yes("Sí, tengo su aval"), no("Todavía no")), "s_surgery", "si"),
                multi("s_medications", "¿Tomas alguno de estos medicamentos?",
                        "Cambian cómo medimos la intensidad o qué precauciones tomar. Elige todos los que apliquen.",
                        opt("betabloqueadores", "💊", "Betabloqueadores", "Para la presión o el corazón (p. ej. metoprolol, propranolol)"),
                        opt("insulina", "💉", "Insulina u otros que bajan el azúcar", "Riesgo de hipoglucemia al entrenar"),
                        opt("anticoagulantes", "🩸", "Anticoagulantes", "Warfarina, rivaroxabán, apixabán…"),
                        opt("otros", "📋", "Otros de uso diario"),
                        opt("ninguno", "✅", "Ninguno")),
                multi("s_mobility", "¿Tienes alguna condición de movilidad o sensorial que debamos tener en cuenta?",
                        "Adaptamos los ejercicios y la forma de explicarlos.",
                        opt("silla_ruedas", "♿", "Uso silla de ruedas"),
                        opt("ayuda_marcha", "🦯", "Uso bastón, muletas o caminador"),
                        opt("amputacion", "🦿", "Amputación o prótesis"),
                        opt("vision", "👓", "Baja visión"),
                        opt("audicion", "🦻", "Baja audición"),
                        opt("ninguna", "✅", "Ninguna")),
                single("s_pregnancy", "¿Estás en embarazo o tuviste un parto en los últimos 6 meses?", null,
                        yes("Sí"), no("No"), opt("no_aplica", "➖", "No aplica")));
    }
}
