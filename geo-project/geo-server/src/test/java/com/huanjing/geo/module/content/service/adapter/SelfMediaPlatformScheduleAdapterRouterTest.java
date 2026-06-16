package com.huanjing.geo.module.content.service.adapter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPlatformScheduleAdapterRouterTest {

    @Test
    void findMapsQuotaAndLegacyAliasesToPublishAdapterPlatforms() {
        SelfMediaPlatformScheduleAdapterRouter router = new SelfMediaPlatformScheduleAdapterRouter(List.of(
                adapter("wechat_mp", SelfMediaPlatformPublishChannel.OFFICIAL_API),
                adapter("douyin", SelfMediaPlatformPublishChannel.OFFICIAL_API)
        ));

        assertTrue(router.find("wechat").isPresent());
        assertEquals("wechat_mp", router.contract("wechat").orElseThrow().platform());
        assertTrue(router.find("douyin_image_text").isPresent());
        assertEquals("douyin", router.contract("douyin_image_text").orElseThrow().platform());
    }

    private SelfMediaPlatformScheduleAdapter adapter(String platform, SelfMediaPlatformPublishChannel channel) {
        return new SelfMediaPlatformScheduleAdapter() {
            @Override
            public String platform() {
                return platform;
            }

            @Override
            public SelfMediaPlatformScheduleRules scheduleRules(String strategy) {
                return SelfMediaPlatformScheduleRules.defaults();
            }

            @Override
            public SelfMediaPlatformCapabilityContract capabilityContract() {
                return new SelfMediaPlatformCapabilityContract(
                        platform,
                        platform,
                        channel,
                        SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                        SelfMediaPlatformScheduleRules.defaults(),
                        false,
                        false,
                        false,
                        true
                );
            }
        };
    }
}
