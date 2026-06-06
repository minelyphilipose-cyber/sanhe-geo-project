package com.huanjing.geo.module.presale.generate.narrative;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conservative normalizer for free-text industry keys.
 * Do not do semantic merges here. Similar industries can map to the same bucket
 * through approved mappings without risking over-normalization.
 */
public final class IndustryKeyNormalizer {

    private static final Pattern WHITESPACE_AND_PUNCT = Pattern.compile("[\\s　,，.。;；:：、/\\\\|_-]+");
    private static final Pattern TRAILING_GENERIC_SUFFIX = Pattern.compile("(行业|服务)$");

    private IndustryKeyNormalizer() {
    }

    public static String normalize(String industry) {
        if (!StringUtils.hasText(industry)) {
            return "";
        }
        if ("_ALL_".equalsIgnoreCase(industry.trim())) {
            return "_all_";
        }
        String key = WHITESPACE_AND_PUNCT.matcher(industry.trim().toLowerCase(Locale.ROOT)).replaceAll("");
        key = TRAILING_GENERIC_SUFFIX.matcher(key).replaceAll("");
        return key;
    }
}
