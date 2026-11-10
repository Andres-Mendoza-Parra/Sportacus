package com.sportacus.backend.worlds.football;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.common.Answers;
import com.sportacus.backend.health.HealthSummary;
import com.sportacus.backend.world.core.Factor;
import com.sportacus.backend.world.core.ProfileFacts;
import com.sportacus.backend.world.core.ProfileInsights;
import com.sportacus.backend.world.core.UserProfileData;
import com.sportacus.backend.world.core.Widget.Insight;

class FootballAdaptationsTest {

    private static final HealthSummary SCREENED = new HealthSummary(true, "bajo", List.of(), false, false, false,
            Set.of(), List.of(), null);
    private static final HealthSummary NEEDS_CLEARANCE = new HealthSummary(true, "moderado", List.of(), true, false,
            false, Set.of(), List.of(), null);

    private static ProfileInsights insights(boolean hrUnreliable, boolean avoidContact, boolean prefersGroup,
            boolean competitive) {
        return new ProfileInsights(ProfileFacts.empty(), null, null, hrUnreliable, false, avoidContact, false, false,
                false, false, false, false, false, prefersGroup, competitive, false, null, null, null, null, List.of(),
                null, null, null, null, null);
    }

    private static UserProfileData profileWith(List<String> equipment) {
        return new UserProfileData(null, null, null, null, null, 3, 60, List.of(), List.of(), equipment, true);
    }

    @Test
    void sinRespuestasOpcionalesSeExplicanLosValoresPorDefecto() {
        Answers a = new Answers(Map.of("f_level", "2", "f_context", "amigos", "f_matches", "1",
                "f_goals", List.of("tecnica")));
        List<String> keys = FootballAdaptations.missingDefaults(a, UserProfileData.empty()).stream()
                .map(Factor::key).toList();
        assertEquals(List.of("posicion", "lesion", "equipo", "material"), keys);
        assertEquals(List.of("balon"), FootballAdaptations.equipment(a, UserProfileData.empty()));
    }

    @Test
    void conRespuestasNoHayValoresPorDefecto() {
        Answers a = new Answers(Map.of("f_position", "portero", "f_injury", "no", "f_team_trainings", "2",
                "f_equipment", List.of("ninguno")));
        assertTrue(FootballAdaptations.missingDefaults(a, UserProfileData.empty()).isEmpty());
        assertTrue(FootballAdaptations.equipment(a, UserProfileData.empty()).isEmpty());
    }

    @Test
    void precargaElMaterialDelPerfil() {
        Map<String, Object> pre = FootballAdaptations.prefill(profileWith(List.of("balon", "mancuernas", "conos")), 2);
        assertEquals(2, pre.get("f_extra"));
        assertEquals(List.of("balon", "conos"), pre.get("f_equipment"));
        assertFalse(FootballAdaptations.prefill(profileWith(List.of()), 2).containsKey("f_equipment"));
    }

    @Test
    void modoPrudenteUsaElMensajeDelChequeo() {
        List<String> n = FootballAdaptations.intensityNotices(HealthSummary.unknown(), true);
        assertEquals(1, n.size());
        assertTrue(n.get(0).startsWith("Modo prudente: completa tu chequeo"));
        assertTrue(FootballAdaptations.restrictionRationale(HealthSummary.unknown()).startsWith("Modo prudente"));
    }

    @Test
    void avalPendienteYChequeoCompleto() {
        assertTrue(FootballAdaptations.intensityNotices(NEEDS_CLEARANCE, false).get(0).contains("aval médico"));
        assertTrue(FootballAdaptations.intensityNotices(SCREENED, false).isEmpty());
        assertFalse(FootballAdaptations.restrictionRationale(SCREENED).contains("Modo prudente"));
    }

    @Test
    void anticoaguladosEvitanContactoSaltosYCaidas() {
        assertTrue(FootballAdaptations.contactOrFallRisk("aereo"));
        assertTrue(FootballAdaptations.contactOrFallRisk("pliometria"));
        assertTrue(FootballAdaptations.contactOrFallRisk("portero_lateral"));
        assertFalse(FootballAdaptations.contactOrFallRisk("pase"));
        assertFalse(FootballAdaptations.contactOrFallRisk(null));
        ProfileInsights in = insights(false, true, false, false);
        assertTrue(FootballAdaptations.profileNotices(in).get(0).contains("anticoagulantes"));
        assertTrue(FootballAdaptations.planFactors(in).stream().anyMatch(f -> f.key().equals("contacto")));
    }

    @Test
    void betabloqueadoresSoloRpe() {
        ProfileInsights in = insights(true, false, false, false);
        assertTrue(FootballAdaptations.profileNotices(in).get(0).contains("RPE"));
        var p = new FootballWorld.Params(3, "recreativo", "campo", false, true, 60, Set.of(), false, false, true, false);
        String intensity = FootballWorld.prescriptionFor("fut_intervalos_4x4", p).intensity();
        assertFalse(intensity.contains("FC"));
        assertTrue(intensity.contains("RPE"));
    }

    @Test
    void sinPerfilNoHayAjustes() {
        ProfileInsights in = ProfileInsights.empty();
        assertTrue(FootballAdaptations.profileNotices(in).isEmpty());
        assertTrue(FootballAdaptations.planFactors(in).isEmpty());
        assertTrue(FootballAdaptations.profileTips(in).isEmpty());
    }

    @Test
    void consejosPorCompetirYGrupo() {
        List<String> titles = FootballAdaptations.profileTips(insights(false, false, true, true)).stream()
                .map(Insight::title).toList();
        assertEquals(List.of("Compite contigo", "Entrena acompañado"), titles);
    }

    @Test
    void lesionDesconocidaActivaPrevencionYPosicionGenerica() {
        var p = new FootballWorld.Params(2, "recreativo", "campo", false, true, 60, Set.of(), false, true, false, false);
        assertTrue(p.prevention());
        assertEquals("Jugador de campo", FootballWorld.positionName(FootballAdaptations.GENERIC_POSITION));
        assertEquals("Mediocampista", FootballWorld.positionName("mediocampista"));
    }
}
