package com.sportacus.backend.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ExerciseMediaServiceTest {

    @Test
    void normalizaTildesYSimbolos() {
        assertEquals("sentadilla bulgara", ExerciseMediaService.normalize("Sentadilla Búlgara!"));
    }

    @Test
    void stemsIgnoranConectoresYPlurales() {
        Set<String> a = ExerciseMediaService.stems("Sentadilla con peso corporal");
        Set<String> b = ExerciseMediaService.stems("Sentadillas del peso");
        assertTrue(a.contains("senta") && a.contains("peso"));
        assertTrue(b.contains("senta"));
        assertEquals("senta", ExerciseMediaService.firstStem("Sentadilla con salto"));
    }

    @Test
    void pasosDesdeListasHtml() {
        List<String> steps = ExerciseMediaService.steps("<ol><li>Posición: de pie.</li><li>Baja &amp; sube.</li></ol>");
        assertEquals(List.of("Posición: de pie.", "Baja & sube."), steps);
    }

    @Test
    void pasosDesdeParrafosSeSeparanPorFrases() {
        // Con un solo párrafo, la primera frase pasa a ser la descripción y el resto, los pasos.
        var text = ExerciseMediaService.describe("<p>Primera frase. Segunda frase.</p>");
        assertEquals("Primera frase.", text.description());
        assertEquals(List.of("Segunda frase."), text.steps());
    }
}
