package com.huanjing.geo.module.dispatch.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchTaskStateService {

    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchQueueService dispatchQueueService;
    private final DispatchAlertService dispatchAlertService;
    private final DispatchPollShardPersistenceService pollShardPersistenceService;
    private final DispatchPollAggregationService pollAggregationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DispatchTask markRunning(Long taskId, int timeoutMinutes) {
        LocalDateTime now = LocalDateTime.now();
        int updated = dispatchTaskMapper.claimRunnableTask(
                taskId,
                DispatchTaskStatus.PENDING.value(),
                DispatchTaskStatus.RETRY_PENDING.value(),
                DispatchTaskStatus.RUNNING.value(),
                now,
                now.plusMinutes(timeoutMinutes)
        );
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        if (updated > 0) {
            return task;
        }
        dispatchQueueService.clearQueueMark(taskId);
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long taskId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        task.setStatus(DispatchTaskStatus.COMPLETED.value());
        task.setFinishedAt(LocalDateTime.now());
        task.setLastError(null);
        task.setErrorContext(null);
        dispatchTaskMapper.updateById(task);
        dispatchQueueService.clearQueueMark(taskId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(Long taskId, String reason) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        task.setStatus(DispatchTaskStatus.CANCELLED.value());
        task.setFinishedAt(LocalDateTime.now());
        task.setNextRetryAt(null);
        task.setTimeoutAt(null);
        task.setLastError(reason);
        dispatchTaskMapper.updateById(task);
        dispatchQueueService.clearQueueMark(taskId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryPending(Long taskId,
                                 int nextRetryCount,
                                 LocalDateTime nextRetryAt,
                                 String lastError,
                                 String errorContext,
                                 String payloadJson,
                                 Long projectId,
                                 Exception ex) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        task.setRetryCount(nextRetryCount);
        task.setLastError(lastError);
        task.setErrorContext(errorContext);
        task.setStatus(DispatchTaskStatus.RETRY_PENDING.value());
        task.setNextRetryAt(nextRetryAt);
        dispatchTaskMapper.updateById(task);
        // Capacity backoff is intentionally re-enqueued by the recovery scanner once nextRetryAt is due.
        // That keeps worker threads from spinning or blocking on shared LLM permits.
        dispatchQueueService.clearQueueMark(taskId);
        dispatchAlertService.createAlert(
                taskId,
                projectId,
                DispatchAlertSeverity.WARN,
                "Dispatch task failed and will retry",
                lastError,
                nextRetryCount,
                payloadJson
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markResourceWaiting(Long taskId,
                                    int nextResourceWaitCount,
                                    LocalDateTime nextRetryAt,
                                    String lastError,
                                    String errorContext,
                                    String payloadJson,
                                    Long projectId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        task.setLastError(lastError);
        task.setErrorContext(errorContext);
        task.setResourceWaitCount(nextResourceWaitCount);
        task.setStatus(DispatchTaskStatus.RETRY_PENDING.value());
        task.setNextRetryAt(nextRetryAt);
        dispatchTaskMapper.updateById(task);
        dispatchQueueService.clearQueueMark(taskId);
        dispatchAlertService.createAlert(
                taskId,
                projectId,
                DispatchAlertSeverity.WARN,
                "Dispatch task postponed for shared LLM capacity",
                lastError,
                task.getRetryCount() == null ? 0 : task.getRetryCount(),
                payloadJson
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDatabaseWaiting(Long taskId,
                                    int nextResourceWaitCount,
                                    LocalDateTime nextRetryAt,
                                    String lastError,
                                    String errorContext) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        task.setLastError(lastError);
        task.setErrorContext(errorContext);
        task.setResourceWaitCount(nextResourceWaitCount);
        task.setStatus(DispatchTaskStatus.RETRY_PENDING.value());
        task.setNextRetryAt(nextRetryAt);
        dispatchTaskMapper.updateById(task);
        dispatchQueueService.clearQueueMark(taskId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeadLetter(Long taskId,
                               String reason,
                               String errorContext,
                               Integer retryCount,
                               String payloadJson,
                               Long projectId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        task.setStatus(DispatchTaskStatus.DEAD_LETTER.value());
        task.setFinishedAt(LocalDateTime.now());
        task.setLastError(reason);
        task.setErrorContext(errorContext);
        dispatchTaskMapper.updateById(task);
        dispatchQueueService.clearQueueMark(taskId);
        dispatchAlertService.createAlert(
                taskId,
                projectId,
                DispatchAlertSeverity.ERROR,
                "Dispatch task entered dead letter",
                reason,
                retryCount == null ? 0 : retryCount,
                payloadJson
        );
    }

    public void reclaimTimedOutRunningTasks(int limit) {
        List<DispatchTask> timedOutTasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getStatus, DispatchTaskStatus.RUNNING.value())
                        .isNotNull(DispatchTask::getTimeoutAt)
                        .lt(DispatchTask::getTimeoutAt, LocalDateTime.now())
                        .orderByAsc(DispatchTask::getTimeoutAt, DispatchTask::getId)
                        .last("LIMIT " + Math.max(limit, 1))
        );

        for (DispatchTask task : timedOutTasks) {
            reclaimTimedOutRunningTask(task);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reclaimTimedOutRunningTask(DispatchTask task) {
        LocalDateTime now = LocalDateTime.now();
        String reason = "task execution timeout";
        String errorContext = JSONUtil.toJsonStr(buildErrorContext(
                reason,
                null,
                task.getCurrentChannel(),
                task.getPlatformCode(),
                now
        ));
        int updated = dispatchTaskMapper.claimTimedOutRunningTask(
                task.getId(),
                DispatchTaskStatus.RUNNING.value(),
                DispatchTaskStatus.DEAD_LETTER.value(),
                now,
                reason,
                errorContext
        );
        if (updated <= 0) {
            return;
        }
        dispatchQueueService.clearQueueMark(task.getId());
        dispatchAlertService.createAlert(
                task.getId(),
                task.getProjectId(),
                DispatchAlertSeverity.ERROR,
                "Dispatch task timed out while running",
                reason,
                task.getRetryCount() == null ? 0 : task.getRetryCount(),
                task.getPayloadJson()
        );
        markPollShardFailedIfPresent(task, reason);
    }

    private void markPollShardFailedIfPresent(DispatchTask task, String reason) {
        Long shardId = resolveShardId(task);
        if (shardId == null) {
            return;
        }
        try {
            Long batchId = pollShardPersistenceService.markShardFailed(shardId, reason);
            pollAggregationService.tryAggregateBatch(batchId);
        } catch (Exception ex) {
            log.warn("Failed to sync timed-out dispatch task to poll shard, taskId={}, shardId={}",
                    task.getId(), shardId, ex);
        }
    }

    private Long resolveShardId(DispatchTask task) {
        if (task == null || task.getPayloadJson() == null || task.getPayloadJson().isBlank()) {
            return null;
        }
        try {
            Object value = JSONUtil.parseObj(task.getPayloadJson()).get("shardId");
            if (value == null || String.valueOf(value).isBlank()) {
                return null;
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private Map<String, Object> buildErrorContext(String error,
                                                  String exception,
                                                  String channel,
                                                  String platformCode,
                                                  LocalDateTime at) {
        Map<String, Object> context = new HashMap<>();
        context.put("error", error);
        context.put("at", at == null ? null : at.toString());
        if (exception != null) {
            context.put("exception", exception);
        }
        if (channel != null) {
            context.put("channel", channel);
        }
        if (platformCode != null) {
            context.put("platformCode", platformCode);
        }
        return context;
    }
}
