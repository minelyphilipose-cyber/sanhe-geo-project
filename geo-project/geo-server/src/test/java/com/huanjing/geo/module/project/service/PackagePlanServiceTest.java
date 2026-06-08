package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.project.dto.PackageChannelQuotaConfigRequest;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.mapper.PackageChannelQuotaConfigMapper;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        verify(bindingService).syncActiveBindingsForPackagePlan(3L);
    }

    private PackagePlan plan() {
        PackagePlan plan = new PackagePlan();
        plan.setId(3L);
        plan.setPackageType("trial");
        plan.setPackageName("Trial");
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
}
