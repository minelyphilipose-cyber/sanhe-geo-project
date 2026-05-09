package com.huanjing.geo.module.customer.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyPackageBindingService {

    private final CompanyPackageBindingMapper bindingMapper;
    private final CompanyMapper companyMapper;
    private final PackagePlanMapper packagePlanMapper;
    private final PackageChannelQuotaConfigMapper channelQuotaConfigMapper;
    private final CompanyChannelQuotaUsageMapper quotaUsageMapper;
    private final CompanyChannelQuotaLedgerMapper quotaLedgerMapper;
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
        return bindingMapper.selectOne(
                new LambdaQueryWrapper<CompanyPackageBinding>()
                        .eq(CompanyPackageBinding::getCompanyId, companyId)
                        .eq(CompanyPackageBinding::getStatus, CompanyPackageBinding.STATUS_ACTIVE)
                        .eq(CompanyPackageBinding::getActiveFlag, 1)
                        .last("LIMIT 1")
        );
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
        CompanyPackageBinding binding = buildBinding(companyId, plan, channelQuotas);
        bindingMapper.insert(binding);
        initTotalUsage(binding, channelQuotas);
        return binding;
    }

    @Transactional
    public void unbind(Long companyId) {
        currentUserService.ensurePermission("user.manage");
        lockCompany(companyId);
        CompanyPackageBinding binding = requireActiveBinding(companyId);
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

    private CompanyPackageBinding buildBinding(Long companyId, PackagePlan plan, List<PackageChannelQuotaConfig> channelQuotas) {
        CompanyPackageBinding binding = new CompanyPackageBinding();
        binding.setCompanyId(companyId);
        binding.setPackagePlanId(plan.getId());
        binding.setPackageType(plan.getPackageType());
        binding.setPackageName(plan.getPackageName());
        binding.setStandardPrice(plan.getStandardPrice());
        binding.setServiceMonths(plan.getServiceMonths());
        binding.setKeywordGroupLimit(plan.getKeywordGroupLimit());
        binding.setChannelQuotaSnapshot(JSONUtil.toJsonStr(toSnapshot(channelQuotas)));
        binding.markActive();
        binding.setBoundAt(LocalDateTime.now());
        return binding;
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
        for (String channel : List.of("official_site", "industry_site", "self_media", "authority_media")) {
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
}
