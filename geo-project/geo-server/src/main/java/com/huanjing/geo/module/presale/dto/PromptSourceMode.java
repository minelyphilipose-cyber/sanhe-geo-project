package com.huanjing.geo.module.presale.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum PromptSourceMode {
    TEMPLATE,
    LLM;

    @JsonCreator
    public static PromptSourceMode fromJson(String value) {
        if (value == null || value.isBlank()) {
            return TEMPLATE;
        }
        return Arrays.stream(values())
                .filter(mode -> mode.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown prompt source mode: " + value));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }
}
