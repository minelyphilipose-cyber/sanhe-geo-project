package com.huanjing.geo.module.presale.dto;

public enum AttributionMode {
    STANDARD,
    DEALER;

    public static AttributionMode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return STANDARD;
        }
    }
}
