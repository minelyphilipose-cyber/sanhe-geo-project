package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCalendarServiceTest {

    private final BusinessCalendarService service = new BusinessCalendarService(new ObjectMapper());

    @Test
    void publishDaysExcludeWeekendsHolidaysAndAdjustedWorkdaysByDefault() {
        List<BusinessCalendarService.BusinessDay> days = service.publishDays(YearMonth.of(2026, 6), false);

        assertFalse(days.isEmpty());
        assertTrue(days.stream().noneMatch(day -> day.date().getDayOfWeek() == DayOfWeek.SATURDAY));
        assertTrue(days.stream().noneMatch(day -> day.date().getDayOfWeek() == DayOfWeek.SUNDAY));
        assertTrue(days.stream().noneMatch(day -> "端午节".equals(day.dayName())));
        assertTrue(days.stream().allMatch(day -> day.windows().size() == 2));
    }

    @Test
    void selectEvenlySpreadsThirteenSlotsAcrossWorkingDays() {
        List<BusinessCalendarService.PublishSlot> slots = service.selectEvenly(YearMonth.of(2026, 6), 13, false);

        assertEquals(13, slots.size());
        assertEquals(13, slots.stream().map(BusinessCalendarService.PublishSlot::date).distinct().count());
        assertTrue(slots.stream().allMatch(slot ->
                slot.plannedAt().toLocalTime().equals(LocalTime.of(9, 15))
                        || slot.plannedAt().toLocalTime().equals(LocalTime.of(14, 30))));
        assertTrue(slots.stream().noneMatch(slot -> "端午节".equals(slot.dayName())));
    }
}
