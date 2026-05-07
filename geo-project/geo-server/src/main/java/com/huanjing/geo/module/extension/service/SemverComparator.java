package com.huanjing.geo.module.extension.service;

import org.springframework.util.StringUtils;

public final class SemverComparator {

    private SemverComparator() {
    }

    public static int parseToInt(String version) {
        if (!StringUtils.hasText(version)) {
            throw new IllegalArgumentException("version is required");
        }
        String normalized = version.trim();
        if (normalized.contains("-")) {
            throw new IllegalArgumentException("prerelease extension versions are not supported");
        }
        int buildIndex = normalized.indexOf('+');
        if (buildIndex >= 0) {
            normalized = normalized.substring(0, buildIndex);
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("version must be semantic version x.y.z");
        }
        int major = parsePart(parts[0], "major");
        int minor = parsePart(parts[1], "minor");
        int patch = parsePart(parts[2], "patch");
        if (major > 999 || minor > 99 || patch > 99) {
            throw new IllegalArgumentException("version part out of supported range");
        }
        return major * 10000 + minor * 100 + patch;
    }

    public static int compare(String left, String right) {
        return Integer.compare(parseToInt(left), parseToInt(right));
    }

    private static int parsePart(String value, String name) {
        if (!value.matches("\\d+")) {
            throw new IllegalArgumentException("version " + name + " is invalid");
        }
        return Integer.parseInt(value);
    }
}
