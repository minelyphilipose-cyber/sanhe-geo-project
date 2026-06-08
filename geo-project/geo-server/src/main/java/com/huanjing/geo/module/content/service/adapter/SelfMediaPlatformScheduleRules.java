package com.huanjing.geo.module.content.service.adapter;

public record SelfMediaPlatformScheduleRules(int fillLeadMinutes,
                                             int minRemainingMinutes,
                                             int maxAttempts) {
    private static final SelfMediaPlatformScheduleRules DEFAULTS = new SelfMediaPlatformScheduleRules(10, 0, 1);

    public static SelfMediaPlatformScheduleRules defaults() {
        return DEFAULTS;
    }
}
