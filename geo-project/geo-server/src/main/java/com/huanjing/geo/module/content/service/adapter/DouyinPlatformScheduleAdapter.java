package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DouyinPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    public static final String PLATFORM = "douyin";
    private static final int BACKEND_DELAYED_FILL_LEAD_MINUTES = 0;
    private static final int PLATFORM_SCHEDULE_FILL_LEAD_MINUTES = 130;
    private static final int PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES = 120;
    private static final int PLATFORM_SCHEDULE_MAX_REMAINING_MINUTES = 14 * 24 * 60;
    private static final int PLATFORM_SCHEDULE_MAX_ATTEMPTS = 3;

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public SelfMediaPlatformScheduleRules scheduleRules(String strategy) {
        if (SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))) {
            return platformScheduleRules();
        }
        return backendDelayedRules();
    }

    @Override
    public SelfMediaPlatformCapabilityContract capabilityContract() {
        return new SelfMediaPlatformCapabilityContract(
                platform(),
                "抖音图文",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                backendDelayedRules(),
                true,
                false,
                false,
                true,
                false,
                60,
                PLATFORM_SCHEDULE_MAX_ATTEMPTS
        );
    }

    private SelfMediaPlatformScheduleRules backendDelayedRules() {
        return new SelfMediaPlatformScheduleRules(BACKEND_DELAYED_FILL_LEAD_MINUTES, 0, 1);
    }

    private SelfMediaPlatformScheduleRules platformScheduleRules() {
        return new SelfMediaPlatformScheduleRules(
                PLATFORM_SCHEDULE_FILL_LEAD_MINUTES,
                PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES,
                PLATFORM_SCHEDULE_MAX_ATTEMPTS,
                PLATFORM_SCHEDULE_MAX_REMAINING_MINUTES
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
