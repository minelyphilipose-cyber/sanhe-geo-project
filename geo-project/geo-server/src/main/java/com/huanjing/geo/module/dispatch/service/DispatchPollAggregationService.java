package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
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
        batch.setStatus(hasFailedShard || finalFailedCount > 0 ? BATCH_STATUS_FINISHED_WITH_FAILURES : BATCH_STATUS_FINISHED);
        batch.setFinishedAt(LocalDateTime.now());
        pollBatchMapper.updateById(batch);
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
}
