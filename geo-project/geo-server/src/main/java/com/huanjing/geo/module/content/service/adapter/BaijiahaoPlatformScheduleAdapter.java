package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class BaijiahaoPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    public static final String PLATFORM = "baijiahao";

    private static final int DEFAULT_FILL_LEAD_MINUTES = 10;
    private static final int PLATFORM_SCHEDULE_FILL_LEAD_MINUTES = 75;
    private static final int PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES = 60;
    private static final int PLATFORM_SCHEDULE_MAX_REMAINING_MINUTES = 7 * 24 * 60;
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
        return new SelfMediaPlatformScheduleRules(DEFAULT_FILL_LEAD_MINUTES, 0, 1);
    }

    @Override
    public SelfMediaPlatformCapabilityContract capabilityContract() {
        return new SelfMediaPlatformCapabilityContract(
                platform(),
                "百家号",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.PLATFORM_NATIVE,
                platformScheduleRules(),
                true,
                false,
                false,
                true
        );
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
