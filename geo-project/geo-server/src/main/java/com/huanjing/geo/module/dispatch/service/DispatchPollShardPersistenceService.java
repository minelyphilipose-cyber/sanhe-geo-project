package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.retention.service.PollRetentionSliceGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DispatchPollShardPersistenceService {

    static final String SHARD_STATUS_READY = "ready";
    static final String SHARD_STATUS_RUNNING = "running";
    static final String SHARD_STATUS_COMPLETED = "completed";
    static final String SHARD_STATUS_FAILED = "failed";
    static final String TRIGGER_TYPE_SCHEDULED = "SCHEDULED";

    private final PollBatchShardMapper pollBatchShardMapper;
    private final PollBatchShardItemMapper pollBatchShardItemMapper;
    private final PollResultMapper pollResultMapper;
    private final PollRetentionSliceGuardService retentionSliceGuardService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollResult ensurePollResult(PollResult result) {
        normalizeRequiredIdentity(result);
        retentionSliceGuardService.lockAndRequireWritable(result);
        PollResult existing = findPollResult(result);
        if (existing != null) {
            return existing;
        }
        pollResultMapper.insert(result);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollBatchShard markShardRunning(Long shardId, Long taskId) {
        PollBatchShard shard = pollBatchShardMapper.selectByIdForUpdate(shardId);
        if (shard == null) {
            return null;
        }
        if (SHARD_STATUS_COMPLETED.equals(shard.getStatus())) {
            return shard;
        }
        shard.setDispatchTaskId(taskId);
        shard.setStatus(SHARD_STATUS_RUNNING);
        shard.setStartedAt(shard.getStartedAt() == null ? LocalDateTime.now() : shard.getStartedAt());
        shard.setFinishedAt(null);
        shard.setLastError(null);
        pollBatchShardMapper.updateById(shard);
        return shard;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollResult upsertPollResultAndMarkItem(PollResult result, PollBatchShardItem item) {
        normalizeRequiredIdentity(result);
        retentionSliceGuardService.lockAndRequireWritable(result);
        PollResult existing = findPollResult(result);
        if (existing == null) {
            pollResultMapper.insert(result);
        } else {
            result.setId(existing.getId());
            pollResultMapper.updateById(result);
        }

        item.setPollResultId(result.getId());
        item.setStatus("completed".equals(result.getStatus()) ? "completed" : "failed");
        item.setLastError("completed".equals(result.getStatus()) ? null : trim(result.getDetailJson()));
        pollBatchShardItemMapper.updateById(item);
        return result;
    }

    private PollResult findPollResult(PollResult result) {
        LambdaQueryWrapper<PollResult> wrapper = new LambdaQueryWrapper<PollResult>()
                .eq(PollResult::getProjectId, result.getProjectId())
                .eq(PollResult::getPlatformId, result.getPlatformId())
                .eq(PollResult::getBatchDate, result.getBatchDate())
                .eq(PollResult::getBatchNo, result.getBatchNo())
                .eq(PollResult::getQuestionTier, result.getQuestionTier());
        if (result.getKeywordResultId() == null) {
            wrapper.isNull(PollResult::getKeywordResultId)
                    .eq(PollResult::getKeywordTextSnapshot, result.getKeywordTextSnapshot());
        } else {
            wrapper.eq(PollResult::getKeywordResultId, result.getKeywordResultId());
        }
        return pollResultMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private void normalizeRequiredIdentity(PollResult result) {
        Objects.requireNonNull(result, "Poll result must not be null");
        if (!StringUtils.hasText(result.getChannelCode())) {
            if (!StringUtils.hasText(result.getPlatformCode())) {
                throw new IllegalArgumentException("Poll result platformCode is required when channelCode is blank");
            }
            result.setChannelCode(result.getPlatformCode().trim());
        } else {
            result.setChannelCode(result.getChannelCode().trim());
        }
        if (!StringUtils.hasText(result.getTriggerType())) {
            result.setTriggerType(TRIGGER_TYPE_SCHEDULED);
        } else {
            result.setTriggerType(result.getTriggerType().trim());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markShardCompleted(Long shardId) {
        PollBatchShard shard = pollBatchShardMapper.selectByIdForUpdate(shardId);
        if (shard == null) {
            return;
        }
        long completed = pollBatchShardItemMapper.selectCount(
                new LambdaQueryWrapper<PollBatchShardItem>()
                        .eq(PollBatchShardItem::getShardId, shardId)
                        .eq(PollBatchShardItem::getStatus, "completed")
        );
        long failed = pollBatchShardItemMapper.selectCount(
                new LambdaQueryWrapper<PollBatchShardItem>()
                        .eq(PollBatchShardItem::getShardId, shardId)
                        .eq(PollBatchShardItem::getStatus, "failed")
        );
        shard.setCompletedCount((int) completed);
        shard.setFailedCount((int) failed);
        shard.setStatus(SHARD_STATUS_COMPLETED);
        shard.setFinishedAt(LocalDateTime.now());
        shard.setLastError(null);
        pollBatchShardMapper.updateById(shard);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markShardResourceWaiting(Long shardId, String error) {
        PollBatchShard shard = pollBatchShardMapper.selectByIdForUpdate(shardId);
        if (shard == null || SHARD_STATUS_COMPLETED.equals(shard.getStatus())) {
            return;
        }
        shard.setStatus(SHARD_STATUS_READY);
        shard.setResourceWaitCount((shard.getResourceWaitCount() == null ? 0 : shard.getResourceWaitCount()) + 1);
        shard.setLastError(trim(error));
        pollBatchShardMapper.updateById(shard);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long markShardFailed(Long shardId, String error) {
        PollBatchShard shard = pollBatchShardMapper.selectByIdForUpdate(shardId);
        if (shard == null || SHARD_STATUS_COMPLETED.equals(shard.getStatus())) {
            return shard == null ? null : shard.getBatchId();
        }
        shard.setStatus(SHARD_STATUS_FAILED);
        shard.setFinishedAt(LocalDateTime.now());
        shard.setLastError(trim(error));
        pollBatchShardMapper.updateById(shard);
        return shard.getBatchId();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 900 ? value : value.substring(0, 900);
    }
}
