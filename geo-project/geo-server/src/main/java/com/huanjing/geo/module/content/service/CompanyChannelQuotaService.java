package com.huanjing.geo.module.content.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.QuotaPeriodResolver;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.content.distribution.DistributionTargetKind;
import com.huanjing.geo.module.content.dto.ChannelQuotaSnapshotItem;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaUsage;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaLedgerMapper;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaUsageMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.customer.entity.CompanyPackageBinding;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyChannelQuotaService {

    private static final java.time.ZoneId BUSINESS_ZONE = QuotaPeriodResolver.BUSINESS_ZONE;
    private static final String BIZ_TYPE_DISTRIBUTION = "distribution";
    private static final String BIZ_TYPE_SELF_MEDIA_SCHEDULE = "self_media_schedule";
    private static final int RESERVED_TIMEOUT_MINUTES = 30;
    private static final int RESERVED_SCAN_BATCH_SIZE = 200;
    private static final int QUOTA_RESERVE_MAX_ATTEMPTS = 2;
    private static final int QUOTA_RESERVE_LOCK_WAIT_SECONDS = 2;
    private static final Set<String> SUCCESS_TASK_STATUS = Set.of("submitted", "confirmed", "published");
    private static final Set<String> FAILED_TASK_STATUS = Set.of("failed", "cancelled", "canceled");

    private final CompanyPackageBindingService companyPackageBindingService;
    private final CompanyChannelQuotaUsageMapper usageMapper;
    private final CompanyChannelQuotaLedgerMapper ledgerMapper;
    private final DistributionTaskMapper distributionTaskMapper;
    private final SelfMediaAccountMapper selfMediaAccountMapper;
    private final SystemAlertService systemAlertService;
    private final PlatformTransactionManager transactionManager;

    public CompanyChannelQuotaLedger reserveDistribution(Long companyId, Long projectId, String targetKind, Long distributionTaskId) {
        if (distributionTaskId == null) {
            throw new BizException(400, "distribution_task_id is required for quota reservation");
        }
        String channel = resolveDistributionChannel(targetKind, distributionTaskId);
        return reserveDistributionForChannel(companyId, projectId, channel, distributionTaskId);
    }

    public CompanyChannelQuotaLedger reserveSelfMediaDistribution(Long companyId,
                                                                  Long projectId,
                                                                  String platform,
                                                                  Long distributionTaskId) {
        if (distributionTaskId == null) {
            throw new BizException(400, "distribution_task_id is required for quota reservation");
        }
        String channel = resolveSelfMediaPlatformChannel(platform);
        return reserveDistributionForChannel(companyId, projectId, channel, distributionTaskId);
    }

    public CompanyChannelQuotaLedger reserveSelfMediaSchedule(Long companyId,
                                                              Long projectId,
                                                              String platform,
                                                              Long scheduleId) {
        if (scheduleId == null) {
            throw new BizException(400, "schedule_id is required for quota reservation");
        }
        return reserveSelfMediaSchedules(companyId, List.of(new SelfMediaScheduleQuotaReservation(
                projectId,
                platform,
                scheduleId
        ))).get(0);
    }

    public List<CompanyChannelQuotaLedger> reserveSelfMediaSchedules(Long companyId,
                                                                     List<SelfMediaScheduleQuotaReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }
        List<BizQuotaReservation> bizReservations = new ArrayList<>();
        for (SelfMediaScheduleQuotaReservation reservation : reservations) {
            if (reservation == null || reservation.scheduleId() == null) {
                throw new BizException(400, "schedule_id is required for quota reservation");
            }
            String channel = resolveSelfMediaPlatformChannel(reservation.platform());
            bizReservations.add(new BizQuotaReservation(
                    reservation.projectId(),
                    channel,
                    BIZ_TYPE_SELF_MEDIA_SCHEDULE,
                    String.valueOf(reservation.scheduleId())
            ));
        }
        return reserveForBizBatch(companyId, bizReservations);
    }

    private CompanyChannelQuotaLedger reserveDistributionForChannel(Long companyId,
                                                                    Long projectId,
                                                                    String channel,
                                                                    Long distributionTaskId) {
        return reserveForBiz(companyId, projectId, channel, BIZ_TYPE_DISTRIBUTION, String.valueOf(distributionTaskId));
    }

    private CompanyChannelQuotaLedger reserveForBiz(Long companyId,
                                                    Long projectId,
                                                    String channel,
                                                    String bizType,
                                                    String bizId) {
        return reserveForBizBatch(companyId, List.of(new BizQuotaReservation(projectId, channel, bizType, bizId))).get(0);
    }

    private List<CompanyChannelQuotaLedger> reserveForBizBatch(Long companyId,
                                                               List<BizQuotaReservation> reservations) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= QUOTA_RESERVE_MAX_ATTEMPTS; attempt++) {
            try {
                return reserveForBizBatchInNewTransaction(companyId, reservations);
            } catch (RuntimeException ex) {
                if (!isTransientQuotaLockFailure(ex)) {
                    throw ex;
                }
                lastFailure = ex;
                if (isLockWaitTimeout(ex) || attempt == QUOTA_RESERVE_MAX_ATTEMPTS) {
                    break;
                }
                sleepBeforeQuotaRetry(attempt);
            }
        }
        log.warn("Quota reservation lock conflict, companyId={}, reservationCount={}, reason={}",
                companyId, reservations == null ? 0 : reservations.size(),
                lastFailure == null ? "unknown" : lastFailure.getMessage());
        log.debug("Quota reservation lock conflict stack", lastFailure);
        throw new BizException(409, "渠道配额正在被其它排期任务占用，请稍后重试", lastFailure);
    }

    private List<CompanyChannelQuotaLedger> reserveForBizBatchInNewTransaction(Long companyId,
                                                                               List<BizQuotaReservation> reservations) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> reserveForBizBatchInTransaction(companyId, reservations));
    }

    private List<CompanyChannelQuotaLedger> reserveForBizBatchInTransaction(Long companyId,
                                                                            List<BizQuotaReservation> reservations) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        List<CompanyChannelQuotaLedger> ledgers = new ArrayList<>();
        Map<QuotaGroupKey, List<BizQuotaReservation>> grouped = new LinkedHashMap<>();
        for (BizQuotaReservation reservation : reservations) {
            CompanyChannelQuotaLedger existed = ledgerMapper.selectByBiz(reservation.bizType(), reservation.bizId());
            if (existed != null) {
                ledgers.add(existed);
                continue;
            }
            SnapshotQuota quota = resolveSnapshotQuota(binding, reservation.channel());
            String periodKey = periodKey(quota.periodType());
            QuotaGroupKey key = new QuotaGroupKey(reservation.channel(), quota.periodType(), periodKey, quota.quotaLimit());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reservation);
        }

        for (Map.Entry<QuotaGroupKey, List<BizQuotaReservation>> entry : grouped.entrySet()) {
            QuotaGroupKey key = entry.getKey();
            List<BizQuotaReservation> groupReservations = entry.getValue();
            reserveQuotaGroup(companyId, key, groupReservations.size());
            for (BizQuotaReservation reservation : groupReservations) {
                CompanyChannelQuotaLedger ledger = createReservedLedger(companyId, reservation, key);
                ledgerMapper.insert(ledger);
                ledgers.add(ledger);
            }
        }
        return ledgers;
    }

    private void reserveQuotaGroup(Long companyId, QuotaGroupKey key, int amount) {
        try {
            usageMapper.setSessionLockWaitTimeout(QUOTA_RESERVE_LOCK_WAIT_SECONDS);
            usageMapper.insertIgnore(companyId, key.channel(), key.periodType(), key.periodKey(), key.quotaLimit());
            usageMapper.updateQuotaLimit(companyId, key.channel(), key.periodType(), key.periodKey(), key.quotaLimit());
            int reserved = amount == 1
                    ? usageMapper.tryReserve(companyId, key.channel(), key.periodType(), key.periodKey())
                    : usageMapper.tryReserveAmount(companyId, key.channel(), key.periodType(), key.periodKey(), amount);
            if (reserved != 1) {
                throw new BizException(400, "Distribution quota exhausted for channel " + key.channel());
            }
        } finally {
            resetQuotaLockWaitTimeout(companyId, key.channel(), key.periodType(), key.periodKey());
        }
    }

    private CompanyChannelQuotaLedger createReservedLedger(Long companyId,
                                                           BizQuotaReservation reservation,
                                                           QuotaGroupKey key) {
        CompanyChannelQuotaLedger ledger = new CompanyChannelQuotaLedger();
        ledger.setCompanyId(companyId);
        ledger.setProjectId(reservation.projectId());
        ledger.setChannelCode(key.channel());
        ledger.setPeriodType(key.periodType());
        ledger.setPeriodKey(key.periodKey());
        ledger.setDeltaCount(1);
        ledger.setStatus("reserved");
        ledger.setBizType(reservation.bizType());
        ledger.setBizId(reservation.bizId());
        ledger.setReservedAt(LocalDateTime.now(BUSINESS_ZONE));
        return ledger;
    }

    private void resetQuotaLockWaitTimeout(Long companyId, String channel, String periodType, String periodKey) {
        try {
            usageMapper.resetSessionLockWaitTimeout();
        } catch (RuntimeException ex) {
            log.warn("Quota lock wait timeout reset failed, companyId={}, channel={}, periodType={}, periodKey={}, reason={}",
                    companyId, channel, periodType, periodKey, ex.getMessage());
            log.debug("Quota lock wait timeout reset failure stack", ex);
        }
    }

    private boolean isTransientQuotaLockFailure(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof CannotAcquireLockException
                    || current instanceof DeadlockLoserDataAccessException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof QueryTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("Lock wait timeout exceeded")
                    || message.contains("Deadlock found")
                    || message.contains("Statement cancelled due to timeout"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isLockWaitTimeout(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && (message.contains("Lock wait timeout exceeded")
                    || message.contains("Statement cancelled due to timeout"))) {
                return true;
            }
        }
        return false;
    }

    private void sleepBeforeQuotaRetry(int attempt) {
        try {
            Thread.sleep(100L * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BizException(409, "渠道配额正在被其它排期任务占用，请稍后重试", interrupted);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmDistribution(Long distributionTaskId) {
        updateReservedLedger(distributionTaskId, "confirmed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmSelfMediaSchedule(Long scheduleId) {
        updateReservedLedger(BIZ_TYPE_SELF_MEDIA_SCHEDULE, scheduleId, "confirmed");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundDistribution(Long distributionTaskId) {
        CompanyChannelQuotaLedger ledger = updateReservedLedger(distributionTaskId, "refunded");
        if (ledger == null) {
            return;
        }
        releaseUsage(ledger);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundSelfMediaSchedule(Long scheduleId) {
        CompanyChannelQuotaLedger ledger = updateReservedLedger(BIZ_TYPE_SELF_MEDIA_SCHEDULE, scheduleId, "refunded");
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
        return distributionQuotaForChannel(companyId, channel);
    }

    public DistributionQuotaView selfMediaDistributionQuota(Long companyId, String platform) {
        String channel = resolveSelfMediaPlatformChannel(platform);
        return distributionQuotaForChannel(companyId, channel);
    }

    private DistributionQuotaView distributionQuotaForChannel(Long companyId, String channel) {
        CompanyPackageBinding binding = companyPackageBindingService.requireActiveBinding(companyId);
        SnapshotQuota quota = resolveSnapshotQuota(binding, channel);
        String periodKey = periodKey(quota.periodType());
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
        CompanyChannelQuotaLedger ledger = findLedger(BIZ_TYPE_DISTRIBUTION, distributionTaskId);
        if (ledger == null) {
            return null;
        }
        return updateLedgerStatusFromReserved(ledger, targetStatus);
    }

    private CompanyChannelQuotaLedger findDistributionLedger(Long distributionTaskId) {
        return findLedger(BIZ_TYPE_DISTRIBUTION, distributionTaskId);
    }

    private CompanyChannelQuotaLedger updateReservedLedger(String bizType, Long bizId, String targetStatus) {
        CompanyChannelQuotaLedger ledger = findLedger(bizType, bizId);
        if (ledger == null) {
            return null;
        }
        return updateLedgerStatusFromReserved(ledger, targetStatus);
    }

    private CompanyChannelQuotaLedger findLedger(String bizType, Long bizId) {
        if (bizId == null || !StringUtils.hasText(bizType)) {
            return null;
        }
        return ledgerMapper.selectByBiz(bizType, String.valueOf(bizId));
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
            case DistributionTargetKind.FORUM_SITE -> "forum";
            case DistributionTargetKind.MP_ACCOUNT -> throw new BizException(400, "Self-media quota requires a concrete platform");
            case DistributionTargetKind.AUTHORITY_MEDIA -> "authority_media";
            case DistributionTargetKind.SITE -> throw new BizException(400, "Legacy site target is not supported by company package channel quota");
            default -> throw new BizException(400, "Unsupported distribution target kind: " + targetKind);
        };
    }

    private String resolveDistributionChannel(String targetKind, Long distributionTaskId) {
        String normalizedTargetKind = StringUtils.hasText(targetKind) ? targetKind.trim() : targetKind;
        if (DistributionTargetKind.MP_ACCOUNT.equals(normalizedTargetKind)) {
            DistributionTask task = distributionTaskMapper.selectById(distributionTaskId);
            if (task == null || task.getSelfMediaAccountId() == null) {
                throw new BizException(400, "Self-media distribution task is missing self-media account");
            }
            SelfMediaAccount account = selfMediaAccountMapper.selectById(task.getSelfMediaAccountId());
            if (account == null || !StringUtils.hasText(account.getPlatform())) {
                throw new BizException(400, "Self-media account is missing platform");
            }
            return resolveSelfMediaPlatformChannel(account.getPlatform());
        }
        return mapTargetKind(normalizedTargetKind);
    }

    private String resolveSelfMediaPlatformChannel(String platformValue) {
        if (!StringUtils.hasText(platformValue)) {
            throw new BizException(400, "Self-media account is missing platform");
        }
        String platform = ArticlePromptChannels.canonicalSubCode(
                ArticlePromptChannels.SELF_MEDIA,
                platformValue.trim().toLowerCase(Locale.ROOT)
        );
        if (!ArticlePromptChannels.SELF_MEDIA_SUBS.contains(platform)) {
            throw new BizException(400, "Unsupported self-media platform for quota: " + platformValue);
        }
        return ArticlePromptChannels.SELF_MEDIA + ":" + platform;
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

    public record SelfMediaScheduleQuotaReservation(Long projectId,
                                                    String platform,
                                                    Long scheduleId) {
    }

    private record BizQuotaReservation(Long projectId,
                                       String channel,
                                       String bizType,
                                       String bizId) {
    }

    private record QuotaGroupKey(String channel,
                                 String periodType,
                                 String periodKey,
                                 int quotaLimit) {
    }

    public record DistributionQuotaView(String channelCode,
                                        String periodType,
                                        String periodKey,
                                        int usedCount,
                                        int quotaLimit) {
    }
}
