package com.huanjing.geo.module.content.service;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

final class ArticleGenerationFailureClassifier {
    private static final Pattern HTTP_SERVER_ERROR = Pattern.compile("\\bhttp\\s+5\\d{2}\\b");

    private ArticleGenerationFailureClassifier() {
    }

    static boolean isInfrastructureFailure(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return false;
        }
        String normalized = errorMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("http 429")
                || normalized.contains("too many requests")
                || normalized.contains("rate limit")
                || normalized.contains("permit unavailable")
                || normalized.contains("permit busy")
                || normalized.contains("connection refused")
                || normalized.contains("connection reset")
                || normalized.contains("connection closed")
                || normalized.contains("connectexception")
                || normalized.contains("socketexception")
                || HTTP_SERVER_ERROR.matcher(normalized).find();
    }
}
