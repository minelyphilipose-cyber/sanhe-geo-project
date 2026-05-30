package com.huanjing.geo.common.log;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class HttpLogSanitizer {

    private static final Set<String> MASK_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            "x-api-key",
            "x-access-token",
            "x-refresh-token",
            "x-ext-token",
            "x-geo-helper-access",
            "x-geo-helper-signature",
            "cookie",
            "set-cookie"
    );

    private static final Pattern[] JSON_MASK_PATTERNS = new Pattern[]{
            Pattern.compile("(\"password\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"newPassword\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"token\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"refreshToken\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"accessToken\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"helperAccessToken\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"backendToken\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"fillToken\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"hmacSecret\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"pairingCode\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"cookie\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\"authorization\"\\s*:\\s*\")[^\"]*(\")", Pattern.CASE_INSENSITIVE)
    };

    private HttpLogSanitizer() {
    }

    public static Map<String, String> maskHeaders(Map<String, String> headers) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        headers.forEach((k, v) -> {
            if (k == null) {
                return;
            }
            String key = k.toLowerCase();
            if (MASK_HEADERS.contains(key)) {
                sanitized.put(k, "***");
            } else {
                sanitized.put(k, truncate(v, 300));
            }
        });
        return sanitized;
    }

    public static String maskBody(String body, String contentType) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String value = body;
        if (contentType != null && contentType.toLowerCase().contains("json")) {
            for (Pattern pattern : JSON_MASK_PATTERNS) {
                value = pattern.matcher(value).replaceAll("$1***$2");
            }
        }
        return truncate(value, 2000);
    }

    private static String truncate(String input, int maxLen) {
        if (input == null) {
            return null;
        }
        if (input.length() <= maxLen) {
            return input;
        }
        return input.substring(0, maxLen) + "...(truncated)";
    }
}
