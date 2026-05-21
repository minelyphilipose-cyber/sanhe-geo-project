package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
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
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyPackageBindingService {

    private static final java.time.ZoneId BUSINESS_ZONE = QuotaPeriodResolver.BUSINESS_ZONE;
    private static final String BIZ_TYPE_DISTRIBUTION = "distribution";
    private static final Set<String> SUCCESS_TASK_STATUS = Set.of("submitted", "confirmed", "published");
    private static final Set<String> FAILED_TASK_STATUS = Set.of("failed", "cancelled", "canceled");

    private final CompanyPackageBindingMapper bindingMapper;
    private final CompanyMapper companyMapper;
    private final PackagePlanMapper packagePlanMapper;
    private final PackageChannelQuotaConfigMapper channelQuotaConfigMapper;
    private final ProjectMapper projectMapper;
    private final CompanyChannelQuotaUsageMapper quotaUsageMapper;
    private final CompanyChannelQuotaLedgerMapper quotaLedgerMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final ProjectChannelAllocationMapper projectChannelAllocationMapper;
    private final CurrentUserService currentUserService;

    public CompanyPackageBinding requireActiveBinding(Long companyId) {
        CompanyPackageBinding binding = activeBinding(companyId);
        if (binding == null) {
            throw new BizException(400, "Customer has no active package binding");
        }
        return binding;
    }

    public CompanyPackageBinding activeBinding(Long companyId) {
        if (companyId == null) {
            return null;
        }
        CompanyPackageBinding binding = bindingMapper.selectOne(
                new LambdaQueryWrapper<CompanyPackageBinding>()
                        .eq(CompanyPackageBinding::getCompanyId, companyId)
                        .eq(CompanyPackageBinding::getStatus, CompanyPackageBinding.STATUS_ACTIVE)
                        .eq(CompanyPackageBinding::getActiveFlag, 1)
                        .last("LIMIT 1")
        );
        hydrateKeywordLimitsFromCurrentPlan(binding);
        return binding;
    }

    public List<CompanyPackageBinding> bindings(Long companyId) {
        currentUserService.ensurePermission("user.manage");
        return bindingMapper.selectList(
                new LambdaQueryWrapper<CompanyPackageBinding>()
                        .eq(CompanyPackageBinding::getCompanyId, companyId)
                        .orderByDesc(CompanyPackageBinding::getBoundAt, CompanyPackageBinding::getId)
        );
    }

    @Transactional
    public void syncActiveBindingsForPackagePlan(Long packagePlanId) {
        if (packagePlanId == null) {
            return;
        }
        PackagePlan plan = packagePlanMapper.selectById(packagePlanId);
        if (plan == null) {
            return;
        }
        List<PackageChannelQuotaConfig> channelQuotas = activeChannelQuotas(packagePlanId);
        List<CompanyPackageBinding> bindings = bindingMapper.selectList(
                new LambdaQueryWrapper<CompanyPackageBinding>()
                        .eq(CompanyPackageBinding::getPackagePlanId, packagePlanId)
                        .eq(CompanyPackageBinding::getStatus, CompanyPackageBinding.STATUS_ACTIVE)
                        .eq(CompanyPackageBinding::getActiveFlag, 1)
        );
        for (CompanyPackageBinding binding : bindings) {
            applyPlanSnapshot(binding, plan, channelQuotas);
            bindingMapper.updateById(binding);
            syncCurrentUsageQuotaLimits(binding, channelQuotas);
        }
    }

    @Transactional
    public CompanyPackageBinding bind(Long companyId, Long packagePlanId) {
        currentUserService.ensurePermission("user.manage");
        lockCompany(companyId);
        bindingMapper.clearInactiveActiveFlags(companyId);
        Company company = companyMapper.selectById(companyId);
        if (company == null || company.getDeletedAt() != null) {
            throw new BizException(404, "Company not found");
        }
        if (activeBinding(companyId) != null) {
            throw new BizException(400, "Customer already has active package binding");
        }
        PackagePlan plan = packagePlanMapper.selectById(packagePlanId);
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            throw new BizException(400, "Package plan not found or disabled");
        }
        List<PackageChannelQuotaConfig> channelQuotas = activeChannelQuotas(packagePlanId);
        validateActiveProjectAllocationsAgainstPackage(companyId, channelQuotas);
        validateActiveProjectKeywordAllocationsAgainstPackage(companyId, plan);
        CompanyPackageBinding binding = buildBinding(companyId, plan, channelQuotas);
        bindingMapper.insert(binding);
        initTotalUsage(binding, channelQuotas);
        markCompanySigned(company);
        return binding;
    }

    @Transactional
    public void unbind(Long companyId) {
        currentUserService.ensurePermission("user.manage");
        lockCompany(companyId);
        CompanyPackageBinding binding = requireActiveBinding(companyId);
        reconcileReservedDistributionQuota(companyId);
        long reserved = quotaLedgerMapper.countReservedByCompany(companyId);
        if (reserved > 0) {
            throw new BizException(400, "Customer has reserved distribution quota, cannot unbind package");
        }
        binding.markInactive();
        int updated = bindingMapper.markInactive(binding.getId(), binding.getUnboundAt());
        if (updated != 1) {
            throw new BizException(409, "Package binding status changed, please retry");
        }
    }

    private void reconcileReservedDistributionQuota(Long companyId) {
        List<CompanyChannelQuotaLedger> ledgers = quotaLedgerMapper.selectReservedByCompany(companyId);
        if (ledgers == null || ledgers.isEmpty()) {
            return;
        }
        for (CompanyChannelQuotaLedger ledger : ledgers) {
            if (ledger == null || ledger.getId() == null || !"reserved".equals(ledger.getStatus())) {
                continue;
            }
            DistributionTask task = resolveDistributionTask(ledger);
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (task != null && SUCCESS_TASK_STATUS.contains(task.getStatus())) {
                quotaLedgerMapper.updateStatusFromReserved(ledger.getId(), "confirmed", now);
                continue;
            }
            if (task != null && FAILED_TASK_STATUS.contains(task.getStatus())) {
                expireReservedLedgerAndReleaseUsage(ledger);
                continue;
            }
            if (task != null && !SUCCESS_TASK_STATUS.contains(task.getStatus())) {
                markDistributionTaskFailed(task, now);
                expireReservedLedgerAndReleaseUsage(ledger);
                continue;
            }
            expireReservedLedgerAndReleaseUsage(ledger);
        }
    }

    private DistributionTask resolveDistributionTask(CompanyChannelQuotaLedger ledger) {
        if (!BIZ_TYPE_DISTRIBUTION.equals(ledger.getBizType()) || ledger.getBizId() == null || ledger.getBizId().isBlank()) {
            return null;
        }
        try {
            return distributionTaskMapper.selectById(Long.valueOf(ledger.getBizId()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void markDistributionTaskFailed(DistributionTask task, LocalDateTime now) {
        task.setStatus("failed");
        task.setFailureKind("UNKNOWN");
        task.setErrorMessage("Distribution task expired while submitting before package unbind");
        task.setFinishedAt(now);
        task.setLockedUntil(null);
        distributionTaskMapper.updateById(task);
    }

    private void expireReservedLedgerAndReleaseUsage(CompanyChannelQuotaLedger ledger) {
        int updated = quotaLedgerMapper.updateStatusFromReserved(ledger.getId(), "expired", LocalDateTime.now(BUSINESS_ZONE));
        if (updated != 1) {
            return;
        }
        quotaUsageMapper.releaseReserved(
                ledger.getCompanyId(),
                ledger.getChannelCode(),
                ledger.getPeriodType(),
                ledger.getPeriodKey()
        );
    }

    private CompanyPackageBinding buildBinding(Long companyId, PackagePlan plan, List<PackageChannelQuotaConfig> channelQuotas) {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(companyId);
        applyPlanSnapshot(binding, plan, channelQuotas);
        binding.markActive();
        binding.setBoundAt(LocalDateTime.now());
        return binding;
    }

    private void applyPlanSnapshot(CompanyPackageBinding binding, PackagePlan plan, List<PackageChannelQuotaConfig> channelQuotas) {
        binding.setPackagePlanId(plan.getId());
        binding.setPackageType(plan.getPackageType());
        binding.setPackageName(plan.getPackageName());
        binding.setStandardPrice(plan.getStandardPrice());
        binding.setServiceMonths(plan.getServiceMonths());
        binding.setKeywordGroupLimit(plan.getKeywordGroupLimit());
        binding.setKeywordGroupLimitA(defaultInt(plan.getKeywordGroupLimitA(), plan.getKeywordGroupLimit()));
        binding.setKeywordGroupLimitB(defaultInt(plan.getKeywordGroupLimitB(), 0));
        binding.setKeywordGroupLimitC(defaultInt(plan.getKeywordGroupLimitC(), 0));
        binding.setChannelQuotaSnapshot(JSONUtil.toJsonStr(toSnapshot(channelQuotas)));
    }

    private void hydrateKeywordLimitsFromCurrentPlan(CompanyPackageBinding binding) {
        if (binding == null || binding.getPackagePlanId() == null) {
            return;
        }
        PackagePlan plan = packagePlanMapper.selectById(binding.getPackagePlanId());
        if (plan == null) {
            return;
        }
        binding.setKeywordGroupLimit(defaultInt(plan.getKeywordGroupLimit(), binding.getKeywordGroupLimit()));
        binding.setKeywordGroupLimitA(defaultInt(plan.getKeywordGroupLimitA(), binding.getKeywordGroupLimit()));
        binding.setKeywordGroupLimitB(defaultInt(plan.getKeywordGroupLimitB(), 0));
        binding.setKeywordGroupLimitC(defaultInt(plan.getKeywordGroupLimitC(), 0));
    }

    private List<PackageChannelQuotaConfig> activeChannelQuotas(Long packagePlanId) {
        return channelQuotaConfigMapper.selectList(
                new LambdaQueryWrapper<PackageChannelQuotaConfig>()
                        .eq(PackageChannelQuotaConfig::getPackagePlanId, packagePlanId)
                        .eq(PackageChannelQuotaConfig::getEnabled, true)
        );
    }

    private void lockCompany(Long companyId) {
        Long locked = companyMapper.lockCompanyForUpdate(companyId);
        if (locked == null) {
            throw new BizException(404, "Company not found");
        }
    }

    private void markCompanySigned(Company company) {
        if (company == null || "signed".equals(company.getStatus())) {
            return;
        }
        company.setStatus("signed");
        companyMapper.updateById(company);
    }

    private void validateActiveProjectAllocationsAgainstPackage(Long companyId, List<PackageChannelQuotaConfig> channelQuotas) {
        Map<String, Integer> quotaByChannel = channelQuotas.stream()
                .filter(cfg -> Boolean.TRUE.equals(cfg.getEnabled()))
                .collect(Collectors.toMap(
                        PackageChannelQuotaConfig::getChannelCode,
                        cfg -> cfg.getQuotaLimit() == null ? 0 : cfg.getQuotaLimit(),
                        Math::max,
                        LinkedHashMap::new
                ));
        List<Map<String, Object>> exceeded = new java.util.ArrayList<>();
        for (String channel : List.of("official_site", "industry_site", "forum", "self_media", "authority_media")) {
            int quotaLimit = quotaByChannel.getOrDefault(channel, 0);
            List<ProjectChannelAllocationProjectRow> activeProjects =
                    projectChannelAllocationMapper.activeProjectRowsForUpdate(companyId, channel, null);
            long allocated = activeProjects.stream()
                    .map(ProjectChannelAllocationProjectRow::getAllocatedCount)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            if (allocated <= quotaLimit) {
                continue;
            }
            List<Map<String, Object>> projects = activeProjects.stream()
                    .map(row -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("projectId", row.getProjectId());
                        map.put("projectName", row.getProjectName());
                        map.put("allocatedCount", row.getAllocatedCount());
                        return map;
                    })
                    .toList();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channelCode", channel);
            item.put("newQuotaLimit", quotaLimit);
            item.put("allocatedTotal", allocated);
            item.put("exceededBy", allocated - quotaLimit);
            item.put("projects", projects);
            exceeded.add(item);
        }
        if (!exceeded.isEmpty()) {
            throw new BizException(400, "PACKAGE_CHANNEL_ALLOCATION_EXCEEDED", 200,
                    Map.of(
                            "errorCode", "PACKAGE_CHANNEL_ALLOCATION_EXCEEDED",
                            "channels", exceeded
                    ));
        }
    }

    private void validateActiveProjectKeywordAllocationsAgainstPackage(Long companyId, PackagePlan plan) {
        List<com.huanjing.geo.module.project.entity.Project> activeProjects = projectMapper.selectList(
                new LambdaQueryWrapper<com.huanjing.geo.module.project.entity.Project>()
                        .eq(com.huanjing.geo.module.project.entity.Project::getCompanyId, companyId)
                        .eq(com.huanjing.geo.module.project.entity.Project::getStatus, "active")
                        .isNull(com.huanjing.geo.module.project.entity.Project::getDeletedAt)
        );
        int usedA = activeProjects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitA(), defaultInt(p.getPlanKeywordGroupLimit(), 0))).sum();
        int usedB = activeProjects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitB(), 0)).sum();
        int usedC = activeProjects.stream().mapToInt(p -> defaultInt(p.getPlanKeywordGroupLimitC(), 0)).sum();
        int limitA = defaultInt(plan.getKeywordGroupLimitA(), plan.getKeywordGroupLimit());
        int limitB = defaultInt(plan.getKeywordGroupLimitB(), 0);
        int limitC = defaultInt(plan.getKeywordGroupLimitC(), 0);
        if (usedA > limitA || usedB > limitB || usedC > limitC) {
            throw new BizException(400, "PACKAGE_KEYWORD_TIER_ALLOCATION_EXCEEDED", 200,
                    Map.of(
                            "errorCode", "PACKAGE_KEYWORD_TIER_ALLOCATION_EXCEEDED",
                            "limitA", limitA,
                            "limitB", limitB,
                            "limitC", limitC,
                            "usedA", usedA,
                            "usedB", usedB,
                            "usedC", usedC
                    ));
        }
    }

    private int defaultInt(Integer value, Integer fallback) {
        return value == null ? (fallback == null ? 0 : fallback) : value;
    }

    private List<ChannelQuotaSnapshotItem> toSnapshot(List<PackageChannelQuotaConfig> channelQuotas) {
        return channelQuotas.stream().map(cfg -> {
            ChannelQuotaSnapshotItem item = new ChannelQuotaSnapshotItem();
            item.setChannelCode(cfg.getChannelCode());
            item.setPeriodType(cfg.getPeriodType());
            item.setQuotaLimit(cfg.getQuotaLimit() == null ? 0 : cfg.getQuotaLimit());
            item.setEnabled(Boolean.TRUE.equals(cfg.getEnabled()));
            return item;
        }).toList();
    }

    private void initTotalUsage(CompanyPackageBinding binding, List<PackageChannelQuotaConfig> channelQuotas) {
        for (PackageChannelQuotaConfig cfg : channelQuotas) {
            if (!"total".equals(cfg.getPeriodType())) {
                continue;
            }
            quotaUsageMapper.insertIgnore(
                    binding.getCompanyId(),
                    cfg.getChannelCode(),
                    cfg.getPeriodType(),
                    "TOTAL",
                    cfg.getQuotaLimit()
            );
            CompanyChannelQuotaUsage update = quotaUsageMapper.selectOne(
                    new LambdaQueryWrapper<CompanyChannelQuotaUsage>()
                            .eq(CompanyChannelQuotaUsage::getCompanyId, binding.getCompanyId())
                            .eq(CompanyChannelQuotaUsage::getChannelCode, cfg.getChannelCode())
                            .eq(CompanyChannelQuotaUsage::getPeriodType, cfg.getPeriodType())
                            .eq(CompanyChannelQuotaUsage::getPeriodKey, "TOTAL")
                            .last("LIMIT 1")
            );
            if (update != null) {
                update.setQuotaLimit(cfg.getQuotaLimit());
                quotaUsageMapper.updateById(update);
            }
        }
    }

    private void syncCurrentUsageQuotaLimits(CompanyPackageBinding binding, List<PackageChannelQuotaConfig> channelQuotas) {
        for (PackageChannelQuotaConfig cfg : channelQuotas) {
            String periodKey = QuotaPeriodResolver.periodKeyOrNull(cfg.getPeriodType());
            if (periodKey == null) {
                continue;
            }
            quotaUsageMapper.insertIgnore(
                    binding.getCompanyId(),
                    cfg.getChannelCode(),
                    cfg.getPeriodType(),
                    periodKey,
                    cfg.getQuotaLimit()
            );
            quotaUsageMapper.updateQuotaLimit(
                    binding.getCompanyId(),
                    cfg.getChannelCode(),
                    cfg.getPeriodType(),
                    periodKey,
                    cfg.getQuotaLimit()
            );
        }
    }
}
