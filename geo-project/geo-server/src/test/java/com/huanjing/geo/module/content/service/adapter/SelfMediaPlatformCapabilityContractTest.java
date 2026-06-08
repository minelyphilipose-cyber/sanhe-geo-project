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
    }

    @Test
    void xiaohongshuSupportsNativeScheduleLocationAndOneClickFormatWithoutCoverRequirement() {
        SelfMediaPlatformCapabilityContract contract = new XiaohongshuPlatformScheduleAdapter().capabilityContract();

        assertTrue(contract.supportsPlatformSchedule());
        assertTrue(contract.supportsLocation());
        assertTrue(contract.supportsOneClickFormat());
        assertFalse(contract.requiresCoverUpload());
    }

    @Test
    void baijiahaoSupportsNativeScheduleAndCoverWithoutLocation() {
        SelfMediaPlatformCapabilityContract contract = new BaijiahaoPlatformScheduleAdapter().capabilityContract();

        assertTrue(contract.supportsPlatformSchedule());
        assertTrue(contract.requiresCoverUpload());
        assertFalse(contract.supportsLocation());
    }

    @Test
    void officialApiPlatformsUseBackendDelayedContractUntilExecutorsAreImplemented() {
        SelfMediaPlatformCapabilityContract douyin = new DouyinPlatformScheduleAdapter().capabilityContract();
        SelfMediaPlatformCapabilityContract wechat = new WechatMpPlatformScheduleAdapter().capabilityContract();

        assertTrue(douyin.supportsBackendDelayedPublish());
        assertTrue(wechat.supportsBackendDelayedPublish());
        assertFalse(douyin.requiresCoverUpload());
        assertFalse(wechat.requiresCoverUpload());
    }
}
