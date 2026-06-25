package com.huanjing.geo.common.llm.router;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class LlmPlatformCodeFilters {
    private LlmPlatformCodeFilters() {
    }

    public static Set<String> parseCodes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split("[,，;；\\s]+"))
                .map(LlmPlatformCodeFilters::normalize)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean containsCode(String rawCodes, String platformCode) {
        String normalized = normalize(platformCode);
        return StringUtils.hasText(normalized) && parseCodes(rawCodes).contains(normalized);
    }

    public static String normalize(String platformCode) {
        return StringUtils.hasText(platformCode) ? platformCode.trim().toLowerCase(Locale.ROOT) : "";
    }
}
