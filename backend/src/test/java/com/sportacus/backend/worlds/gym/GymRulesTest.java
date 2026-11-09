package com.sportacus.backend.worlds.gym;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.world.core.Prescription;

class GymRulesTest {

    @Test
    void splitSegunDias() {
        assertEquals(List.of("full_a", "full_b"), GymWorld.split(2));
        assertEquals(3, GymWorld.split(3).size());
        assertEquals(List.of("torso", "pierna", "torso", "pierna"), GymWorld.split(4));
        assertEquals(6, GymWorld.split(9).size());
    }

    @Test
    void epleyEstimaUnaRepeticionMaxima() {
        assertEquals(100.0, GymWorld.epley(100, 1), 0.001);
        assertEquals(116.67, GymWorld.epley(100, 5), 0.01);
    }

    @Test
    void redondeoAlIncremento() {
        assertEquals(42.5, GymWorld.roundTo(43.4, 2.5), 0.001);
        assertEquals(45.0, GymWorld.roundTo(43.8, 2.5), 0.001);
    }

    @Test
    void zonasDelRadar() {
        assertEquals("brazos", GymWorld.zoneOf("triceps"));
        assertEquals("piernas", GymWorld.zoneOf("isquiotibiales"));
        assertEquals("", GymWorld.zoneOf("desconocido"));
    }

    @Test
    void duracionEstimadaDeBloques() {
        assertEquals(3 * 30 + 2 * 60, Prescription.reps(3, 10, 60).estimatedSeconds());
        assertEquals(8 * (60 + 90), Prescription.interval(8, 60, 90, "suave").estimatedSeconds());
    }
}
