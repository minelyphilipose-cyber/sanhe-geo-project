package com.huanjing.geo.module.presale.generate;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 竞品名确定性归一化。
 *
 * <p>normalizeKey 保持稳定、低风险,只做空白与大小写归一;别名归并必须在已知候选竞品集内做唯一匹配。</p>
 */
@Component
public class CompetitorNameNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BRACKET_CONTENT = Pattern.compile("[（(][^）)]*[）)]");
    private static final Pattern PUNCTUATION = Pattern.compile("[·•,，.。/／、|｜:：;；\\-—_]+");
    private static final List<String> GENERIC_SUFFIXES = List.of(
            "口腔医院",
            "口腔门诊部",
            "口腔诊所",
            "口腔科",
            "门诊部",
            "诊所",
            "医院",
            "口腔"
    );
    private static final List<String> COMMON_REGION_PREFIXES = List.of(
            "阜阳市",
            "阜阳"
    );
    private static final List<String> OVER_BROAD_CORES = List.of(
            "人民",
            "口腔医院",
            "口腔"
    );

    public String normalizeKey(String input) {
        if (input == null) {
            return "";
        }
        return WHITESPACE.matcher(input.trim()).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public Optional<String> matchDisplayName(String rawName, List<String> candidateDisplayNames) {
        if (!StringUtils.hasText(rawName) || candidateDisplayNames == null || candidateDisplayNames.isEmpty()) {
            return Optional.empty();
        }

        String rawKey = normalizeKey(rawName);
        for (String candidate : candidateDisplayNames) {
            if (rawKey.equals(normalizeKey(candidate))) {
                return Optional.of(candidate);
            }
        }

        String rawCore = distinctiveCore(rawName);
        if (!StringUtils.hasText(rawCore) || OVER_BROAD_CORES.contains(rawCore)) {
            return Optional.empty();
        }

        Map<String, List<String>> candidatesByCore = new LinkedHashMap<>();
        for (String candidate : candidateDisplayNames) {
            String core = distinctiveCore(candidate);
            if (!StringUtils.hasText(core)) {
                continue;
            }
            candidatesByCore.computeIfAbsent(core, ignored -> new ArrayList<>()).add(candidate);
        }

        List<String> exactCoreMatches = candidatesByCore.get(rawCore);
        if (exactCoreMatches != null && exactCoreMatches.size() == 1) {
            return Optional.of(exactCoreMatches.get(0));
        }

        List<String> containsMatches = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : candidatesByCore.entrySet()) {
            String candidateCore = entry.getKey();
            if (candidateCore.length() < 2 || rawCore.length() < 2 || entry.getValue().size() != 1) {
                continue;
            }
            if (rawCore.contains(candidateCore) || candidateCore.contains(rawCore)) {
                containsMatches.add(entry.getValue().get(0));
            }
        }
        return containsMatches.size() == 1 ? Optional.of(containsMatches.get(0)) : Optional.empty();
    }

    public String distinctiveCore(String input) {
        String key = normalizeKey(input);
        if (!StringUtils.hasText(key)) {
            return "";
        }
        key = BRACKET_CONTENT.matcher(key).replaceAll("");
        key = PUNCTUATION.matcher(key).replaceAll("");
        key = stripRegionPrefix(key);

        String withoutSuffix = stripGenericSuffixes(key);
        return StringUtils.hasText(withoutSuffix) ? withoutSuffix : key;
    }

    private String stripRegionPrefix(String value) {
        String out = value;
        for (String prefix : COMMON_REGION_PREFIXES) {
            if (out.startsWith(prefix) && out.length() > prefix.length()) {
                out = out.substring(prefix.length());
                break;
            }
        }
        return out;
    }

    private String stripGenericSuffixes(String value) {
        String out = value;
        boolean changed;
        do {
            changed = false;
            for (String suffix : GENERIC_SUFFIXES) {
                if (out.endsWith(suffix) && out.length() > suffix.length()) {
                    out = out.substring(0, out.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return out;
    }
}
