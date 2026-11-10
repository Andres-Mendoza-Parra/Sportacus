package com.sportacus.backend.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.sportacus.backend.engagement.EngagementService.Streak;

class StreakDecayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Test
    void rachaCaducaTrasMasDeTresDias() {
        Streak s = new Streak(12, 20, TODAY.minusDays(30));
        Streak out = EngagementService.decay(s, TODAY);
        assertEquals(0, out.currentDays());
        assertEquals(20, out.longestDays());
        assertEquals(0, EngagementService.decay(new Streak(5, 5, TODAY.minusDays(4)), TODAY).currentDays());
    }

    @Test
    void rachaSeMantieneConDescansosCortos() {
        assertEquals(5, EngagementService.decay(new Streak(5, 9, TODAY.minusDays(3)), TODAY).currentDays());
        assertEquals(5, EngagementService.decay(new Streak(5, 9, TODAY), TODAY).currentDays());
        assertEquals(0, EngagementService.decay(new Streak(0, 0, null), TODAY).currentDays());
    }
}
