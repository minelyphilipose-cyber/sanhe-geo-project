package com.huanjing.geo.common.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;

public final class QuotaPeriodResolver {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    public static final String TOTAL_PERIOD_KEY = "TOTAL";

    private QuotaPeriodResolver() {
    }

    public static String periodKey(String periodType) {
        return periodKey(periodType, LocalDate.now(BUSINESS_ZONE));
    }

    public static String periodKey(String periodType, LocalDate date) {
        if (periodType == null || date == null) {
            throw new IllegalArgumentException("periodType and date are required");
        }
        return switch (periodType.trim().toLowerCase()) {
            case "day" -> date.toString();
            case "week" -> {
                int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
                int year = date.get(WeekFields.ISO.weekBasedYear());
                yield year + "-W" + String.format("%02d", week);
            }
            case "month" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case "total" -> TOTAL_PERIOD_KEY;
            default -> throw new IllegalArgumentException("Unsupported period_type: " + periodType);
        };
    }

    public static String periodKeyOrNull(String periodType) {
        try {
            return periodKey(periodType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
