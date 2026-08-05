package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PresaleWebExecutionContext(PresaleQueryWebMode mode,
                                         Map<String, ResolvedCompanionExecutionConfig> companions,
                                         List<AiPlatformConfig> reportPlatforms) {
    public PresaleWebExecutionContext(PresaleQueryWebMode mode,
                                      Map<String, ResolvedCompanionExecutionConfig> companions) {
        this(mode, companions, reportPlatformsFrom(companions));
    }

    public PresaleWebExecutionContext {
        mode = mode == null ? PresaleQueryWebMode.OFF : mode;
        companions = companions == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(companions));
        reportPlatforms = reportPlatforms == null ? List.of() : reportPlatforms.stream()
                .map(PresaleWebExecutionContext::copyReportPlatform)
                .toList();
    }

    public ResolvedCompanionExecutionConfig requireCompanion(String reportPlatformCode) {
        ResolvedCompanionExecutionConfig config = companions.get(reportPlatformCode);
        if (config == null) {
            throw new IllegalStateException("No web companion in execution context for " + reportPlatformCode);
        }
        return config;
    }

    public boolean usesWebQuery(String reportPlatformCode) {
        return mode.requiresWebQuery() && companions.containsKey(reportPlatformCode);
    }

    public CompanionIdentity identity(String reportPlatformCode) {
        ResolvedCompanionExecutionConfig config = companions.get(reportPlatformCode);
        return config == null ? null : CompanionIdentity.from(config);
    }

    private static AiPlatformConfig copyReportPlatform(AiPlatformConfig source) {
        AiPlatformConfig copy = new AiPlatformConfig();
        copy.setId(source.getId());
        copy.setPlatformCode(source.getPlatformCode());
        copy.setPlatformName(source.getPlatformName());
        copy.setChannelCode(source.getChannelCode());
        copy.setConcurrencyLimit(source.getConcurrencyLimit());
        return copy;
    }

    private static List<AiPlatformConfig> reportPlatformsFrom(
            Map<String, ResolvedCompanionExecutionConfig> companions) {
        if (companions == null || companions.isEmpty()) {
            return List.of();
        }
        return companions.values().stream().map(config -> {
            AiPlatformConfig platform = new AiPlatformConfig();
            platform.setPlatformCode(config.reportPlatformCode());
            platform.setPlatformName(config.reportPlatformName());
            platform.setChannelCode(config.channelCode());
            platform.setConcurrencyLimit(config.concurrencyLimit());
            return platform;
        }).toList();
    }
}
