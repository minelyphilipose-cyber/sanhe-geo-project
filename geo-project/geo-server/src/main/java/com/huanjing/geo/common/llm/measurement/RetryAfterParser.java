package com.huanjing.geo.common.llm.measurement;

import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RetryAfterParser {
    private RetryAfterParser() {
    }

    public static Long parse(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "retry-after".equals(entry.getKey().toLowerCase(Locale.ROOT))) {
                List<String> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    return null;
                }
                return parse(values.get(0));
            }
        }
        return null;
    }

    public static Long parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Math.max(0L, seconds) * 1000L;
        } catch (NumberFormatException ignored) {
            // Try HTTP-date below.
        }
        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            long millis = Duration.between(ZonedDateTime.now(retryAt.getZone()), retryAt).toMillis();
            return Math.max(0L, millis);
        } catch (Exception ignored) {
            return null;
        }
    }
}
