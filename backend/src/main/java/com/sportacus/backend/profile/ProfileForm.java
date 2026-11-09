package com.sportacus.backend.profile;

import com.sportacus.backend.world.form.Forms;
import static com.sportacus.backend.world.form.Forms.multi;
import static com.sportacus.backend.world.form.Forms.number;
import static com.sportacus.backend.world.form.Forms.opt;
import static com.sportacus.backend.world.form.Forms.optional;
import static com.sportacus.backend.world.form.Forms.range;
import static com.sportacus.backend.world.form.Forms.single;

import com.sportacus.backend.world.form.FormSpec;

/** Perfil común: solo lo que sirve a todos los mundos. Lo específico se pregunta dentro de cada mundo. */
public final class ProfileForm {

    private ProfileForm() {
    }

    public static FormSpec form() {
        return Forms.form("perfil", "Cuéntanos de ti", "Con esto adaptamos cualquier mundo que elijas.",
                number("p_birth_year", "¿En qué año naciste?", null, 1925, 2016, null),
                single("p_sex", "Sexo", "Lo usamos para valores de referencia de los tests.",
                        opt("femenino", "♀️", "Femenino"), opt("masculino", "♂️", "Masculino"),
                        opt("otro", "⚧️", "Otro"), opt("prefiero_no_decir", "➖", "Prefiero no decir")),
                number("p_weight", "Peso", null, 25, 300, "kg"),
                number("p_height", "Estatura", null, 100, 230, "cm"),
                single("p_activity", "¿Qué tan activo eres hoy?", null,
                        opt("0", "🛋️", "Sedentario", "Casi todo el día sentado"),
                        opt("1", "🚶", "Ligero", "Camino a diario, sin hacer ejercicio"),
                        opt("2", "🚴", "Moderado", "Ejercicio 1–2 días por semana"),
                        opt("3", "🏃", "Activo", "Ejercicio 3–4 días por semana"),
                        opt("4", "🔥", "Muy activo", "Entreno 5 o más días por semana")),
                range("p_days", "¿Cuántos días por semana quieres entrenar?", null, 1, 7, 1, "días"),
                single("p_minutes", "¿Cuánto tiempo tienes por sesión?", null,
                        opt("20", "⏱️", "20 minutos"), opt("30", "⏱️", "30 minutos"),
                        opt("45", "⏱️", "45 minutos"), opt("60", "⏱️", "1 hora"),
                        opt("90", "⏱️", "1 hora y media")),
                optional(multi("p_preferred_days", "¿Qué días te quedan mejor?", "Opcional.",
                        opt("1", null, "Lunes"), opt("2", null, "Martes"), opt("3", null, "Miércoles"),
                        opt("4", null, "Jueves"), opt("5", null, "Viernes"), opt("6", null, "Sábado"),
                        opt("7", null, "Domingo"))),
                multi("p_environments", "¿Dónde puedes entrenar?", "Elige todos los que tengas a mano.",
                        opt("gimnasio", "🏋️", "Gimnasio"), opt("casa", "🏠", "Casa"),
                        opt("parque", "🌳", "Parque con barras"), opt("exterior", "🛣️", "Calle o pista"),
                        opt("cancha", "⚽", "Cancha")),
                multi("p_equipment", "¿Qué equipamiento tienes?", "Fuera del gimnasio.",
                        opt("ninguno", "🙌", "Nada, solo mi cuerpo"),
                        opt("mancuernas", "🏋️", "Mancuernas"), opt("kettlebell", "🔔", "Kettlebell"),
                        opt("bandas", "🎗️", "Bandas elásticas"), opt("barra_dominadas", "🪜", "Barra de dominadas"),
                        opt("paralelas", "🤸", "Paralelas"), opt("anillas", "⭕", "Anillas"),
                        opt("banco", "🪑", "Banco"), opt("colchoneta", "🧘", "Colchoneta"),
                        opt("balon", "⚽", "Balón"), opt("conos", "🔺", "Conos"),
                        opt("cuerda", "➰", "Cuerda para saltar"), opt("foam_roller", "🧻", "Foam roller"),
                        opt("silla", "💺", "Silla firme"), opt("escalon", "🪜", "Escalón o step")));
    }
}
