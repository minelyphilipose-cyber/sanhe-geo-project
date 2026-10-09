package com.huanjing.geo.common.util;

public final class DatabaseText {
    private DatabaseText() {
    }

    public static String truncate(String value, int maxCharacters) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCharacters) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCharacters));
    }
}
