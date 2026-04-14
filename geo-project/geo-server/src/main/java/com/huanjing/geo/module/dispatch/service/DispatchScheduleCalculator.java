package com.huanjing.geo.module.dispatch.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public final class DispatchScheduleCalculator {

    private DispatchScheduleCalculator() {
    }

    public static boolean isBiDailyDue(LocalDate activatedDate, LocalDate today) {
        if (activatedDate == null || today.isBefore(activatedDate)) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(activatedDate, today);
        return days % 2 == 0;
    }

    public static LocalDate firstBiweeklyMonday(LocalDate activatedDate) {
        if (activatedDate == null) {
            return null;
        }
        return activatedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    public static boolean isBiweeklyDue(LocalDate anchorMonday, LocalDate today) {
        if (anchorMonday == null || today == null || today.getDayOfWeek() != DayOfWeek.MONDAY || today.isBefore(anchorMonday)) {
            return false;
        }
        long weeksBetween = ChronoUnit.WEEKS.between(anchorMonday, today);
        return weeksBetween % 2 == 0;
    }
}
