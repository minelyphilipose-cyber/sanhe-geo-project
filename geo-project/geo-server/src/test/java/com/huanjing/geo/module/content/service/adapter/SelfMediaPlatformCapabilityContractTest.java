package com.huanjing.geo.module.content.service.adapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfMediaPlatformCapabilityContractTest {

    @Test
    void zhihuUsesAdspowerBackendDelayedPublishWithOptionalCover() {
        SelfMediaPlatformCapabilityContract contract = new ZhihuPlatformScheduleAdapter().capabilityContract();

        assertTrue(contract.supportsAnySchedule());
        assertTrue(contract.supportsBackendDelayedPublish());
        assertFalse(contract.supportsPlatformSchedule());
        assertTrue(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION.equals(contract.publishChannel()));
        assertFalse(contract.requiresCoverUpload());
        assertFalse(contract.supportsLocation());
        assertFalse(contract.supportsPublishCheck());
        assertTrue(contract.publishCheckDelayMinutes() == 0);
        assertTrue(contract.publishCheckMaxAttempts() == 0);
    }

    @Test
    void xiaohongshuSupportsNativeScheduleLocationAndOneClickFormatWithoutCoverRequirement() {
        SelfMediaPlatformCapabilityContract contract = new XiaohongshuPlatformScheduleAdapter().capabilityContract();

        assertTrue(contract.supportsPlatformSchedule());
        assertTrue(contract.supportsLocation());
        assertTrue(contract.supportsOneClickFormat());
        assertTrue(contract.supportsPublishCheck());
        assertFalse(contract.requiresCoverUpload());
        assertTrue(contract.scheduleRules().minRemainingMinutes() >= 60);
        assertTrue(contract.scheduleRules().fillLeadMinutes() >= contract.scheduleRules().minRemainingMinutes() + 30);
        assertTrue(contract.scheduleRules().maxRemainingMinutes() >= 14 * 24 * 60);
    }

    @Test
    void baijiahaoSupportsNativeScheduleAndCoverWithoutLocation() {
        SelfMediaPlatformCapabilityContract contract = new BaijiahaoPlatformScheduleAdapter().capabilityContract();

        assertTrue(contract.supportsPlatformSchedule());
        assertTrue(contract.requiresCoverUpload());
        assertFalse(contract.supportsLocation());
        assertTrue(contract.supportsPublishCheck());
        assertTrue(contract.scheduleRules().minRemainingMinutes() >= 60);
        assertTrue(contract.scheduleRules().fillLeadMinutes() > contract.scheduleRules().minRemainingMinutes());
        assertTrue(contract.scheduleRules().maxRemainingMinutes() >= 7 * 24 * 60);
    }

    @Test
    void douyinImageTextUsesAdspowerBackendDelayedPublishAndCover() {
        SelfMediaPlatformCapabilityContract douyin = new DouyinPlatformScheduleAdapter().capabilityContract();

        assertTrue(douyin.supportsBackendDelayedPublish());
        assertFalse(douyin.supportsPlatformSchedule());
        assertTrue(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION.equals(douyin.publishChannel()));
        assertTrue(douyin.requiresCoverUpload());
        assertTrue(douyin.supportsPublishCheck());
        assertTrue(douyin.scheduleRules().minRemainingMinutes() == 0);
        assertTrue(douyin.scheduleRules().fillLeadMinutes() == 0);
        assertTrue(douyin.scheduleRules().maxAttempts() == 1);
    }

    @Test
    void wechatOfficialApiUsesBackendDelayedContract() {
        SelfMediaPlatformCapabilityContract wechat = new WechatMpPlatformScheduleAdapter().capabilityContract();

        assertTrue(wechat.supportsBackendDelayedPublish());
        assertFalse(wechat.requiresCoverUpload());
    }
}
