package com.huanjing.geo.module.content.constant;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class PlatformAccountIdentityPolicy {
    private PlatformAccountIdentityPolicy() {
    }

    public static String comparablePlatformAccountId(String platform, String platformAccountId) {
        String id = trimToNull(platformAccountId);
        if (!StringUtils.hasText(id) || isSyntheticPlatformAccountId(platform, id)) {
            return null;
        }
        return id;
    }

    public static boolean isSyntheticPlatformAccountId(String platform, String platformAccountId) {
        String id = trimToNull(platformAccountId);
        String normalizedPlatform = trimToNull(platform);
        if (!StringUtils.hasText(id) || !StringUtils.hasText(normalizedPlatform)) {
            return false;
        }
        return id.toLowerCase(Locale.ROOT)
                .startsWith("geo-" + normalizedPlatform.toLowerCase(Locale.ROOT) + "-");
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
