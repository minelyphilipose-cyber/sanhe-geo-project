package com.huanjing.geo.common.util;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Locale;

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
        return switch (normalize(periodType)) {
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

    public static PeriodWindow periodWindow(String periodType, LocalDate anchorDate) {
        LocalDate anchor = anchorDate == null ? LocalDate.now(BUSINESS_ZONE) : anchorDate;
        return switch (normalize(periodType)) {
            case "day" -> new PeriodWindow(anchor, anchor);
            case "week" -> {
                LocalDate start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new PeriodWindow(start, start.plusDays(6));
            }
            case "month" -> {
                YearMonth month = YearMonth.from(anchor);
                yield new PeriodWindow(month.atDay(1), month.atEndOfMonth());
            }
            case "total" -> new PeriodWindow(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31));
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

    private static String normalize(String periodType) {
        return periodType == null ? "" : periodType.trim().toLowerCase(Locale.ROOT);
    }

    public record PeriodWindow(LocalDate start, LocalDate end) {
    }
}
