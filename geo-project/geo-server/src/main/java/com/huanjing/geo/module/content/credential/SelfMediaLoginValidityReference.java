package com.huanjing.geo.module.content.credential;

import java.util.Locale;
import java.util.Set;

/**
 * Conservative reference periods for browser login credentials.
 *
 * <p>These values are estimates used for re-verification reminders. They are not platform
 * guarantees and must never be treated as proof that a login is valid or expired.</p>
 */
public final class SelfMediaLoginValidityReference {
    public static final int SHORT_REFERENCE_DAYS = 30;
    public static final int LONG_REFERENCE_DAYS = 90;

    private static final Set<String> SHORT_REFERENCE_PLATFORMS = Set.of("douyin", "xiaohongshu");

    private SelfMediaLoginValidityReference() {
    }

    public static int estimatedDays(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        return SHORT_REFERENCE_PLATFORMS.contains(normalized)
                ? SHORT_REFERENCE_DAYS
                : LONG_REFERENCE_DAYS;
    }
}
