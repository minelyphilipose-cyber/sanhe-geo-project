package com.huanjing.geo.module.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DispatchPollShardPersistenceService {

    static final String SHARD_STATUS_READY = "ready";
    static final String SHARD_STATUS_RUNNING = "running";
    static final String SHARD_STATUS_COMPLETED = "completed";
    static final String SHARD_STATUS_FAILED = "failed";
    private final PollBatchShardMapper pollBatchShardMapper;
    private final PollBatchShardItemMapper pollBatchShardItemMapper;
    private final PollResultPersistenceTransactionService resultPersistenceTransactionService;
    private final PollDatabaseWriteRetryService databaseWriteRetryService;
    private final ObjectMapper objectMapper;

    public PollResult ensurePollResult(PollResult result) {
        return databaseWriteRetryService.execute(
                "Poll result persistence",
                () -> resultPersistenceTransactionService.ensurePollResult(result));
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

    public PollResult upsertPollResultAndMarkItem(PollResult result, PollBatchShardItem item) {
        return databaseWriteRetryService.execute(
                "Poll result persistence",
                () -> resultPersistenceTransactionService.upsertPollResultAndMarkItem(result, item));
    }

    public void stagePollResult(PollBatchShardItem item, PollResult result) {
        String snapshotJson = writeSnapshot(result);
        databaseWriteRetryService.run(
                "Poll result staging",
                () -> resultPersistenceTransactionService.stagePollResult(item, result, snapshotJson));
    }

    public PollResult readStagedPollResult(PollBatchShardItem item) {
        if (item == null || item.getResultSnapshotJson() == null || item.getResultSnapshotJson().isBlank()) {
            return null;
        }
        try {
            PollResult result = objectMapper.readValue(item.getResultSnapshotJson(), PollResult.class);
            if (item.getKeywordResultId() != null
                    && !item.getKeywordResultId().equals(result.getKeywordResultId())) {
                throw new IllegalStateException("Poll result snapshot keyword does not match shard item " + item.getId());
            }
            return result;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read poll result snapshot for shard item " + item.getId(), ex);
        }
    }

    private String writeSnapshot(PollResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize poll result snapshot", ex);
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
