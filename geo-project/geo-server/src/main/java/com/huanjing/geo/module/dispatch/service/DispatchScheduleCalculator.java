package com.huanjing.geo.module.dispatch.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

}
