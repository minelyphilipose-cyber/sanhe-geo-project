package com.huanjing.geo.module.extension.dto;

public record ExtensionVersionCheckResponse(
        boolean supported,
        boolean upgradeRequired,
        boolean upgradeRecommended,
        String minVersion,
        String recommendedVersion,
        String latestVersion,
        String downloadUrl,
        String warning
) {
}
