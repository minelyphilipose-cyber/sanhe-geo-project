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
        boolean supportsPublishCheck
) {
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
                false
        );
    }
}
