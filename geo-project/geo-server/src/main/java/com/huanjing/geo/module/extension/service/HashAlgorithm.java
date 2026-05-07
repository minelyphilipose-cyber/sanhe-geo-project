package com.huanjing.geo.module.extension.service;

import java.util.Arrays;

public enum HashAlgorithm {
    SHA_256("SHA-256", "SHA-256");

    private final String dbValue;
    private final String javaName;

    HashAlgorithm(String dbValue, String javaName) {
        this.dbValue = dbValue;
        this.javaName = javaName;
    }

    public String dbValue() {
        return dbValue;
    }

    public String javaName() {
        return javaName;
    }

    public static HashAlgorithm fromDbValue(String dbValue) {
        return Arrays.stream(values())
                .filter(value -> value.dbValue.equals(dbValue))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("unsupported hash algorithm: " + dbValue));
    }
}
