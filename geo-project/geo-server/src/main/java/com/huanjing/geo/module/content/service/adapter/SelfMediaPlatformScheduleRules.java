package com.huanjing.geo.module.content.service.adapter;

public record SelfMediaPlatformScheduleRules(int fillLeadMinutes,
                                             int minRemainingMinutes,
                                             int maxAttempts) {
}
