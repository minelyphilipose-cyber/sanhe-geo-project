package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfMediaAccountPlatformEligibilityServiceTest {
    private BrandMapper brandMapper;
    private CompanyPackageBindingService bindingService;
    private SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private SelfMediaAccountPlatformEligibilityService service;

    @BeforeEach
    void setUp() {
        brandMapper = mock(BrandMapper.class);
        bindingService = mock(CompanyPackageBindingService.class);
        scheduleCapabilityService = mock(SelfMediaScheduleCapabilityService.class);
        service = new SelfMediaAccountPlatformEligibilityService(brandMapper, bindingService, scheduleCapabilityService);

        Brand brand = new Brand();
        brand.setId(10L);
        brand.setCompanyId(20L);
        when(brandMapper.selectById(10L)).thenReturn(brand);
    }

    @Test
    void marksPlatformEligibleOnlyWhenPackageQuotaAndScheduleCapabilityAreReady() {
        CompanyPackageBinding binding = activeBinding("""
                [
                  {"channelCode":"self_media:toutiao","periodType":"week","quotaLimit":3,"enabled":true},
                  {"channelCode":"self_media:baijiahao","periodType":"week","quotaLimit":0,"enabled":true}
                ]
                """);
        when(bindingService.activeBinding(20L)).thenReturn(binding);
        when(scheduleCapabilityService.readiness(anyString()))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(false, "PLATFORM_CAPABILITY_UNVERIFIED", "平台定时发布能力尚未完成验证", null));
        when(scheduleCapabilityService.readiness("toutiao"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(scheduleCapabilityService.readiness("baijiahao"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));

        var options = service.listByBrand(10L);

        var toutiao = options.stream().filter(item -> "toutiao".equals(item.getPlatform())).findFirst().orElseThrow();
        var baijiahao = options.stream().filter(item -> "baijiahao".equals(item.getPlatform())).findFirst().orElseThrow();
        var zhihu = options.stream().filter(item -> "zhihu".equals(item.getPlatform())).findFirst().orElseThrow();
        assertTrue(toutiao.getEligible());
        assertEquals("enabled", toutiao.getQuotaStatus());
        assertFalse(baijiahao.getEligible());
        assertEquals("quota_zero", baijiahao.getQuotaStatus());
        assertFalse(zhihu.getEligible());
        assertEquals("not_enabled", zhihu.getQuotaStatus());
    }

    @Test
    void mapsWechatQuotaPlatformToWechatMpScheduleCapability() {
        CompanyPackageBinding binding = activeBinding("""
                [
                  {"channelCode":"self_media:wechat","periodType":"week","quotaLimit":2,"enabled":true},
                  {"channelCode":"self_media:douyin","periodType":"week","quotaLimit":2,"enabled":true}
                ]
                """);
        when(bindingService.activeBinding(20L)).thenReturn(binding);
        when(scheduleCapabilityService.readiness(anyString()))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(false, "PLATFORM_CAPABILITY_UNVERIFIED", "平台定时发布能力尚未完成验证", null));
        when(scheduleCapabilityService.readiness("wechat_mp"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(scheduleCapabilityService.readiness("douyin"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));

        var options = service.listByBrand(10L);

        var wechat = options.stream().filter(item -> "wechat".equals(item.getPlatform())).findFirst().orElseThrow();
        var douyin = options.stream().filter(item -> "douyin".equals(item.getPlatform())).findFirst().orElseThrow();
        assertTrue(wechat.getEligible());
        assertTrue(wechat.getScheduleReady());
        assertTrue(douyin.getEligible());
        assertTrue(douyin.getScheduleReady());
    }

    @Test
    void requireEligibleRejectsPlatformOutsideIntersection() {
        CompanyPackageBinding binding = activeBinding("""
                [{"channelCode":"self_media:baijiahao","periodType":"week","quotaLimit":2,"enabled":true}]
                """);
        when(bindingService.activeBinding(20L)).thenReturn(binding);
        when(scheduleCapabilityService.readiness(anyString()))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(false, "PLATFORM_CAPABILITY_UNVERIFIED", "平台定时发布能力尚未完成验证", null));

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.huanjing.geo.common.exception.BizException.class,
                () -> service.requireEligible(10L, "baijiahao")
        );
        assertEquals("平台定时发布能力尚未完成验证", ex.getMessage());
    }

    private CompanyPackageBinding activeBinding(String snapshot) {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(20L);
        binding.setStatus(CompanyPackageBinding.STATUS_ACTIVE);
        binding.setActiveFlag(1);
        binding.setChannelQuotaSnapshot(snapshot);
        return binding;
    }
}
