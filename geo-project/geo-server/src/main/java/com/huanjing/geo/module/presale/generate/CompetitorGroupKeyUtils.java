package com.huanjing.geo.module.presale.generate;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class CompetitorGroupKeyUtils {
    public static final String SEPARATOR = "、";

    private CompetitorGroupKeyUtils() {
    }

    /**
     * Storage key keeps Top competitor rank order because this is what users see in generated prompts.
     */
    public static String storageKey(List<String> competitors) {
        if (competitors == null || competitors.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String item : competitors) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            ordered.add(item.trim());
        }
        return String.join(SEPARATOR, ordered);
    }

    public static List<String> split(String competitorName) {
        if (!StringUtils.hasText(competitorName)) {
            return List.of();
        }
        String[] parts = competitorName.split(SEPARATOR);
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            out.add(part.trim());
        }
        return out;
    }

    /**
     * Reuse key canonicalizes group members so "A、B、C" and "B、 A、C" reuse the same rows.
     * Single-competitor historical rows keep their original trim-only semantics.
     */
    public static String reuseKey(String competitorName) {
        List<String> parts = split(competitorName);
        if (parts.size() <= 1) {
            return StringUtils.hasText(competitorName) ? competitorName.trim() : "";
        }
        return parts.stream()
                .sorted(Comparator.naturalOrder())
                .distinct()
                .reduce((left, right) -> left + SEPARATOR + right)
                .orElse("");
    }
}
