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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    void saveChannelQuotaConfigsRejectsQuotaDecreaseForBoundPackagePlan() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota("official_site", "month", 2, true)));

        BizException ex = assertThrows(BizException.class,
                () -> service.saveChannelQuotaConfigsByPlanId(3L, List.of(request("official_site", "month", 1))));

        assertEquals("套餐已绑定客户，只允许扩大权益：渠道 official_site 的额度只能增加，不能从 2 调整为 1", ex.getMessage());
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
    void saveChannelQuotaConfigsRejectsDisablingBoundChannel() {
        when(bindingService.hasBindingsForPackagePlan(3L)).thenReturn(true);
        when(channelQuotaConfigMapper.selectList(any()))
                .thenReturn(List.of(quota("self_media:wechat", "week", 1, true)));
        PackageChannelQuotaConfigRequest disabled = request("self_media:wechat", "week", 1);
        disabled.setEnabled(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.saveChannelQuotaConfigsByPlanId(3L, List.of(disabled)));

        assertEquals("套餐已绑定客户，只允许扩大权益：已启用渠道 self_media:wechat 不可删除或停用", ex.getMessage());
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
