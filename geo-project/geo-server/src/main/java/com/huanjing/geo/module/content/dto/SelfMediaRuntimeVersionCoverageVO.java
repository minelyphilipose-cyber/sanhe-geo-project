package com.huanjing.geo.module.content.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SelfMediaRuntimeVersionCoverageVO(
        LocalDateTime generatedAt,
        List<VersionCount> extensionVersions,
        List<VersionCount> helperVersions,
        int recentExtensionInstallCount,
        int recentHelperCount
) {
    public record VersionCount(String version, int count, LocalDateTime latestSeenAt) {
    }
}
