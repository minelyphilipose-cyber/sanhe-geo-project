package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.project.dto.PackageChannelQuotaConfigRequest;
import com.huanjing.geo.module.project.dto.PackagePlanUpdateRequest;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.mapper.PackageChannelQuotaConfigMapper;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagePlanServiceTest {

    private PackagePlanMapper packagePlanMapper;
    private PackageChannelQuotaConfigMapper channelQuotaConfigMapper;
    private CompanyPackageBindingService bindingService;
    private PackagePlanService service;

    @BeforeEach
    void setUp() {
        packagePlanMapper = mock(PackagePlanMapper.class);
        channelQuotaConfigMapper = mock(PackageChannelQuotaConfigMapper.class);
        bindingService = mock(CompanyPackageBindingService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        service = new PackagePlanService(
                packagePlanMapper,
                channelQuotaConfigMapper,
                bindingService,
                currentUserService
        );
        when(packagePlanMapper.selectById(3L)).thenReturn(plan());
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void savesExplicitSohuPeriodQuotaAndEnabledStateIndependentlyOfToutiao() {
        var toutiao = request("self_media:toutiao", "month", 12);
        var sohu = request("self_media:sohu", "week", 0);
        sohu.setEnabled(false);
        service.saveChannelQuotaConfigsByPlanId(3L, List.of(toutiao, sohu));

        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper, atLeastOnce()).insert(captor.capture());
        var saved = captor.getAllValues().stream()
                .filter(item -> "self_media:sohu".equals(item.getChannelCode())).findFirst().orElseThrow();
        assertEquals("week", saved.getPeriodType());
        assertEquals(0, saved.getQuotaLimit());
        assertFalse(saved.getEnabled());
        var savedToutiao = captor.getAllValues().stream()
                .filter(item -> "self_media:toutiao".equals(item.getChannelCode())).findFirst().orElseThrow();
        assertEquals("month", savedToutiao.getPeriodType());
        assertEquals(12, savedToutiao.getQuotaLimit());
        assertTrue(savedToutiao.getEnabled());
        assertEquals("week", sohu.getPeriodType());
        assertEquals(0, sohu.getQuotaLimit());
    }

    @Test
    void addsSohuToLegacyPackageInputAndMirrorsDisabledToutiao() {
        var toutiao = request("self_media:toutiao", "week", 3);
        toutiao.setEnabled(false);
        service.saveChannelQuotaConfigsByPlanId(3L, List.of(toutiao));

        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper, atLeastOnce()).insert(captor.capture());
        var saved = captor.getAllValues().stream()
                .filter(item -> "self_media:sohu".equals(item.getChannelCode())).findFirst().orElseThrow();
        assertEquals("week", saved.getPeriodType());
        assertEquals(3, saved.getQuotaLimit());
        assertFalse(saved.getEnabled());
    }

    @Test
    void independentSohuQuotaIncreasePreservesExistingCustomerSnapshots() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of(
                quota("self_media:toutiao", "month", 12, true),
                quota("self_media:sohu", "week", 1, true)));

        service.saveChannelQuotaConfigsByPlanId(3L, List.of(
                request("self_media:toutiao", "month", 12),
                request("self_media:sohu", "week", 2)));

        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper, atLeastOnce()).insert(captor.capture());
        var saved = captor.getAllValues().stream()
                .filter(item -> "self_media:sohu".equals(item.getChannelCode())).findFirst().orElseThrow();
        assertEquals("week", saved.getPeriodType());
        assertEquals(2, saved.getQuotaLimit());
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @Test
    void independentSohuQuotaCanDecreaseWithoutRefreshingExistingCustomerSnapshots() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota("self_media:sohu", "month", 13, true)));

        service.saveChannelQuotaConfigsByPlanId(3L, List.of(request("self_media:sohu", "month", 12)));
        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper).insert(captor.capture());
        assertEquals(12, captor.getValue().getQuotaLimit());
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @Test
    void saveChannelQuotaConfigsRejectsLegacySelfMediaAggregateChannel() {
        BizException ex = assertThrows(BizException.class,
                () -> service.saveChannelQuotaConfigsByPlanId(3L, List.of(request("self_media", "week", 1))));

        assertEquals("Unsupported channel_code: self_media", ex.getMessage());
    }

    @Test
    void saveChannelQuotaConfigsDefaultsContainSelfMediaPlatformChannelsOnly() {
        service.saveChannelQuotaConfigsByPlanId(3L, null);

        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper, atLeastOnce()).insert(captor.capture());
        Set<String> channels = captor.getAllValues().stream()
                .map(PackageChannelQuotaConfig::getChannelCode)
                .collect(Collectors.toSet());
        assertFalse(channels.contains("self_media"));
        for (String platform : ArticlePromptChannels.SELF_MEDIA_SUB_CODES) {
            assertTrue(channels.contains("self_media:" + platform));
        }
        assertEquals(12, channels.size());
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @Test
    void saveChannelQuotaConfigsAllowsQuotaDecreaseForBoundPackagePlan() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota("official_site", "month", 2, true)));

        service.saveChannelQuotaConfigsByPlanId(3L, List.of(request("official_site", "month", 1)));
        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getQuotaLimit());
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @Test
    void saveChannelQuotaConfigsAllowsActivePackagePlanWhenUnbound() {
        PackagePlan active = plan();
        active.setPackageStatus(PackagePlanService.STATUS_ACTIVE);
        when(packagePlanMapper.selectById(3L)).thenReturn(active);

        service.saveChannelQuotaConfigsByPlanId(3L, List.of(request("official_site", "month", 1)));

        verify(channelQuotaConfigMapper).delete(any());
    }

    @Test
    void updateAllowsActivePackagePlanWhenUnbound() {
        PackagePlan active = plan();
        active.setPackageStatus(PackagePlanService.STATUS_ACTIVE);
        when(packagePlanMapper.selectById(3L)).thenReturn(active);

        PackagePlanUpdateRequest request = updateRequest();
        request.setServiceMonths(6);
        request.setKeywordGroupLimit(5);
        request.setKeywordGroupLimitA(5);

        service.update(3L, request);

        verify(packagePlanMapper).updateById(active);
        assertEquals(6, active.getServiceMonths());
        assertEquals(5, active.getKeywordGroupLimit());
    }

    @Test
    void updateAllowsBoundPackagePlanWhenBenefitsExpand() {
        PackagePlan active = plan();
        active.setPackageStatus(PackagePlanService.STATUS_ACTIVE);
        when(packagePlanMapper.selectById(3L)).thenReturn(active);
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota("official_site", "month", 1, true)));
        PackagePlanUpdateRequest request = updateRequest();
        request.setServiceMonths(18);
        request.setKeywordGroupLimit(15);
        request.setKeywordGroupLimitA(15);
        request.setChannelQuotaConfigs(List.of(
                request("official_site", "month", 2),
                request("self_media:wechat", "week", 1)
        ));

        service.update(3L, request);

        verify(packagePlanMapper).updateById(active);
        assertEquals(18, active.getServiceMonths());
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @Test
    void updateRejectsBoundPackagePlanWhenQuestionBenefitDecreases() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        PackagePlanUpdateRequest request = updateRequest();
        request.setKeywordGroupLimit(9);
        request.setKeywordGroupLimitA(9);

        BizException ex = assertThrows(BizException.class, () -> service.update(3L, request));

        assertEquals("套餐已绑定客户，只允许扩大权益：拓词问题总数只能增加，不能从 10 调整为 9", ex.getMessage());
        verify(packagePlanMapper, never()).updateById(any());
    }

    @Test
    void saveChannelQuotaConfigsAllowsDisablingBoundChannel() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota("self_media:wechat", "week", 1, true)));
        PackageChannelQuotaConfigRequest disabled = request("self_media:wechat", "week", 1);
        disabled.setEnabled(false);

        service.saveChannelQuotaConfigsByPlanId(3L, List.of(disabled));
        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper).insert(captor.capture());
        assertFalse(captor.getValue().getEnabled());
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @ParameterizedTest
    @MethodSource("periodicChannels")
    void boundPackageChannelPeriodAndQuotaCanChangeThroughBothSaveEndpoints(String channel) {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota(channel, "month", 13, true)));
        var nextQuota = request(channel, "day", 3);

        service.saveChannelQuotaConfigsByPlanId(3L, List.of(nextQuota));
        var update = updateRequest();
        update.setChannelQuotaConfigs(List.of(nextQuota));
        service.update(3L, update);

        ArgumentCaptor<PackageChannelQuotaConfig> captor = ArgumentCaptor.forClass(PackageChannelQuotaConfig.class);
        verify(channelQuotaConfigMapper, atLeastOnce()).insert(captor.capture());
        var saved = captor.getAllValues().stream().filter(item -> channel.equals(item.getChannelCode())).toList();
        assertEquals(2, saved.size());
        for (var quota : saved) {
            assertEquals("day", quota.getPeriodType());
            assertEquals(3, quota.getQuotaLimit());
            assertTrue(quota.getEnabled());
        }
        verify(bindingService, never()).syncActiveBindingsForPackagePlan(3L);
    }

    @Test
    void boundPackageStillRejectsInvalidChannelQuotasBeforeWriting() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        assertThrows(BizException.class, () -> service.saveChannelQuotaConfigsByPlanId(3L,
                List.of(request("self_media:sohu", "day", -1))));
        var update = updateRequest();
        update.setChannelQuotaConfigs(List.of(request("self_media:sohu", "total", 3)));
        assertThrows(BizException.class, () -> service.update(3L, update));
        verify(channelQuotaConfigMapper, never()).delete(any());
        verify(packagePlanMapper, never()).updateById(any());
    }

    private static Stream<String> periodicChannels() {
        return Stream.concat(Stream.of("official_site", "industry_site", "forum"),
                ArticlePromptChannels.SELF_MEDIA_SUB_CODES.stream().map(platform -> "self_media:" + platform));
    }

    @Test
    void updateStatusRejectsDraftToInactive() {
        PackagePlan draft = plan();
        when(packagePlanMapper.selectById(3L)).thenReturn(draft);

        BizException ex = assertThrows(BizException.class, () -> service.updateStatus(3L, false));

        assertEquals("Draft package can only be published", ex.getMessage());
        verify(packagePlanMapper, never()).updateById(any());
    }

    @Test
    void updateStatusPublishesDraftPackage() {
        PackagePlan draft = plan();
        when(packagePlanMapper.selectById(3L)).thenReturn(draft);

        service.updateStatus(3L, true);

        assertTrue(draft.getEnabled());
        assertEquals(PackagePlanService.STATUS_ACTIVE, draft.getPackageStatus());
        verify(packagePlanMapper).updateById(draft);
    }

    private PackagePlan plan() {
        PackagePlan plan = new PackagePlan();
        plan.setId(3L);
        plan.setPackageType("trial");
        plan.setPackageName("Trial");
        plan.setAudienceType(PackagePlanService.AUDIENCE_INTERNAL);
        plan.setPackageStatus(PackagePlanService.STATUS_DRAFT);
        plan.setStandardPrice(new BigDecimal("1000.00"));
        plan.setServiceMonths(12);
        plan.setKeywordGroupLimit(10);
        plan.setKeywordGroupLimitA(10);
        plan.setKeywordGroupLimitB(0);
        plan.setKeywordGroupLimitC(0);
        plan.setMonthlyReportDepth("L1");
        plan.setQuarterlyReportDepth("L1");
        plan.setConsultantIntensity("L1");
        plan.setCompetitorInsightDepth("L1");
        plan.setMediaDistributionIntensity("L1");
        plan.setCommitmentTargetIntensity("L1");
        plan.setTargetMetricType("visibility_rate");
        plan.setTargetMetricValue(new BigDecimal("0.05"));
        plan.setTargetWindowDays(90);
        return plan;
    }

    private PackageChannelQuotaConfigRequest request(String channelCode, String periodType, int quotaLimit) {
        PackageChannelQuotaConfigRequest req = new PackageChannelQuotaConfigRequest();
        req.setChannelCode(channelCode);
        req.setPeriodType(periodType);
        req.setQuotaLimit(quotaLimit);
        req.setEnabled(true);
        return req;
    }

    private PackageChannelQuotaConfig quota(String channelCode, String periodType, int quotaLimit, boolean enabled) {
        PackageChannelQuotaConfig quota = new PackageChannelQuotaConfig();
        quota.setPackagePlanId(3L);
        quota.setChannelCode(channelCode);
        quota.setPeriodType(periodType);
        quota.setQuotaLimit(quotaLimit);
        quota.setEnabled(enabled);
        return quota;
    }

    private PackagePlanUpdateRequest updateRequest() {
        PackagePlanUpdateRequest req = new PackagePlanUpdateRequest();
        req.setPackageName("Trial New");
        req.setAudienceType(PackagePlanService.AUDIENCE_INTERNAL);
        req.setStandardPrice(new BigDecimal("1000.00"));
        req.setServiceMonths(12);
        req.setSortOrder(10);
        req.setKeywordGroupLimit(10);
        req.setKeywordGroupLimitA(10);
        req.setKeywordGroupLimitB(0);
        req.setKeywordGroupLimitC(0);
        req.setMonthlyReportDepth("L1");
        req.setQuarterlyReportDepth("L1");
        req.setConsultantIntensity("L1");
        req.setCompetitorInsightDepth("L1");
        req.setMediaDistributionIntensity("L1");
        req.setCommitmentTargetIntensity("L1");
        req.setTargetMetricType("visibility_rate");
        req.setTargetMetricValue(new BigDecimal("0.05"));
        req.setTargetWindowDays(90);
        req.setChannelQuotaConfigs(List.of(request("official_site", "month", 1)));
        return req;
    }
}
