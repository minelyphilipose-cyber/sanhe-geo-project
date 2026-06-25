package com.huanjing.geo.common.util;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class EntityMatchTextNormalizer {
    private EntityMatchTextNormalizer() {
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.replaceAll("[\\s\\p{Punct}]+", "").toLowerCase(Locale.ROOT);
    }
}
