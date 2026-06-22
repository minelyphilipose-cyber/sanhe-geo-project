package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.SelfMediaScheduleCapabilityUpsertRequest;
import com.huanjing.geo.module.content.entity.SelfMediaScheduleCapability;
import com.huanjing.geo.module.content.mapper.SelfMediaScheduleCapabilityMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleMode;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.vo.SelfMediaScheduleCapabilityVO;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaScheduleCapabilityServiceTest {
    private SelfMediaScheduleCapabilityMapper mapper;
    private SelfMediaPlatformScheduleAdapterRouter adapterRouter;
    private SelfMediaScheduleCapabilityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SelfMediaScheduleCapabilityMapper.class);
        adapterRouter = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        service = new SelfMediaScheduleCapabilityService(mapper, currentUserService, adapterRouter, new ObjectMapper());
    }

    @Test
    void upsertVerifiedPlatformScheduleRequiresDelayRangeAndStoresVerifier() {
        SelfMediaScheduleCapabilityUpsertRequest request = verifiedRequest();
        when(adapterRouter.contract("toutiao")).thenReturn(Optional.of(toutiaoContract()));

        SelfMediaScheduleCapabilityVO response = service.upsert(request);

        assertEquals("toutiao", response.getPlatform());
        assertEquals("verified", response.getVerificationStatus());
        assertTrue(response.getSupportsSchedule());
        assertEquals("platform_schedule", response.getV1Strategy());
        assertEquals(99L, response.getVerifiedBy());
        assertNotNull(response.getVerifiedAt());
        verify(mapper).insert(any(SelfMediaScheduleCapability.class));
    }

    @Test
    void listIncludesContractPlatformsWithoutStoredRows() {
        when(mapper.selectList(any())).thenReturn(List.of());
        when(adapterRouter.contracts()).thenReturn(List.of(toutiaoContract()));
        when(adapterRouter.rules("toutiao", "platform_schedule")).thenReturn(new SelfMediaPlatformScheduleRules(130, 120, 4, 10080));

        List<SelfMediaScheduleCapabilityVO> capabilities = service.list();

        assertEquals(1, capabilities.size());
        SelfMediaScheduleCapabilityVO capability = capabilities.get(0);
        assertEquals("toutiao", capability.getPlatform());
        assertEquals("今日头条", capability.getDisplayName());
        assertEquals("unverified", capability.getVerificationStatus());
        assertFalse(capability.getSupportsSchedule());
        assertEquals("pending", capability.getV1Strategy());
        assertTrue(capability.getContractRequiresCoverUpload());
        assertTrue(capability.getContractSupportsLocation());
        assertEquals(130, capability.getFillLeadMinutes());
        assertEquals(120, capability.getMinRemainingMinutes());
        assertEquals(4, capability.getMaxAttempts());
        assertEquals(10080, capability.getMaxRemainingMinutes());
    }

    @Test
    void upsertRejectsVerifiedScheduleWithoutDelayRange() {
        SelfMediaScheduleCapabilityUpsertRequest request = verifiedRequest();
        request.setMinDelayMinutes(null);
        when(adapterRouter.contract("toutiao")).thenReturn(Optional.of(toutiaoContract()));

        BizException error = assertThrows(BizException.class, () -> service.upsert(request));

        assertEquals("DELAY_RANGE_REQUIRED", ((Map<?, ?>) error.getData()).get("code"));
    }

    @Test
    void readinessRequiresVerifiedPlatformScheduleStrategy() {
        when(mapper.selectByPlatform("zhihu")).thenReturn(capability("zhihu", "verified", true, "platform_schedule"));
        when(adapterRouter.contract("zhihu")).thenReturn(Optional.of(platformScheduleContract("zhihu", "知乎")));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("zhihu");

        assertTrue(readiness.ready());
        assertEquals("知乎", readiness.contract().displayName());
    }

    @Test
    void readinessRejectsWhenPlatformContractMissing() {
        when(mapper.selectByPlatform("zhihu")).thenReturn(capability("zhihu", "verified", true, "platform_schedule"));
        when(adapterRouter.contract("zhihu")).thenReturn(Optional.empty());

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("zhihu");

        assertFalse(readiness.ready());
        assertEquals("PLATFORM_CONTRACT_MISSING", readiness.code());
    }

    @Test
    void readinessRejectsNativeScheduleWhenContractDoesNotSupportIt() {
        when(mapper.selectByPlatform("zhihu")).thenReturn(capability("zhihu", "verified", true, "platform_schedule"));
        when(adapterRouter.contract("zhihu")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "zhihu",
                "知乎",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.UNSUPPORTED,
                SelfMediaPlatformScheduleRules.defaults(),
                true,
                false,
                false,
                true
        )));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("zhihu");

        assertFalse(readiness.ready());
        assertEquals("PLATFORM_SCHEDULE_UNSUPPORTED", readiness.code());
    }

    @Test
    void readinessAcceptsOfficialApiBackendDelayedWhenContractSupportsIt() {
        when(mapper.selectByPlatform("douyin"))
                .thenReturn(capability("douyin", "verified", true, "backend_delayed_publish"));
        when(adapterRouter.contract("douyin")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "douyin",
                "抖音图文",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("douyin");

        assertTrue(readiness.ready());
        assertEquals("抖音图文", readiness.contract().displayName());
    }

    @Test
    void readinessNormalizesWechatQuotaPlatformToWechatMpScheduleCapability() {
        when(mapper.selectByPlatform("wechat_mp"))
                .thenReturn(capability("wechat_mp", "verified", true, "backend_delayed_publish"));
        when(adapterRouter.contract("wechat_mp")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "wechat_mp",
                "微信公众号",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("wechat");

        assertTrue(readiness.ready());
        assertEquals("微信公众号", readiness.contract().displayName());
    }

    @Test
    void readinessNormalizesLegacyDouyinImageTextPlatformToDouyinScheduleCapability() {
        when(mapper.selectByPlatform("douyin"))
                .thenReturn(capability("douyin", "verified", true, "backend_delayed_publish"));
        when(adapterRouter.contract("douyin")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "douyin",
                "抖音图文",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("douyin_image_text");

        assertTrue(readiness.ready());
        assertEquals("抖音图文", readiness.contract().displayName());
    }

    @Test
    void readinessRejectsUnverifiedPlatform() {
        when(mapper.selectByPlatform("xiaohongshu"))
                .thenReturn(capability("xiaohongshu", "unverified", false, "pending"));

        SelfMediaScheduleCapabilityService.PlatformScheduleReadiness readiness = service.readiness("xiaohongshu");

        assertFalse(readiness.ready());
        assertEquals("PLATFORM_CAPABILITY_UNVERIFIED", readiness.code());
    }

    private SelfMediaScheduleCapabilityUpsertRequest verifiedRequest() {
        SelfMediaScheduleCapabilityUpsertRequest request = new SelfMediaScheduleCapabilityUpsertRequest();
        request.setPlatform("Toutiao");
        request.setVerificationStatus("verified");
        request.setSupportsSchedule(true);
        request.setMinDelayMinutes(10);
        request.setMaxDelayMinutes(10080);
        request.setV1Strategy("platform_schedule");
        request.setSupportsPublishCheck(true);
        return request;
    }

    private SelfMediaScheduleCapability capability(String platform,
                                                   String verificationStatus,
                                                   boolean supportsSchedule,
                                                   String strategy) {
        SelfMediaScheduleCapability row = new SelfMediaScheduleCapability();
        row.setPlatform(platform);
        row.setVerificationStatus(verificationStatus);
        row.setSupportsSchedule(supportsSchedule);
        row.setV1Strategy(strategy);
        return row;
    }

    private SelfMediaPlatformCapabilityContract toutiaoContract() {
        return platformScheduleContract("toutiao", "今日头条");
    }

    private SelfMediaPlatformCapabilityContract platformScheduleContract(String platform, String displayName) {
        return new SelfMediaPlatformCapabilityContract(
                platform,
                displayName,
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.PLATFORM_NATIVE,
                new SelfMediaPlatformScheduleRules(130, 120, 4),
                true,
                true,
                false,
                true
        );
    }
}
