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

}
