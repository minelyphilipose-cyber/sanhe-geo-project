package com.huanjing.geo.module.customer.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaLedgerMapper;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.mapper.CompanyPackageBindingMapper;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationProjectRow;
import com.huanjing.geo.module.project.entity.PackageChannelQuotaConfig;
import com.huanjing.geo.module.project.entity.PackagePlan;
import com.huanjing.geo.module.project.mapper.PackageChannelQuotaConfigMapper;
import com.huanjing.geo.module.project.mapper.PackagePlanMapper;
import com.huanjing.geo.module.project.mapper.ProjectChannelAllocationMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyPackageBindingServiceTest {

    private CompanyPackageBindingMapper bindingMapper;
    private CompanyMapper companyMapper;
    private PackagePlanMapper packagePlanMapper;
    private PackageChannelQuotaConfigMapper channelQuotaConfigMapper;
    private CompanyChannelQuotaUsageMapper quotaUsageMapper;
    private CompanyChannelQuotaLedgerMapper quotaLedgerMapper;
    private ProjectChannelAllocationMapper projectChannelAllocationMapper;
    private CurrentUserService currentUserService;
    private CompanyPackageBindingService service;

    @BeforeEach
    void setUp() {
        bindingMapper = mock(CompanyPackageBindingMapper.class);
        companyMapper = mock(CompanyMapper.class);
        packagePlanMapper = mock(PackagePlanMapper.class);
        channelQuotaConfigMapper = mock(PackageChannelQuotaConfigMapper.class);
        quotaUsageMapper = mock(CompanyChannelQuotaUsageMapper.class);
        quotaLedgerMapper = mock(CompanyChannelQuotaLedgerMapper.class);
        projectChannelAllocationMapper = mock(ProjectChannelAllocationMapper.class);
        currentUserService = mock(CurrentUserService.class);
        when(companyMapper.lockCompanyForUpdate(any())).thenReturn(7L);
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(any(), any(), any())).thenReturn(List.of());
        service = new CompanyPackageBindingService(
                bindingMapper,
                companyMapper,
                packagePlanMapper,
                channelQuotaConfigMapper,
                quotaUsageMapper,
                quotaLedgerMapper,
                projectChannelAllocationMapper,
                currentUserService
        );
    }

    @Test
    void unbindClearsActiveFlagWithExplicitNullUpdate() {
        CompanyPackageBinding binding = activeBinding();
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(quotaLedgerMapper.countReservedByCompany(7L)).thenReturn(0L);
        when(bindingMapper.markInactive(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        service.unbind(7L);

        verify(bindingMapper).markInactive(eq(100L), any(LocalDateTime.class));
    }

    @Test
    void unbindThrowsWhenBindingWasChangedConcurrently() {
        CompanyPackageBinding binding = activeBinding();
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(quotaLedgerMapper.countReservedByCompany(7L)).thenReturn(0L);
        when(bindingMapper.markInactive(eq(100L), any(LocalDateTime.class))).thenReturn(0);

        assertThrows(BizException.class, () -> service.unbind(7L));
    }

    @Test
    void bindRepairsInactiveActiveFlagsBeforeInsertingNewBinding() {
        Company company = new Company();
        company.setId(7L);
        PackagePlan plan = enabledPlan();
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(packagePlanMapper.selectById(3L)).thenReturn(plan);
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of());

        service.bind(7L, 3L);

        verify(bindingMapper).clearInactiveActiveFlags(7L);
        verify(bindingMapper).insert(any(CompanyPackageBinding.class));
    }

    @Test
    void bindAllowsPackageWhenActiveProjectAllocationsFitAllChannels() {
        Company company = new Company();
        company.setId(7L);
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(packagePlanMapper.selectById(3L)).thenReturn(enabledPlan());
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of(
                quota("official_site", 2),
                quota("industry_site", 1)
        ));
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(7L, "official_site", null))
                .thenReturn(List.of(projectRow(11L, "P1", 2)));
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(7L, "industry_site", null))
                .thenReturn(List.of(projectRow(12L, "P2", 1)));

        assertDoesNotThrow(() -> service.bind(7L, 3L));

        verify(bindingMapper).insert(any(CompanyPackageBinding.class));
    }

    @Test
    void bindBlocksWhenOneChannelAllocationExceedsNewPackage() {
        Company company = new Company();
        company.setId(7L);
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(packagePlanMapper.selectById(3L)).thenReturn(enabledPlan());
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of(quota("official_site", 1)));
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(7L, "official_site", null))
                .thenReturn(List.of(projectRow(11L, "P1", 2)));

        BizException ex = assertThrows(BizException.class, () -> service.bind(7L, 3L));

        org.junit.jupiter.api.Assertions.assertEquals("PACKAGE_CHANNEL_ALLOCATION_EXCEEDED", ex.getMessage());
    }

    @Test
    void bindBlocksAndReportsMultipleExceededChannels() {
        Company company = new Company();
        company.setId(7L);
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(packagePlanMapper.selectById(3L)).thenReturn(enabledPlan());
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of(
                quota("official_site", 1),
                quota("industry_site", 1)
        ));
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(7L, "official_site", null))
                .thenReturn(List.of(projectRow(11L, "P1", 2)));
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(7L, "industry_site", null))
                .thenReturn(List.of(projectRow(12L, "P2", 3)));

        BizException ex = assertThrows(BizException.class, () -> service.bind(7L, 3L));

        java.util.Map<?, ?> data = (java.util.Map<?, ?>) ex.getData();
        List<?> channels = (List<?>) data.get("channels");
        org.junit.jupiter.api.Assertions.assertEquals(2, channels.size());
    }

    private CompanyPackageBinding activeBinding() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setId(100L);
        binding.setCompanyId(7L);
        binding.markActive();
        return binding;
    }

    private PackagePlan enabledPlan() {
        PackagePlan plan = new PackagePlan();
        plan.setId(3L);
        plan.setPackageType("trial");
        plan.setPackageName("Trial");
        plan.setStandardPrice(new BigDecimal("6980.00"));
        plan.setServiceMonths(3);
        plan.setKeywordGroupLimit(100);
        plan.setEnabled(true);
        return plan;
    }

    private PackageChannelQuotaConfig quota(String channelCode, int quotaLimit) {
        PackageChannelQuotaConfig config = new PackageChannelQuotaConfig();
        config.setPackagePlanId(3L);
        config.setChannelCode(channelCode);
        config.setPeriodType("month");
        config.setQuotaLimit(quotaLimit);
        config.setEnabled(true);
        return config;
    }

    private ProjectChannelAllocationProjectRow projectRow(Long projectId, String projectName, int allocatedCount) {
        ProjectChannelAllocationProjectRow row = new ProjectChannelAllocationProjectRow();
        row.setProjectId(projectId);
        row.setProjectName(projectName);
        row.setAllocatedCount(allocatedCount);
        return row;
    }
}
