package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaLedgerMapper;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyChannelQuotaService {

    private static final java.time.ZoneId BUSINESS_ZONE = QuotaPeriodResolver.BUSINESS_ZONE;
    private static final String BIZ_TYPE_DISTRIBUTION = "distribution";
    private static final int RESERVED_TIMEOUT_MINUTES = 30;
    private static final int RESERVED_SCAN_BATCH_SIZE = 200;
    private static final Set<String> SUCCESS_TASK_STATUS = Set.of("submitted", "confirmed", "published");
    private static final Set<String> FAILED_TASK_STATUS = Set.of("failed", "cancelled", "canceled");

    private final CompanyPackageBindingService companyPackageBindingService;
    private final CompanyChannelQuotaUsageMapper usageMapper;
    private final CompanyChannelQuotaLedgerMapper ledgerMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final SystemAlertService systemAlertService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompanyChannelQuotaLedger reserveDistribution(Long companyId, Long projectId, String targetKind, Long distributionTaskId) {
        if (distributionTaskId == null) {
            throw new BizException(400, "distribution_task_id is required for quota reservation");
        }
        String channel = mapTargetKind(targetKind);
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        SnapshotQuota quota = resolveSnapshotQuota(binding, channel);
        String periodKey = periodKey(quota.periodType());

        CompanyChannelQuotaLedger existed = ledgerMapper.selectOne(
                new LambdaQueryWrapper<CompanyChannelQuotaLedger>()
                        .eq(CompanyChannelQuotaLedger::getBizType, BIZ_TYPE_DISTRIBUTION)
                        .eq(CompanyChannelQuotaLedger::getBizId, String.valueOf(distributionTaskId))
                        .last("LIMIT 1")
        );
        if (existed != null) {
            return existed;
        }

        usageMapper.insertIgnore(companyId, channel, quota.periodType(), periodKey, quota.quotaLimit());
        int reserved = usageMapper.tryReserve(companyId, channel, quota.periodType(), periodKey);
        if (reserved != 1) {
            throw new BizException(400, "Distribution quota exhausted for channel " + channel);
        }

        CompanyChannelQuotaLedger ledger = new CompanyChannelQuotaLedger();
        ledger.setCompanyId(companyId);
        ledger.setProjectId(projectId);
        ledger.setChannelCode(channel);
        ledger.setPeriodType(quota.periodType());
        ledger.setPeriodKey(periodKey);
        ledger.setDeltaCount(1);
        ledger.setStatus("reserved");
        ledger.setBizType(BIZ_TYPE_DISTRIBUTION);
        ledger.setBizId(String.valueOf(distributionTaskId));
        ledger.setReservedAt(LocalDateTime.now(BUSINESS_ZONE));
        ledgerMapper.insert(ledger);
        return ledger;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmDistribution(Long distributionTaskId) {
        updateReservedLedger(distributionTaskId, "confirmed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundDistribution(Long distributionTaskId) {
        CompanyChannelQuotaLedger ledger = updateReservedLedger(distributionTaskId, "refunded");
        if (ledger == null) {
            return;
        }
        releaseUsage(ledger);
    }

    @Transactional
    public void refundConfirmedDistribution(Long distributionTaskId) {
        CompanyChannelQuotaLedger ledger = findDistributionLedger(distributionTaskId);
        if (ledger == null || !"confirmed".equals(ledger.getStatus())) {
            return;
        }
        int updated = ledgerMapper.refundConfirmed(ledger.getId(), LocalDateTime.now(BUSINESS_ZONE));
        if (updated != 1) {
            return;
        }
        ledger.setStatus("refunded");
        releaseUsage(ledger);
    }

    public DistributionQuotaView distributionQuota(Long companyId, String targetKind) {
        String channel = mapTargetKind(targetKind);
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        SnapshotQuota quota = resolveSnapshotQuota(binding, channel);
        String periodKey = periodKey(quota.periodType());
        usageMapper.insertIgnore(companyId, channel, quota.periodType(), periodKey, quota.quotaLimit());
        CompanyChannelQuotaUsage usage = usageMapper.selectOne(
                new LambdaQueryWrapper<CompanyChannelQuotaUsage>()
                        .eq(CompanyChannelQuotaUsage::getCompanyId, companyId)
                        .eq(CompanyChannelQuotaUsage::getChannelCode, channel)
                        .eq(CompanyChannelQuotaUsage::getPeriodType, quota.periodType())
                        .eq(CompanyChannelQuotaUsage::getPeriodKey, periodKey)
                        .last("LIMIT 1")
        );
        int usedCount = usage == null || usage.getUsedCount() == null ? 0 : usage.getUsedCount();
        return new DistributionQuotaView(channel, quota.periodType(), periodKey, usedCount, quota.quotaLimit());
    }

    public long reservedCount(Long companyId) {
        return ledgerMapper.countReservedByCompany(companyId);
    }

    @Scheduled(fixedDelayString = "${geo.company-quota.reserved-scan-ms:600000}")
    public void expireTimedOutReservedLedgers() {
        LocalDateTime before = LocalDateTime.now(BUSINESS_ZONE).minusMinutes(RESERVED_TIMEOUT_MINUTES);
        for (CompanyChannelQuotaLedger ledger : ledgerMapper.selectTimedOutReserved(before, RESERVED_SCAN_BATCH_SIZE)) {
            reconcileReservedLedger(ledger);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileReservedLedger(CompanyChannelQuotaLedger ledger) {
        if (ledger == null || ledger.getId() == null || !"reserved".equals(ledger.getStatus())) {
            return;
        }
        DistributionTask task = resolveDistributionTask(ledger);
        if (task != null && SUCCESS_TASK_STATUS.contains(task.getStatus())) {
            updateLedgerStatusFromReserved(ledger, "confirmed");
            return;
        }
        if (task != null && !FAILED_TASK_STATUS.contains(task.getStatus())) {
            ledgerMapper.touchExpireCheckedAt(ledger.getId(), LocalDateTime.now(BUSINESS_ZONE));
            return;
        }
        CompanyChannelQuotaLedger expired = updateLedgerStatusFromReserved(ledger, "expired");
        if (expired != null) {
            releaseUsage(expired);
        }
    }

    private CompanyChannelQuotaLedger updateReservedLedger(Long distributionTaskId, String targetStatus) {
        CompanyChannelQuotaLedger ledger = findDistributionLedger(distributionTaskId);
        if (ledger == null) {
            return null;
        }
        return updateLedgerStatusFromReserved(ledger, targetStatus);
    }

    private CompanyChannelQuotaLedger findDistributionLedger(Long distributionTaskId) {
        if (distributionTaskId == null) {
            return null;
        }
        return ledgerMapper.selectOne(
                new LambdaQueryWrapper<CompanyChannelQuotaLedger>()
                        .eq(CompanyChannelQuotaLedger::getBizType, BIZ_TYPE_DISTRIBUTION)
                        .eq(CompanyChannelQuotaLedger::getBizId, String.valueOf(distributionTaskId))
                        .last("LIMIT 1")
        );
    }

    private CompanyChannelQuotaLedger updateLedgerStatusFromReserved(CompanyChannelQuotaLedger ledger, String targetStatus) {
        int updated = ledgerMapper.updateStatusFromReserved(ledger.getId(), targetStatus, LocalDateTime.now(BUSINESS_ZONE));
        if (updated != 1) {
            return null;
        }
        ledger.setStatus(targetStatus);
        return ledger;
    }

    private void releaseUsage(CompanyChannelQuotaLedger ledger) {
        int released = usageMapper.releaseReserved(
                ledger.getCompanyId(),
                ledger.getChannelCode(),
                ledger.getPeriodType(),
                ledger.getPeriodKey()
        );
        if (released != 1) {
            log.error("Quota usage release failed, ledgerId={}, companyId={}, channel={}, periodType={}, periodKey={}",
                    ledger.getId(), ledger.getCompanyId(), ledger.getChannelCode(), ledger.getPeriodType(), ledger.getPeriodKey());
            systemAlertService.createAlert(
                    "quota_usage_release_failed",
                    "error",
                    "company_channel_quota",
                    "Quota usage release failed",
                    Map.of(
                            "ledgerId", ledger.getId(),
                            "companyId", ledger.getCompanyId(),
                            "channelCode", ledger.getChannelCode(),
                            "periodType", ledger.getPeriodType(),
                            "periodKey", ledger.getPeriodKey()
                    )
            );
        }
    }

    private DistributionTask resolveDistributionTask(CompanyChannelQuotaLedger ledger) {
        if (!BIZ_TYPE_DISTRIBUTION.equals(ledger.getBizType()) || !StringUtils.hasText(ledger.getBizId())) {
            return null;
        }
        try {
            return distributionTaskMapper.selectById(Long.valueOf(ledger.getBizId()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private SnapshotQuota resolveSnapshotQuota(CompanyPackageBinding binding, String channel) {
        if (!StringUtils.hasText(binding.getChannelQuotaSnapshot())) {
            throw new BizException(400, "Customer package channel quota snapshot is empty");
        }
        JSONArray arr = JSONUtil.parseArray(binding.getChannelQuotaSnapshot());
        for (Object obj : arr) {
            ChannelQuotaSnapshotItem item = JSONUtil.toBean(JSONUtil.parseObj(obj), ChannelQuotaSnapshotItem.class);
            if (!channel.equals(item.getChannelCode()) || !item.isEnabled()) {
                continue;
            }
            String periodType = item.getPeriodType();
            if (!StringUtils.hasText(periodType)) {
                break;
            }
            return new SnapshotQuota(periodType.trim().toLowerCase(Locale.ROOT), item.getQuotaLimit());
        }
        throw new BizException(400, "Customer package has no quota for channel " + channel);
    }

    public String mapTargetKind(String targetKind) {
        if (!StringUtils.hasText(targetKind)) {
            throw new BizException(400, "distribution target kind is required");
        }
        return switch (targetKind.trim()) {
            case DistributionTargetKind.BRAND_OFFICIAL_SITE, DistributionTargetKind.BRAND_GEO_SITE -> "official_site";
            case DistributionTargetKind.INDUSTRY_SITE -> "industry_site";
            case DistributionTargetKind.MP_ACCOUNT -> "self_media";
            case DistributionTargetKind.AUTHORITY_MEDIA -> "authority_media";
            case DistributionTargetKind.SITE -> throw new BizException(400, "Legacy site target is not supported by company package channel quota");
            default -> throw new BizException(400, "Unsupported distribution target kind: " + targetKind);
        };
    }

    private String periodKey(String periodType) {
        try {
            return QuotaPeriodResolver.periodKey(periodType);
        } catch (IllegalArgumentException ex) {
            throw new BizException(400, ex.getMessage());
        }
    }

    private record SnapshotQuota(String periodType, int quotaLimit) {
    }

    public record DistributionQuotaView(String channelCode,
                                        String periodType,
                                        String periodKey,
                                        int usedCount,
                                        int quotaLimit) {
    }
}
