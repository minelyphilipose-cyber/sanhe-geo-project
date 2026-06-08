package com.huanjing.geo.module.content.service.adapter;

import org.springframework.stereotype.Component;

@Component
public class ZhihuPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    private static final SelfMediaPlatformScheduleRules BACKEND_DELAYED_RULES =
            new SelfMediaPlatformScheduleRules(0, 0, 2);

    @Override
    public String platform() {
        return ZhihuSemiAutoAdapter.PLATFORM;
    }

    @Override
    public SelfMediaPlatformScheduleRules scheduleRules(String strategy) {
        return BACKEND_DELAYED_RULES;
    }

    @Override
    public SelfMediaPlatformCapabilityContract capabilityContract() {
        return new SelfMediaPlatformCapabilityContract(
                platform(),
                "知乎",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                BACKEND_DELAYED_RULES,
                false,
                false,
                false,
                true
        );
    }
}
