package com.huanjing.geo.module.content.service.adapter;

import org.springframework.stereotype.Component;

@Component
public class WechatMpPlatformScheduleAdapter implements SelfMediaPlatformScheduleAdapter {
    public static final String PLATFORM = "wechat_mp";

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public SelfMediaPlatformScheduleRules scheduleRules(String strategy) {
        return SelfMediaPlatformScheduleRules.defaults();
    }

    @Override
    public SelfMediaPlatformCapabilityContract capabilityContract() {
        return new SelfMediaPlatformCapabilityContract(
                platform(),
                "微信公众号",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        );
    }
}
