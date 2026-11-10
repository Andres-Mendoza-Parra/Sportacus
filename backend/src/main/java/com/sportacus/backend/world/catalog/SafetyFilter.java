package com.sportacus.backend.world.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.catalog.CatalogModels.Contraindication;
import com.sportacus.backend.world.catalog.CatalogModels.MovementInfo;
import com.sportacus.backend.world.catalog.CatalogModels.WorldMovement;
import com.sportacus.backend.world.core.UserContext;
import com.sportacus.backend.world.core.UserProfileData;

/**
 * Exclusión DURA de movimientos contraindicados (corrige el hallazgo crítico de la auditoría, docs/12 §2.1:
 * antes era una penalización de puntaje). Un movimiento contraindicado no aparece "con puntaje bajo":
 * no aparece. Reglas:
 * <ol>
 * <li>Contraindicación de nivel "evitar" cuyo tag está entre los activos del usuario.</li>
 * <li>Impacto alto con lesión de miembro inferior, embarazo, osteoporosis, edad ≥ 65 o IMC ≥ 35.</li>
 * </ol>
 * Las de nivel "precaución" se muestran, pero el reproductor avisa (ver {@link #cautions}).
 */
@Component
public class SafetyFilter {

    public boolean allowed(MovementInfo m, UserContext user) {
        HealthSummary h = user.health();
        for (Contraindication c : m.contraindications()) {
            if ("evitar".equals(c.level()) && h.has(c.tag())) return false;
        }
        if ("alto".equals(m.impact()) && highImpactRestricted(user)) return false;
        return true;
    }

    public List<WorldMovement> filter(List<WorldMovement> movements, UserContext user) {
        return movements.stream().filter(wm -> allowed(wm.movement(), user)).toList();
    }

    /** Etiquetas de precaución que aplican al usuario para este movimiento. */
    public List<String> cautions(MovementInfo m, UserContext user) {
        return m.contraindications().stream()
                .filter(c -> "precaucion".equals(c.level()) && user.health().has(c.tag()))
                .map(Contraindication::tag).toList();
    }

    public boolean highImpactRestricted(UserContext user) {
        HealthSummary h = user.health();
        UserProfileData p = user.profile();
        Double bmi = p.bmi();
        return !h.vigorousAllowed() || h.hasLowerLimbIssue()
                || h.hasAny("embarazo", "osteoporosis", "postoperatorio", "movilidad_reducida")
                || p.ageOr(30) >= 65 || (bmi != null && bmi >= 35);
    }

    /** Equipamiento al que el usuario tiene acceso (lo que tiene + lo que implica su entorno). */
    public static Set<String> availableEquipment(UserProfileData p) {
        Set<String> out = new HashSet<>(p.equipment() == null ? List.of() : p.equipment());
        if (p.hasEnvironment("gimnasio")) {
            out.addAll(List.of("barra_olimpica", "mancuernas", "kettlebell", "banco", "rack", "polea", "maquina",
                    "barra_dominadas", "paralelas", "colchoneta", "bandas", "bicicleta", "foam_roller", "escalon",
                    "balon_medicinal", "cuerda"));
        }
        if (p.hasEnvironment("parque")) {
            out.addAll(List.of("barra_dominadas", "paralelas", "banco"));
        }
        if (p.hasEnvironment("cancha")) {
            out.add("porteria");
        }
        out.add("silla"); // casi cualquier persona tiene una silla firme
        return out;
    }

    /** El usuario tiene todo el equipamiento que el movimiento exige. */
    public static boolean hasEquipment(MovementInfo m, Set<String> available) {
        return available.containsAll(m.equipment());
    }
}
