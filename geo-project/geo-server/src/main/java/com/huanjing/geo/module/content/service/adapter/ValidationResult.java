package com.huanjing.geo.module.content.service.adapter;

import lombok.Data;

import java.util.List;

@Data
public class ValidationResult {
    private boolean passed;
    private List<String> errors;

    public static ValidationResult pass() {
        ValidationResult result = new ValidationResult();
        result.passed = true;
        result.errors = List.of();
        return result;
    }

    public static ValidationResult fail(List<String> errors) {
        ValidationResult result = new ValidationResult();
        result.passed = false;
        result.errors = errors == null ? List.of("validation failed") : errors;
        return result;
    }
}
