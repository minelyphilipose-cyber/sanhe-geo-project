package com.huanjing.geo.module.content.service.adapter;

public record SelfMediaPlatformScheduleRules(int fillLeadMinutes,
                                             int minRemainingMinutes,
                                             int maxAttempts,
                                             int maxRemainingMinutes) {
    private static final SelfMediaPlatformScheduleRules DEFAULTS = new SelfMediaPlatformScheduleRules(10, 0, 1, 0);

    public SelfMediaPlatformScheduleRules(int fillLeadMinutes, int minRemainingMinutes, int maxAttempts) {
        this(fillLeadMinutes, minRemainingMinutes, maxAttempts, 0);
    }

    public static SelfMediaPlatformScheduleRules defaults() {
        return DEFAULTS;
    }
}
