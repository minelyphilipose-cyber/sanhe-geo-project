package com.huanjing.geo.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuotaPeriodResolverTest {

    @Test
    void resolvesIsoWeekAcrossCalendarYearBoundary() {
        assertEquals("2025-W01", QuotaPeriodResolver.periodKey("week", LocalDate.of(2024, 12, 30)));
        assertEquals("2020-W53", QuotaPeriodResolver.periodKey("week", LocalDate.of(2021, 1, 1)));
        QuotaPeriodResolver.PeriodWindow window = QuotaPeriodResolver.periodWindow("week", LocalDate.of(2025, 1, 1));
        assertEquals(LocalDate.of(2024, 12, 30), window.start());
        assertEquals(LocalDate.of(2025, 1, 5), window.end());
    }

    @Test
    void resolvesMonthAtMonthBoundary() {
        assertEquals("2026-01", QuotaPeriodResolver.periodKey("month", LocalDate.of(2026, 1, 31)));
        assertEquals("2026-02", QuotaPeriodResolver.periodKey("month", LocalDate.of(2026, 2, 1)));
        QuotaPeriodResolver.PeriodWindow window = QuotaPeriodResolver.periodWindow("month", LocalDate.of(2026, 2, 1));
        assertEquals(LocalDate.of(2026, 2, 1), window.start());
        assertEquals(LocalDate.of(2026, 2, 28), window.end());
    }

    @Test
    void resolvesTotalToFixedKey() {
        assertEquals("TOTAL", QuotaPeriodResolver.periodKey("total", LocalDate.of(2026, 5, 9)));
        QuotaPeriodResolver.PeriodWindow window = QuotaPeriodResolver.periodWindow("total", LocalDate.of(2026, 5, 9));
        assertEquals(LocalDate.of(1970, 1, 1), window.start());
        assertEquals(LocalDate.of(9999, 12, 31), window.end());
    }
}
