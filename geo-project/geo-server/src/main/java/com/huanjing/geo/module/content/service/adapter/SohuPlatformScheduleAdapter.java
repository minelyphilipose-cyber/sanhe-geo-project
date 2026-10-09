package com.huanjing.geo.module.content.service.adapter;

import org.springframework.stereotype.Component;

@Component
public class SohuPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    private static final SelfMediaPlatformScheduleRules RULES = new SelfMediaPlatformScheduleRules(0, 0, 1);

    @Override
    public String platform() {
        return "sohu";
    }

    @Override
    public SelfMediaPlatformScheduleRules scheduleRules(String strategy) {
        return RULES;
    }

    @Override
    public SelfMediaPlatformCapabilityContract capabilityContract() {
        return new SelfMediaPlatformCapabilityContract(platform(), "搜狐",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED, RULES,
                false, false, false, true, true, 3, 12);
    }
}
