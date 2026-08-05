package com.huanjing.geo.module.presale.generate.web;

import java.util.Locale;

public enum PresaleQueryWebMode {
    OFF,
    SHADOW,
    REQUIRED;

    public static PresaleQueryWebMode from(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean requiresWebQuery() {
        return this == REQUIRED;
    }
}
