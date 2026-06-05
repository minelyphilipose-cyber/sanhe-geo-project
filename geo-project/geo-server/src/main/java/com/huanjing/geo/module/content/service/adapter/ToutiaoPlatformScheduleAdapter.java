package com.huanjing.geo.module.content.service.adapter;

import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class ToutiaoPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    private static final int DEFAULT_FILL_LEAD_MINUTES = 10;
    private static final int PLATFORM_SCHEDULE_FILL_LEAD_MINUTES = 130;
    private static final int PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES = 120;
    private static final int PLATFORM_SCHEDULE_MAX_ATTEMPTS = 4;

    @Override
    public String platform() {
        return ToutiaoSemiAutoAdapter.PLATFORM;
    }

    @Override
    public SelfMediaPlatformScheduleRules scheduleRules(String strategy) {
        if (SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE.equals(normalize(strategy))) {
            return new SelfMediaPlatformScheduleRules(
                    PLATFORM_SCHEDULE_FILL_LEAD_MINUTES,
                    PLATFORM_SCHEDULE_MIN_REMAINING_MINUTES,
                    PLATFORM_SCHEDULE_MAX_ATTEMPTS
            );
        }
        return new SelfMediaPlatformScheduleRules(DEFAULT_FILL_LEAD_MINUTES, 0, 1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
