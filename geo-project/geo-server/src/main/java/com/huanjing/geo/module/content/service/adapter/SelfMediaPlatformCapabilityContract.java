package com.huanjing.geo.module.content.service.adapter;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record SelfMediaPlatformCapabilityContract(
        String platform,
        String displayName,
        SelfMediaPlatformPublishChannel publishChannel,
        SelfMediaPlatformScheduleMode scheduleMode,
        SelfMediaPlatformScheduleRules scheduleRules,
        boolean requiresCoverUpload,
        boolean supportsLocation,
        boolean supportsOneClickFormat,
        boolean supportsPublishCheck,
        boolean requiresPublishedUrl,
        int publishCheckDelayMinutes,
        int publishCheckMaxAttempts
) {
    public SelfMediaPlatformCapabilityContract(String platform,
                                               String displayName,
                                               SelfMediaPlatformPublishChannel publishChannel,
                                               SelfMediaPlatformScheduleMode scheduleMode,
                                               SelfMediaPlatformScheduleRules scheduleRules,
                                               boolean requiresCoverUpload,
                                               boolean supportsLocation,
                                               boolean supportsOneClickFormat,
                                               boolean supportsPublishCheck) {
        this(platform, displayName, publishChannel, scheduleMode, scheduleRules,
                requiresCoverUpload, supportsLocation, supportsOneClickFormat, supportsPublishCheck,
                true, 60, Math.max(1, scheduleRules == null ? 1 : scheduleRules.maxAttempts()));
    }

    public boolean supportsPlatformSchedule() {
        return SelfMediaPlatformScheduleMode.PLATFORM_NATIVE.equals(scheduleMode);
    }

    public boolean supportsBackendDelayedPublish() {
        return SelfMediaPlatformScheduleMode.BACKEND_DELAYED.equals(scheduleMode);
    }

    public boolean supportsAnySchedule() {
        return !SelfMediaPlatformScheduleMode.UNSUPPORTED.equals(scheduleMode);
    }

    public static SelfMediaPlatformCapabilityContract unsupported(String platform) {
        String normalized = StringUtils.hasText(platform) ? platform.trim().toLowerCase(Locale.ROOT) : "unknown";
        return new SelfMediaPlatformCapabilityContract(
                normalized,
                normalized,
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.UNSUPPORTED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                false,
                true,
                60,
                6
        );
    }
}
