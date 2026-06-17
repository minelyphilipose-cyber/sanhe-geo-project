package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DispatchPollAggregationService {

    private static final String BATCH_STATUS_READY = "ready";
    private static final String BATCH_STATUS_PLANNING = "planning";
    private static final String BATCH_STATUS_FINISHED = "finished";
    private static final String BATCH_STATUS_FINISHED_WITH_FAILURES = "finished_with_failures";
    private static final String BATCH_STATUS_FAILED = "failed";

    private final PollBatchMapper pollBatchMapper;
    private final PollBatchShardMapper pollBatchShardMapper;
    private final PollResultMapper pollResultMapper;
    private final PollDailyStatMapper pollDailyStatMapper;
    private final StringRedisTemplate redisTemplate;
    private final ProjectMapper projectMapper;
    private final PollSummaryRecomputeService pollSummaryRecomputeService;
    private final DispatchAlertService dispatchAlertService;

    public void tryAggregateBatch(Long batchId) {
        if (batchId == null) {
            return;
        }
        String lockKey = "geo:poll:aggregate:" + batchId;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofMinutes(10));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            aggregateBatchIfReady(batchId);
        } finally {
            Object current = redisTemplate.opsForValue().get(lockKey);
            if (lockValue.equals(current)) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    public void recoverFinishedAggregations(int limit) {
        failStalePlanningBatches(limit);
        List<PollBatch> batches = pollBatchMapper.selectList(
                new LambdaQueryWrapper<PollBatch>()
                        .eq(PollBatch::getStatus, BATCH_STATUS_READY)
                        .isNull(PollBatch::getFinishedAt)
                        .gt(PollBatch::getTotalShardCount, 0)
                        .orderByAsc(PollBatch::getUpdatedAt)
                        .last("LIMIT " + Math.max(limit, 1))
        );
        for (PollBatch batch : batches) {
            tryAggregateBatch(batch.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStalePlanningBatches(int limit) {
        List<PollBatch> staleBatches = pollBatchMapper.selectList(
                new LambdaQueryWrapper<PollBatch>()
                        .eq(PollBatch::getStatus, BATCH_STATUS_PLANNING)
                        .lt(PollBatch::getPlanningStartedAt, LocalDateTime.now().minusMinutes(30))
                        .orderByAsc(PollBatch::getPlanningStartedAt)
                        .last("LIMIT " + Math.max(limit, 1))
        );
        for (PollBatch batch : staleBatches) {
            batch.setStatus(BATCH_STATUS_FAILED);
            pollBatchMapper.updateById(batch);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aggregateBatchIfReady(Long batchId) {
        PollBatch batch = pollBatchMapper.selectByIdForUpdate(batchId);
        if (batch == null || batch.getFinishedAt() != null) {
            return;
        }
        int totalShardCount = batch.getTotalShardCount() == null ? 0 : batch.getTotalShardCount();
        if (totalShardCount <= 0) {
            return;
        }
        long terminalShardCount = pollBatchShardMapper.countTerminalByBatchId(batchId);
        batch.setCompletedShardCount((int) terminalShardCount);
        if (terminalShardCount < totalShardCount) {
            pollBatchMapper.updateById(batch);
            return;
        }

        List<PollResult> results = pollResultMapper.selectList(
                new LambdaQueryWrapper<PollResult>()
                        .eq(PollResult::getBatchId, batchId)
        );
        List<PollBatchShard> shards = pollBatchShardMapper.selectByBatchId(batchId);
        boolean hasFailedShard = shards.stream().anyMatch(shard -> "failed".equals(shard.getStatus()));
        Project project = projectMapper.selectById(batch.getProjectId());
        String projectName = project == null ? "" : project.getProjectName();

        int expectedResultCount = Math.max(0, defaultInt(batch.getTotalQuestionCount()) * defaultInt(batch.getTotalPlatformCount()));
        int totalCompleted = 0;
        int totalFailed = 0;
        int totalHit = 0;
        Map<Long, PlatformAgg> aggByPlatform = new LinkedHashMap<>();
        for (PollBatchShard shard : shards) {
            aggByPlatform.computeIfAbsent(shard.getPlatformId(), key -> new PlatformAgg(shard))
                    .expectedCount += defaultInt(shard.getExpectedCount());
        }
        for (PollResult result : results) {
            PlatformAgg agg = aggByPlatform.get(result.getPlatformId());
            if (agg == null) {
                continue;
            }
            agg.questionCount++;
            agg.requestCount += Math.max(result.getRequestCount() == null ? 0 : result.getRequestCount(), 0);
            if ("completed".equals(result.getStatus())) {
                totalCompleted++;
                agg.completedCount++;
                if (Boolean.TRUE.equals(result.getIsHit())) {
                    totalHit++;
                    agg.hitCount++;
                }
                if (Boolean.TRUE.equals(result.getSiteMentioned())) {
                    agg.siteMentionCount++;
                }
                int contactMentionCount = result.getContactMentionCount() == null
                        ? (Boolean.TRUE.equals(result.getContactMentioned()) ? 1 : 0)
                        : Math.max(result.getContactMentionCount(), 0);
                if (contactMentionCount > 0) {
                    agg.contactMentionCount += contactMentionCount;
                }
            } else {
                totalFailed++;
                agg.failedCount++;
            }
        }

        for (PlatformAgg agg : aggByPlatform.values()) {
            int missingCount = Math.max(0, agg.expectedCount - agg.questionCount);
            agg.failedCount += missingCount;
            PollDailyStat stat = new PollDailyStat();
            stat.setBatchId(batch.getId());
            stat.setDispatchTaskId(null);
            stat.setProjectId(batch.getProjectId());
            stat.setProjectName(projectName);
            stat.setPlatformId(agg.platformId);
            stat.setPlatformCode(agg.platformCode);
            stat.setPlatformName(agg.platformName);
            stat.setBatchDate(batch.getBatchDate());
            stat.setBatchNo(batch.getBatchNo());
            stat.setQuestionTier(batch.getQuestionTier());
            stat.setQuestionCount(agg.questionCount);
            stat.setRequestCount(agg.requestCount);
            stat.setCompletedCount(agg.completedCount);
            stat.setFailedCount(agg.failedCount);
            stat.setHitCount(agg.hitCount);
            stat.setSiteMentionCount(agg.siteMentionCount);
            stat.setContactMentionCount(agg.contactMentionCount);
            stat.setHitRate(agg.completedCount <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(agg.hitCount).divide(BigDecimal.valueOf(agg.completedCount), 4, RoundingMode.HALF_UP));
            upsertPollStat(stat);
        }

        int missingResultCount = Math.max(0, expectedResultCount - results.size());
        int finalFailedCount = totalFailed + missingResultCount;
        batch.setQuestionCount(results.size());
        batch.setCompletedCount(totalCompleted);
        batch.setFailedCount(finalFailedCount);
        batch.setHitCount(totalHit);
        batch.setOverallHitRate(totalCompleted <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalHit).divide(BigDecimal.valueOf(totalCompleted), 4, RoundingMode.HALF_UP));
        batch.setCompletedShardCount((int) terminalShardCount);
        batch.setStatus(hasFailedShard ? BATCH_STATUS_FINISHED_WITH_FAILURES : BATCH_STATUS_FINISHED);
        batch.setFinishedAt(LocalDateTime.now());
        pollBatchMapper.updateById(batch);
        publishFailureAlertIfNeeded(batch, projectName, aggByPlatform, results, expectedResultCount, finalFailedCount, hasFailedShard);
        recomputeSummaryAfterCommit(batch.getProjectId(), batch.getBatchDate(), batch.getQuestionTier());
    }

    private void publishFailureAlertIfNeeded(PollBatch batch,
                                             String projectName,
                                             Map<Long, PlatformAgg> aggByPlatform,
                                             List<PollResult> results,
                                             int expectedResultCount,
                                             int finalFailedCount,
                                             boolean hasFailedShard) {
        if (finalFailedCount <= 0 && !hasFailedShard) {
            return;
        }
        Map<Long, PlatformFailureDetail> details = buildFailureDetails(aggByPlatform, results);
        List<Map<String, Object>> platformFailures = details.values().stream()
                .filter(detail -> detail.failedCount > 0)
                .map(PlatformFailureDetail::toPayload)
                .toList();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("alertType", "question_poll_daily_summary");
        context.put("batchId", batch.getId());
        context.put("projectId", batch.getProjectId());
        context.put("projectName", projectName);
        context.put("batchDate", batch.getBatchDate() == null ? null : batch.getBatchDate().toString());
        context.put("batchNo", batch.getBatchNo());
        context.put("questionTier", batch.getQuestionTier());
        context.put("expectedResultCount", expectedResultCount);
        context.put("completedCount", defaultInt(batch.getCompletedCount()));
        context.put("failedCount", finalFailedCount);
        context.put("failureRate", failureRate(finalFailedCount, expectedResultCount));
        context.put("hasFailedShard", hasFailedShard);
        context.put("platformFailures", platformFailures);

        String dedupeKey = "question_poll_batch:" + batch.getProjectId() + ":" + batch.getBatchDate()
                + ":" + batch.getBatchNo() + ":" + batch.getQuestionTier();
        String content = "问题轮询跑批完成，存在 " + finalFailedCount + " 条失败结果，失败率 "
                + failureRate(finalFailedCount, expectedResultCount) + "%";
        dispatchAlertService.createOrRefreshAlert(
                batch.getDispatchTaskId(),
                batch.getProjectId(),
                dedupeKey,
                hasFailedShard ? DispatchAlertSeverity.ERROR : DispatchAlertSeverity.WARN,
                "Question poll daily batch completed with failures",
                content,
                0,
                JSONUtil.toJsonStr(context)
        );
    }

    private Map<Long, PlatformFailureDetail> buildFailureDetails(Map<Long, PlatformAgg> aggByPlatform, List<PollResult> results) {
        Map<Long, PlatformFailureDetail> details = new LinkedHashMap<>();
        for (PlatformAgg agg : aggByPlatform.values()) {
            PlatformFailureDetail detail = new PlatformFailureDetail();
            detail.platformId = agg.platformId;
            detail.platformCode = agg.platformCode;
            detail.platformName = agg.platformName;
            detail.expectedCount = agg.expectedCount;
            detail.completedCount = agg.completedCount;
            detail.failedCount = agg.failedCount;
            detail.requestCount = agg.requestCount;
            details.put(agg.platformId, detail);
        }
        for (PollResult result : results) {
            if (!"failed".equals(result.getStatus())) {
                continue;
            }
            PlatformFailureDetail detail = details.get(result.getPlatformId());
            if (detail == null) {
                continue;
            }
            FailureReason reason = extractFailureReason(result);
            FailureReason existing = detail.reasons.get(reason.key());
            if (existing == null) {
                detail.reasons.put(reason.key(), reason);
            } else {
                existing.count += reason.count;
            }
        }
        return details;
    }

    private FailureReason extractFailureReason(PollResult result) {
        String errorCode = "UNKNOWN";
        String errorMessage = "unknown failure";
        if (result.getDetailJson() != null && JSONUtil.isTypeJSONObject(result.getDetailJson())) {
            try {
                JSONObject detail = JSONUtil.parseObj(result.getDetailJson());
                JSONObject payload = detail.getJSONObject("error_payload");
                if (payload != null) {
                    String code = payload.getStr("error_code");
                    String message = payload.getStr("error_message");
                    if (code != null && !code.isBlank()) {
                        errorCode = code;
                    }
                    if (message != null && !message.isBlank()) {
                        errorMessage = message.length() <= 300 ? message : message.substring(0, 300);
                    }
                }
            } catch (Exception ignore) {
                // Keep the fallback reason when historical detail_json is malformed.
            }
        }
        FailureReason reason = new FailureReason();
        reason.errorCode = errorCode;
        reason.errorMessage = errorMessage;
        reason.count = 1;
        return reason;
    }

    private double failureRate(int failedCount, int totalCount) {
        if (totalCount <= 0) {
            return failedCount > 0 ? 100D : 0D;
        }
        return BigDecimal.valueOf(failedCount * 100D)
                .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private void recomputeSummaryAfterCommit(Long projectId, LocalDate batchDate, String questionTier) {
        if (projectId == null || batchDate == null) {
            return;
        }
        Runnable recompute = () -> {
            try {
                PollSummaryRecomputeService.RecomputeResult result =
                        pollSummaryRecomputeService.recomputeSlice(projectId, batchDate, questionTier);
                if (result.skipped()) {
                    log.info("Skip poll summary recompute, projectId={}, batchDate={}, tier={}, reason={}",
                            projectId, batchDate, questionTier, result.skipReason());
                }
            } catch (Exception ex) {
                log.warn("Poll summary recompute failed after batch aggregation, projectId={}, batchDate={}, tier={}",
                        projectId, batchDate, questionTier, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recompute.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recompute.run();
            }
        });
    }

    private void upsertPollStat(PollDailyStat stat) {
        PollDailyStat existing = pollDailyStatMapper.selectOne(
                new LambdaQueryWrapper<PollDailyStat>()
                        .eq(PollDailyStat::getProjectId, stat.getProjectId())
                        .eq(PollDailyStat::getPlatformId, stat.getPlatformId())
                        .eq(PollDailyStat::getBatchDate, stat.getBatchDate())
                        .eq(PollDailyStat::getBatchNo, stat.getBatchNo())
                        .eq(PollDailyStat::getQuestionTier, stat.getQuestionTier())
                        .last("LIMIT 1")
        );
        if (existing == null) {
            pollDailyStatMapper.insert(stat);
            return;
        }
        stat.setId(existing.getId());
        pollDailyStatMapper.updateById(stat);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static final class PlatformAgg {
        private final Long platformId;
        private final String platformCode;
        private final String platformName;
        private int expectedCount;
        private int questionCount;
        private int requestCount;
        private int completedCount;
        private int failedCount;
        private int hitCount;
        private int siteMentionCount;
        private int contactMentionCount;

        private PlatformAgg(PollBatchShard shard) {
            this.platformId = shard.getPlatformId();
            this.platformCode = shard.getPlatformCode();
            this.platformName = shard.getPlatformName();
        }
    }

    private static final class PlatformFailureDetail {
        private Long platformId;
        private String platformCode;
        private String platformName;
        private int expectedCount;
        private int completedCount;
        private int failedCount;
        private int requestCount;
        private final Map<String, FailureReason> reasons = new LinkedHashMap<>();

        private Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("platformId", platformId);
            payload.put("platformCode", platformCode);
            payload.put("platformName", platformName);
            payload.put("expectedCount", expectedCount);
            payload.put("completedCount", completedCount);
            payload.put("failedCount", failedCount);
            payload.put("failureRate", expectedCount <= 0 ? 0D
                    : BigDecimal.valueOf(failedCount * 100D)
                    .divide(BigDecimal.valueOf(expectedCount), 2, RoundingMode.HALF_UP)
                    .doubleValue());
            payload.put("requestCount", requestCount);
            payload.put("reasons", reasons.values().stream().map(FailureReason::toPayload).toList());
            return payload;
        }
    }

    private static final class FailureReason {
        private String errorCode;
        private String errorMessage;
        private int count;

        private String key() {
            return errorCode + "\n" + errorMessage;
        }

        private Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("errorCode", errorCode);
            payload.put("errorMessage", errorMessage);
            payload.put("count", count);
            return payload;
        }
    }
}
