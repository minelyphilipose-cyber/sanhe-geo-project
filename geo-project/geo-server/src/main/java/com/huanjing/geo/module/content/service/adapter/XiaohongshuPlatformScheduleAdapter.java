package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class XiaohongshuPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    private static final int DEFAULT_FILL_LEAD_MINUTES = 10;
    private static final int PLATFORM_SCHEDULE_FILL_LEAD_MINUTES = 30;
    private static final int PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES = 0;
    private static final int PLATFORM_SCHEDULE_MAX_ATTEMPTS = 3;

    @Override
    public String platform() {
        return XiaohongshuSemiAutoAdapter.PLATFORM;
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
                "小红书",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.PLATFORM_NATIVE,
                platformScheduleRules(),
                false,
                true,
                true,
                true
        );
    }

    private SelfMediaPlatformScheduleRules platformScheduleRules() {
        return new SelfMediaPlatformScheduleRules(
                PLATFORM_SCHEDULE_FILL_LEAD_MINUTES,
                PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES,
                PLATFORM_SCHEDULE_MAX_ATTEMPTS
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
