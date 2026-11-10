package com.sportacus.backend.world.core;

import java.util.List;

/**
 * Perfil derivado: conclusiones accionables a partir del Perfil 360, del chequeo de salud y del check-in de hoy. Lo
 * calcula {@code profile.InsightRules} (reglas puras) y lo reciben todos los mundos y el asistente. Cada campo es
 * conservador: si falta el dato, el valor por defecto es el que no cambia nada.
 *
 * @param facts               respuestas crudas, para lógica de nicho de cada mundo
 * @param readiness           disposición de hoy según el check-in (null si no hay check-in de hoy)
 * @param altitudeM           altitud aproximada donde entrena (null si no se sabe)
 * @param heartRateUnreliable betabloqueadores: guiar la intensidad por esfuerzo percibido, no por pulsaciones
 * @param hypoglycemiaRisk    insulina u otros: comer antes, llevar carbohidratos, evitar ayunas
 * @param avoidContact        anticoagulantes: sin contacto ni riesgo de caídas
 * @param eatingCaution       señales de relación difícil con la comida: nada de déficit, pérdida de peso ni foco estético
 * @param noJumpingAtHome     no puede saltar en casa (vecinos, piso)
 * @param lowBudget           presupuesto nulo o bajo para actividad física
 * @param heatHumid           clima cálido (hidratación y horarios frescos)
 * @param unsafeAtNight       entorno inseguro de noche: no recomendar exterior nocturno
 * @param hasDog              tiene perro: caminatas como hábito
 * @param youngChildren       hijos pequeños: sesiones cortas y en casa
 * @param prefersGroup        prefiere entrenar acompañado o en grupo
 * @param competitive         le motiva competir
 * @param physicalJob         trabajo físico pesado: ya suma carga
 * @param sittingHours        horas sentado al día
 * @param activeCommuteMinWeek minutos semanales de transporte activo (caminar o bici)
 * @param tone                tono preferido del asistente: motivador | tecnico | breve | cercano
 * @param learningFormat      video | texto | audio | mixto
 * @param barriers            barreras declaradas
 * @param dropoutReason       por qué dejó de entrenar la última vez
 * @param wellbeingWho5       WHO-5 en escala 0–100 (null si no se respondió completo)
 * @param diet                omnivora | vegetariana | vegana | otra
 * @param dueEvent            evento con fecha (tipo) y su fecha AAAA-MM-DD
 */
public record ProfileInsights(
        ProfileFacts facts,
        Readiness readiness,
        Integer altitudeM,
        boolean heartRateUnreliable,
        boolean hypoglycemiaRisk,
        boolean avoidContact,
        boolean eatingCaution,
        boolean noJumpingAtHome,
        boolean lowBudget,
        boolean heatHumid,
        boolean unsafeAtNight,
        boolean hasDog,
        boolean youngChildren,
        boolean prefersGroup,
        boolean competitive,
        boolean physicalJob,
        Integer sittingHours,
        Integer activeCommuteMinWeek,
        String tone,
        String learningFormat,
        List<String> barriers,
        String dropoutReason,
        Integer wellbeingWho5,
        String diet,
        String dueEvent,
        String dueEventDate) {

    public static ProfileInsights empty() {
        return new ProfileInsights(ProfileFacts.empty(), null, null, false, false, false, false, false, false, false,
                false, false, false, false, false, false, null, null, null, null, List.of(), null, null, null, null,
                null);
    }

    /**
     * Disposición de hoy. {@code factor} 0,6–1,0 multiplica el volumen sugerido (1 = normal).
     *
     * @param label   texto corto ("Lista para entrenar", "Hoy, suave"…)
     * @param reasons datos que lo motivan ("Dormiste 5 h"…)
     */
    public record Readiness(double factor, String label, List<String> reasons) {
    }

    /** Factor de volumen de hoy (1 si no hay check-in). */
    public double volumeFactor() {
        return readiness == null ? 1.0 : readiness.factor();
    }

    /** Factor de ritmo por altitud para running: el ritmo objetivo se hace más lento (≈ 1–6 %). */
    public double altitudePaceFactor() {
        if (altitudeM == null || altitudeM < 1000) return 1.0;
        if (altitudeM < 2000) return 1.02;
        if (altitudeM < 3000) return 1.04;
        return 1.06;
    }
}
