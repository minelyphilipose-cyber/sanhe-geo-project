package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaLedgerMapper;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
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
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyPackageBindingServiceTest {

    private CompanyPackageBindingMapper bindingMapper;
    private CompanyMapper companyMapper;
    private PackagePlanMapper packagePlanMapper;
    private PackageChannelQuotaConfigMapper channelQuotaConfigMapper;
    private ProjectMapper projectMapper;
    private CompanyChannelQuotaUsageMapper quotaUsageMapper;
    private CompanyChannelQuotaLedgerMapper quotaLedgerMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private ProjectChannelAllocationMapper projectChannelAllocationMapper;
    private CurrentUserService currentUserService;
    private CompanyPackageBindingService service;

    @BeforeEach
    void setUp() {
        bindingMapper = mock(CompanyPackageBindingMapper.class);
        companyMapper = mock(CompanyMapper.class);
        packagePlanMapper = mock(PackagePlanMapper.class);
        channelQuotaConfigMapper = mock(PackageChannelQuotaConfigMapper.class);
        projectMapper = mock(ProjectMapper.class);
        quotaUsageMapper = mock(CompanyChannelQuotaUsageMapper.class);
        quotaLedgerMapper = mock(CompanyChannelQuotaLedgerMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        projectChannelAllocationMapper = mock(ProjectChannelAllocationMapper.class);
        currentUserService = mock(CurrentUserService.class);
        when(companyMapper.lockCompanyForUpdate(any())).thenReturn(7L);
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(any(), any(), any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of());
        service = new CompanyPackageBindingService(
                bindingMapper,
                companyMapper,
                packagePlanMapper,
                channelQuotaConfigMapper,
                projectMapper,
                quotaUsageMapper,
                quotaLedgerMapper,
                distributionTaskMapper,
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
    void unbindConfirmsSubmittedReservedLedgerBeforeCheckingBlockers() {
        CompanyPackageBinding binding = activeBinding();
        CompanyChannelQuotaLedger ledger = reservedLedger();
        DistributionTask task = distributionTask("submitted");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(quotaLedgerMapper.selectReservedByCompany(7L)).thenReturn(List.of(ledger));
        when(distributionTaskMapper.selectById(200L)).thenReturn(task);
        when(quotaLedgerMapper.updateStatusFromReserved(eq(10L), eq("confirmed"), any(LocalDateTime.class))).thenReturn(1);
        when(quotaLedgerMapper.countReservedByCompany(7L)).thenReturn(0L);
        when(bindingMapper.markInactive(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        service.unbind(7L);

        verify(quotaLedgerMapper).updateStatusFromReserved(eq(10L), eq("confirmed"), any(LocalDateTime.class));
        verify(bindingMapper).markInactive(eq(100L), any(LocalDateTime.class));
    }

    @Test
    void unbindExpiresFailedReservedLedgerAndReleasesUsageBeforeCheckingBlockers() {
        CompanyPackageBinding binding = activeBinding();
        CompanyChannelQuotaLedger ledger = reservedLedger();
        DistributionTask task = distributionTask("failed");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(quotaLedgerMapper.selectReservedByCompany(7L)).thenReturn(List.of(ledger));
        when(distributionTaskMapper.selectById(200L)).thenReturn(task);
        when(quotaLedgerMapper.updateStatusFromReserved(eq(10L), eq("expired"), any(LocalDateTime.class))).thenReturn(1);
        when(quotaUsageMapper.releaseReserved(7L, "official_site", "month", "2026-05")).thenReturn(1);
        when(quotaLedgerMapper.countReservedByCompany(7L)).thenReturn(0L);
        when(bindingMapper.markInactive(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        service.unbind(7L);

        verify(quotaUsageMapper).releaseReserved(7L, "official_site", "month", "2026-05");
        verify(bindingMapper).markInactive(eq(100L), any(LocalDateTime.class));
    }

    @Test
    void unbindContinuesWhenReservedUsageWasAlreadyReleased() {
        CompanyPackageBinding binding = activeBinding();
        CompanyChannelQuotaLedger ledger = reservedLedger();
        DistributionTask task = distributionTask("failed");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(quotaLedgerMapper.selectReservedByCompany(7L)).thenReturn(List.of(ledger));
        when(distributionTaskMapper.selectById(200L)).thenReturn(task);
        when(quotaLedgerMapper.updateStatusFromReserved(eq(10L), eq("expired"), any(LocalDateTime.class))).thenReturn(1);
        when(quotaUsageMapper.releaseReserved(7L, "official_site", "month", "2026-05")).thenReturn(0);
        when(quotaLedgerMapper.countReservedByCompany(7L)).thenReturn(0L);
        when(bindingMapper.markInactive(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        service.unbind(7L);

        verify(quotaUsageMapper).releaseReserved(7L, "official_site", "month", "2026-05");
        verify(bindingMapper).markInactive(eq(100L), any(LocalDateTime.class));
    }

    @Test
    void unbindExpiresActiveReservedLedgerAndMarksTaskFailedBeforeCheckingBlockers() {
        CompanyPackageBinding binding = activeBinding();
        CompanyChannelQuotaLedger ledger = reservedLedger();
        DistributionTask task = distributionTask("token_issued");
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(quotaLedgerMapper.selectReservedByCompany(7L)).thenReturn(List.of(ledger));
        when(distributionTaskMapper.selectById(200L)).thenReturn(task);
        when(distributionTaskMapper.updateById(any(DistributionTask.class))).thenReturn(1);
        when(quotaLedgerMapper.updateStatusFromReserved(eq(10L), eq("expired"), any(LocalDateTime.class))).thenReturn(1);
        when(quotaUsageMapper.releaseReserved(7L, "official_site", "month", "2026-05")).thenReturn(1);
        when(quotaLedgerMapper.countReservedByCompany(7L)).thenReturn(0L);
        when(bindingMapper.markInactive(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        service.unbind(7L);

        org.mockito.ArgumentCaptor<DistributionTask> captor = forClass(DistributionTask.class);
        verify(distributionTaskMapper).updateById(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        verify(quotaUsageMapper).releaseReserved(7L, "official_site", "month", "2026-05");
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
    void unbindRejectsLockedCustomerPackage() {
        CompanyPackageBinding binding = activeBinding();
        binding.setLockedAt(LocalDateTime.now());
        when(bindingMapper.selectOne(any())).thenReturn(binding);

        BizException ex = assertThrows(BizException.class, () -> service.unbind(7L));

        assertEquals("Customer package is locked and cannot be changed", ex.getMessage());
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
    void bindMarksCompanySignedWhenPackageIsBound() {
        Company company = new Company();
        company.setId(7L);
        company.setStatus("potential");
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(packagePlanMapper.selectById(3L)).thenReturn(enabledPlan());
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of());

        service.bind(7L, 3L);

        org.mockito.ArgumentCaptor<Company> captor = forClass(Company.class);
        verify(companyMapper).updateById(captor.capture());
        assertEquals("signed", captor.getValue().getStatus());
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
    void bindValidatesSelfMediaPlatformProjectAllocationsAgainstPackage() {
        Company company = new Company();
        company.setId(7L);
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(packagePlanMapper.selectById(3L)).thenReturn(enabledPlan());
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of(quota("self_media:zhihu", 1)));
        when(projectChannelAllocationMapper.activeProjectRowsForUpdate(7L, "self_media:zhihu", null))
                .thenReturn(List.of(projectRow(11L, "P1", 2)));

        BizException ex = assertThrows(BizException.class, () -> service.bind(7L, 3L));

        org.junit.jupiter.api.Assertions.assertEquals("PACKAGE_CHANNEL_ALLOCATION_EXCEEDED", ex.getMessage());
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

    @Test
    void syncActiveBindingsForPackagePlanDoesNotRefreshHistoricalSnapshot() {
        service.syncActiveBindingsForPackagePlan(3L);

        verify(packagePlanMapper, never()).selectById(3L);
        verify(bindingMapper, never()).updateById(any());
        verify(quotaUsageMapper, never()).updateQuotaLimit(any(), any(), any(), any(), any());
    }

    @Test
    void refreshActiveBindingAppliesLatestPlanSnapshotAndCurrentQuotaLimits() {
        CompanyPackageBinding binding = activeBinding();
        binding.setPackagePlanId(3L);
        PackagePlan plan = enabledPlan();
        plan.setPackageName("Expanded");
        plan.setServiceMonths(6);
        plan.setKeywordGroupLimit(200);
        plan.setMonthlyReportDepth("L3");
        plan.setTargetMetricType("visibility");
        plan.setTargetMetricValue(new BigDecimal("90"));
        plan.setTargetWindowDays(60);
        PackageChannelQuotaConfig monthlyQuota = quota("official_site", 20);
        PackageChannelQuotaConfig totalQuota = totalQuota("authority_media", 50);
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        when(packagePlanMapper.selectById(3L)).thenReturn(plan);
        when(channelQuotaConfigMapper.selectList(any())).thenReturn(List.of(monthlyQuota, totalQuota));

        CompanyPackageBinding refreshed = service.refreshActiveBinding(7L);

        assertEquals("Expanded", refreshed.getPackageName());
        assertEquals(6, refreshed.getServiceMonths());
        assertEquals(200, refreshed.getKeywordGroupLimit());
        assertEquals("L3", JSONUtil.parseObj(refreshed.getPackageSnapshotJson()).getStr("monthlyReportDepth"));
        assertEquals("visibility", JSONUtil.parseObj(refreshed.getPackageSnapshotJson()).getStr("targetMetricType"));
        assertEquals(60, JSONUtil.parseObj(refreshed.getPackageSnapshotJson()).getInt("targetWindowDays"));
        verify(bindingMapper).updateById(binding);
        verify(quotaUsageMapper).insertIgnore(eq(7L), eq("official_site"), eq("month"), any(), eq(20));
        verify(quotaUsageMapper).updateQuotaLimit(eq(7L), eq("official_site"), eq("month"), any(), eq(20));
        verify(quotaUsageMapper).insertIgnore(7L, "authority_media", "total", "TOTAL", 50);
        verify(quotaUsageMapper).updateQuotaLimit(7L, "authority_media", "total", "TOTAL", 50);
    }

    @Test
    void refreshActiveBindingRejectsMissingSourcePackage() {
        CompanyPackageBinding binding = activeBinding();
        binding.setPackagePlanId(3L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);

        BizException ex = assertThrows(BizException.class, () -> service.refreshActiveBinding(7L));

        assertEquals("Package plan not found", ex.getMessage());
        verify(bindingMapper, never()).updateById(any());
    }

    @Test
    void partnerStaffCanReadAssignedCustomerPackageBinding() {
        SysUser staff = partnerStaffUser(20L);
        Company company = company(7L, 9L);
        company.setPartnerStaffOwnerId(20L);
        CompanyPackageBinding binding = activeBinding();
        when(currentUserService.requireCurrentUser()).thenReturn(staff);
        when(currentUserService.isPartnerUser(staff)).thenReturn(true);
        when(companyMapper.selectById(7L)).thenReturn(company);
        when(bindingMapper.selectOne(any())).thenReturn(binding);

        assertEquals(binding, service.activeBindingForCurrentUser(7L));
    }

    @Test
    void partnerStaffCannotReadUnassignedCustomerPackageBinding() {
        SysUser staff = partnerStaffUser(20L);
        Company company = company(7L, 9L);
        company.setPartnerStaffOwnerId(21L);
        when(currentUserService.requireCurrentUser()).thenReturn(staff);
        when(currentUserService.isPartnerUser(staff)).thenReturn(true);
        when(companyMapper.selectById(7L)).thenReturn(company);

        assertThrows(BizException.class, () -> service.activeBindingForCurrentUser(7L));
    }

    @Test
    void partnerStaffCannotBindCustomerPackage() {
        SysUser staff = partnerStaffUser(20L);
        Company company = company(7L, 9L);
        company.setPartnerStaffOwnerId(20L);
        when(currentUserService.requireCurrentUser()).thenReturn(staff);
        when(currentUserService.isPartnerUser(staff)).thenReturn(true);
        when(companyMapper.selectById(7L)).thenReturn(company);

        BizException ex = assertThrows(BizException.class, () -> service.bind(7L, 3L));

        assertEquals("Only partner owner can manage customer package", ex.getMessage());
    }

    @Test
    void partnerStaffCannotRefreshCustomerPackage() {
        SysUser staff = partnerStaffUser(20L);
        Company company = company(7L, 9L);
        company.setPartnerStaffOwnerId(20L);
        when(currentUserService.requireCurrentUser()).thenReturn(staff);
        when(currentUserService.isPartnerUser(staff)).thenReturn(true);
        when(companyMapper.selectById(7L)).thenReturn(company);

        BizException ex = assertThrows(BizException.class, () -> service.refreshActiveBinding(7L));

        assertEquals("Only partner owner can manage customer package", ex.getMessage());
        verify(packagePlanMapper, never()).selectById(any());
    }

    private CompanyPackageBinding activeBinding() {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setId(100L);
        binding.setCompanyId(7L);
        binding.markActive();
        return binding;
    }

    private CompanyChannelQuotaLedger reservedLedger() {
        CompanyChannelQuotaLedger ledger = new CompanyChannelQuotaLedger();
        ledger.setId(10L);
        ledger.setCompanyId(7L);
        ledger.setProjectId(8L);
        ledger.setChannelCode("official_site");
        ledger.setPeriodType("month");
        ledger.setPeriodKey("2026-05");
        ledger.setStatus("reserved");
        ledger.setBizType("distribution");
        ledger.setBizId("200");
        ledger.setReservedAt(LocalDateTime.now().minusMinutes(5));
        return ledger;
    }

    private DistributionTask distributionTask(String status) {
        DistributionTask task = new DistributionTask();
        task.setId(200L);
        task.setStatus(status);
        return task;
    }

    private Company company(Long id, Long partnerId) {
        Company company = new Company();
        company.setId(id);
        company.setPartnerId(partnerId);
        return company;
    }

    private SysUser partnerStaffUser(Long userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setRole("partner_staff");
        user.setPartnerId(9L);
        return user;
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
        plan.setAudienceType("internal");
        plan.setPackageStatus("active");
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

    private PackageChannelQuotaConfig totalQuota(String channelCode, int quotaLimit) {
        PackageChannelQuotaConfig config = quota(channelCode, quotaLimit);
        config.setPeriodType("total");
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
