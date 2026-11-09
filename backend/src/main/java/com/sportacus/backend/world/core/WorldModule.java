package com.sportacus.backend.world.core;

import java.util.List;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.world.form.FormSpec;

/**
 * Contrato de un mundo de Sportacus (docs/14 §5.1). Cada implementación es un bean de Spring en
 * {@code worlds/<mundo>} y contiene SOLO la lógica de su nicho; persistencia, seguridad, racha y
 * logros genéricos los resuelve el núcleo.
 */
public interface WorldModule {

    /** Clave estable (coincide con world.key del contenido). */
    String key();

    /** Preguntas propias del mundo para su onboarding. */
    FormSpec onboardingForm(UserContext user);

    /** Factores explicables de afinidad para el test de descubrimiento (base 40, se recorta a 0–100). */
    List<Factor> discoveryFactors(DiscoveryInput input);

    /** Procesa el onboarding: ruta, nivel, perfil, objetivos y escalones iniciales. */
    EnrollmentResult enroll(UserContext user, Answers answers);

    /** Construye el plan del usuario en este mundo (el núcleo aplica después el filtro de seguridad). */
    GeneratedPlan generatePlan(WorldContext ctx);

    /** Convierte una sesión registrada en métricas, progresión y ajustes. */
    SessionOutcome evaluateSession(WorldContext ctx, LoggedSession session);

    /** Reacciona a un test físico del mundo. */
    default AssessmentOutcome onAssessment(WorldContext ctx, String assessmentKey, double value) {
        return AssessmentOutcome.none();
    }

    /**
     * Reacciona al dolor registrado a las 24 h de una sesión (POST /world/sessions/{id}/next-day-pain). El núcleo ya
     * guarda la métrica {@code dolor_24h}; aquí el mundo puede ajustar fase, volumen o pedir un plan nuevo.
     */
    default SessionOutcome onNextDayPain(WorldContext ctx, java.util.UUID sessionLogId, int pain) {
        return SessionOutcome.empty();
    }

    /**
     * ¿Puede el usuario subir o bajar un escalón a mano (POST /world/progressions/{key}/step)? Los mundos donde la
     * progresión depende de reglas de seguridad (p. ej. Salud, guiada por el dolor) devuelven false.
     */
    default boolean allowsManualSteps() {
        return true;
    }

    /** Widgets del inicio del mundo. */
    List<Widget> dashboard(WorldContext ctx, PlanView plan);

    /** Widgets de la pantalla de progreso (histórico). */
    List<Widget> progress(WorldContext ctx);

    /** Configuración del reproductor de sesión. */
    SessionUi sessionUi();

    /** Contexto especializado para el asistente (tono, prioridades y límites del nicho). */
    String assistantPersona(WorldContext ctx);
}
