package com.sportacus.backend.worlds.football;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.worlds.football.Microcycle.Input;
import com.sportacus.backend.worlds.football.Microcycle.Slot;
import com.sportacus.backend.worlds.football.Microcycle.Type;

class MicrocycleTest {

    private static Map<Integer, Type> byDay(List<Slot> slots) {
        return slots.stream().filter(s -> s.weekday() != null).collect(Collectors.toMap(Slot::weekday, Slot::type));
    }

    @Test
    void partidoSabadoSinEquipo() {
        var r = Microcycle.build(new Input(6, 1, 0, 4, List.of()));
        Map<Integer, Type> d = byDay(r.slots());
        assertEquals(Type.PARTIDO, d.get(6));
        assertEquals(Type.FUERZA, d.get(2));      // MD-4 martes
        assertEquals(Type.VELOCIDAD, d.get(4));   // MD-2 jueves
        assertEquals(Type.RESISTENCIA, d.get(3)); // MD-3 miércoles
        assertEquals(Type.RECUPERACION, d.get(7)); // MD+1 domingo
        assertEquals("MD-2 · Velocidad y técnica", r.slots().stream().filter(s -> s.type() == Type.VELOCIDAD)
                .findFirst().orElseThrow().title());
        assertEquals(0, r.droppedOwn());
    }

    @Test
    void conDosEntrenamientosLasPropiasComplementan() {
        var r = Microcycle.build(new Input(6, 1, 2, 3, List.of()));
        Map<Integer, Type> d = byDay(r.slots());
        assertEquals(Type.EQUIPO, d.get(3));
        assertEquals(Type.EQUIPO, d.get(4));
        assertEquals(Type.FUERZA, d.get(2));
        assertEquals(Type.RECUPERACION, d.get(7));
        assertEquals(Type.ACTIVACION, d.get(5));
        assertFalse(d.containsValue(Type.RESISTENCIA));
    }

    @Test
    void dosPartidosComprimenLaSemana() {
        var r = Microcycle.build(new Input(6, 2, 0, 3, List.of()));
        Map<Integer, Type> d = byDay(r.slots());
        assertEquals(Type.PARTIDO, d.get(3));
        assertFalse(d.containsValue(Type.FUERZA)); // nunca a menos de 3 días de un partido
        assertTrue(d.containsValue(Type.RECUPERACION));
    }

    @Test
    void sinPartidoFijoRepartoGenerico() {
        var r = Microcycle.build(new Input(null, 1, 1, 3, List.of(1, 3, 5, 6)));
        assertEquals(5, r.slots().size()); // 3 propias + 1 equipo + partido sin día
        Slot match = r.slots().stream().filter(s -> s.type() == Type.PARTIDO).findFirst().orElseThrow();
        assertNull(match.weekday());
        assertEquals(Type.FUERZA, r.slots().get(0).type());
    }

    @Test
    void equipoNoOcupaMdMas1SiHayOtrosDias() {
        var r = Microcycle.build(new Input(6, 2, 3, 2, List.of()));
        Map<Integer, Type> d = byDay(r.slots());
        assertFalse(d.get(7) == Type.EQUIPO || d.get(4) == Type.EQUIPO);
        assertEquals(Type.RECUPERACION, d.get(7));
    }

    @Test
    void etiquetasMd() {
        assertEquals("MD+1", Microcycle.label(7, List.of(6)));
        assertEquals("MD-4", Microcycle.label(2, List.of(6)));
        assertEquals("MD-1", Microcycle.label(5, List.of(6)));
        assertEquals(7, Microcycle.wrap(0));
    }
}
