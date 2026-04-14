package com.huanjing.geo.module.dispatch.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DispatchScheduleCalculatorTest {

    @Test
    void shouldMatchBiDailyRuleByModulo() {
        LocalDate activated = LocalDate.of(2026, 1, 1);
        assertTrue(DispatchScheduleCalculator.isBiDailyDue(activated, LocalDate.of(2026, 1, 1)));
        assertFalse(DispatchScheduleCalculator.isBiDailyDue(activated, LocalDate.of(2026, 1, 2)));
        assertTrue(DispatchScheduleCalculator.isBiDailyDue(activated, LocalDate.of(2026, 1, 3)));
    }

    @Test
    void shouldComputeFirstMondayAndBiweeklyCadence() {
        LocalDate activated = LocalDate.of(2026, 1, 7); // Wednesday
        LocalDate anchor = DispatchScheduleCalculator.firstBiweeklyMonday(activated);
        assertEquals(LocalDate.of(2026, 1, 12), anchor);
        assertTrue(DispatchScheduleCalculator.isBiweeklyDue(anchor, LocalDate.of(2026, 1, 12)));
        assertFalse(DispatchScheduleCalculator.isBiweeklyDue(anchor, LocalDate.of(2026, 1, 19)));
        assertTrue(DispatchScheduleCalculator.isBiweeklyDue(anchor, LocalDate.of(2026, 1, 26)));
    }
}
