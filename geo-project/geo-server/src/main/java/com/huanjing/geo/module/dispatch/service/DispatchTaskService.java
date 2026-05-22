package com.huanjing.geo.module.dispatch.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchTaskService {

    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchQueueService dispatchQueueService;
    private final DispatchExecutionService dispatchExecutionService;
    private final DispatchProperties dispatchProperties;
    private final DispatchTaskStateService dispatchTaskStateService;
    private final CurrentUserService currentUserService;
    private final ActivityLogService activityLogService;
    private final DispatchPollShardPersistenceService pollShardPersistenceService;
    private final DispatchPollAggregationService pollAggregationService;

    @Transactional
    public DispatchTask createTaskAndEnqueue(Long projectId,
                                             DispatchTaskType taskType,
                                             LocalDate windowStart,
                                             LocalDate windowEnd,
                                             LocalDateTime dueTime,
                                             Map<String, Object> payload) {
        String idempotencyKey = defaultIdempotencyKey(taskType, payload);
        return createTaskAndEnqueue(projectId, taskType, windowStart, windowEnd, dueTime, payload, idempotencyKey, null, null);
    }

    @Transactional
    public DispatchTask createTaskAndEnqueue(Long projectId,
                                             DispatchTaskType taskType,
                                             LocalDate windowStart,
                                             LocalDate windowEnd,
                                             LocalDateTime dueTime,
                                             Map<String, Object> payload,
                                             String idempotencyKey,
                                             String targetChannel,
                                             Integer generationSlotNo) {
        return createTask(projectId, taskType, windowStart, windowEnd, dueTime, payload, idempotencyKey, targetChannel, generationSlotNo, true);
    }

    @Transactional
    public DispatchTask createTaskWithoutEnqueue(Long projectId,
                                                 DispatchTaskType taskType,
                                                 LocalDate windowStart,
                                                 LocalDate windowEnd,
                                                 LocalDateTime dueTime,
                                                 Map<String, Object> payload,
                                                 String idempotencyKey,
                                                 String targetChannel,
                                                 Integer generationSlotNo) {
        return createTask(projectId, taskType, windowStart, windowEnd, dueTime, payload, idempotencyKey, targetChannel, generationSlotNo, false);
    }

    private DispatchTask createTask(Long projectId,
                                    DispatchTaskType taskType,
                                    LocalDate windowStart,
                                    LocalDate windowEnd,
                                    LocalDateTime dueTime,
                                    Map<String, Object> payload,
                                    String idempotencyKey,
                                    String targetChannel,
                                    Integer generationSlotNo,
                                    boolean enqueue) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(400, "dispatch task idempotency_key is required");
        }
        DispatchTask task = new DispatchTask();
        task.setTaskNo("TSK" + System.currentTimeMillis() + RandomUtil.randomNumbers(5));
        task.setProjectId(projectId);
        task.setTaskType(taskType.name());
        task.setIdempotencyKey(idempotencyKey.trim());
        task.setTargetChannel(targetChannel);
        task.setGenerationSlotNo(generationSlotNo);
        task.setPriorityLevel(taskType.getPriorityLevel());
        task.setStatus(DispatchTaskStatus.PENDING.value());
        task.setWindowStart(windowStart);
        task.setWindowEnd(windowEnd);
        task.setDueTime(dueTime);
        task.setPayloadJson(payload == null ? null : JSONUtil.toJsonStr(payload));
        task.setRetryCount(0);
        task.setResourceWaitCount(0);
        task.setMaxRetry(3);
        task.setTimeoutAt(dueTime.plusMinutes(dispatchProperties.getTaskTimeoutMinutes()));

        try {
            dispatchTaskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            DispatchTask existing = dispatchTaskMapper.selectOne(
                    new LambdaQueryWrapper<DispatchTask>()
                            .eq(DispatchTask::getProjectId, projectId)
                            .eq(DispatchTask::getTaskType, taskType.name())
                            .eq(DispatchTask::getIdempotencyKey, idempotencyKey.trim())
                            .eq(DispatchTask::getWindowStart, windowStart)
                            .eq(DispatchTask::getWindowEnd, windowEnd)
                            .last("LIMIT 1")
            );
            if (existing != null) {
                if (taskType == DispatchTaskType.BRAND_STATEMENT_GENERATION
                        && List.of(
                        DispatchTaskStatus.COMPLETED.value(),
                        DispatchTaskStatus.FAILED.value(),
                        DispatchTaskStatus.DEAD_LETTER.value()
                ).contains(existing.getStatus())) {
                    existing.setStatus(DispatchTaskStatus.PENDING.value());
                    existing.setDueTime(dueTime);
                    existing.setPayloadJson(task.getPayloadJson());
                    existing.setRetryCount(0);
                    existing.setResourceWaitCount(0);
                    existing.setMaxRetry(3);
                    existing.setFirstStartedAt(null);
                    existing.setLastStartedAt(null);
                    existing.setNextRetryAt(null);
                    existing.setFinishedAt(null);
                    existing.setLastError(null);
                    existing.setErrorContext(null);
                    existing.setTimeoutAt(dueTime.plusMinutes(dispatchProperties.getTaskTimeoutMinutes()));
                    dispatchTaskMapper.updateById(existing);
                    if (enqueue) {
                        safeEnqueue(existing);
                    }
                    return existing;
                }
                if (enqueue && !DispatchTaskStatus.CANCELLED.value().equals(existing.getStatus())) {
                    safeEnqueue(existing);
                }
                return existing;
            }
            throw ex;
        }

        if (enqueue) {
            safeEnqueue(task);
        }
        return task;
    }

    public void enqueueIfNeeded(DispatchTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        DispatchTask current = dispatchTaskMapper.selectById(task.getId());
        if (current != null) {
            task = current;
        }
        if (!DispatchTaskType.fromValue(task.getTaskType()).isQueueTask()) {
            return;
        }
        if (!DispatchTaskStatus.PENDING.value().equals(task.getStatus())
                && !DispatchTaskStatus.RETRY_PENDING.value().equals(task.getStatus())) {
            return;
        }
        dispatchQueueService.enqueueTask(task.getId(), task.getPriorityLevel(), task.getCreatedAt() == null ? System.currentTimeMillis() : task.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private void safeEnqueue(DispatchTask task) {
        try {
            enqueueIfNeeded(task);
        } catch (Exception ex) {
            // Keep task in MySQL pending state and rely on recovery scheduler when Redis becomes healthy.
            log.warn("Dispatch task enqueue failed, taskId={}, taskType={}, reason={}",
                    task.getId(), task.getTaskType(), ex.getMessage());
        }
    }

    public void processTask(Long taskId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }
        if (DispatchTaskStatus.COMPLETED.value().equals(task.getStatus())
                || DispatchTaskStatus.DEAD_LETTER.value().equals(task.getStatus())
                || DispatchTaskStatus.CANCELLED.value().equals(task.getStatus())) {
            dispatchQueueService.clearQueueMark(taskId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (task.getTimeoutAt() != null && now.isAfter(task.getTimeoutAt())) {
            markDeadLetter(task, "task execution timeout");
            return;
        }

        task = dispatchTaskStateService.markRunning(taskId, dispatchProperties.getTaskTimeoutMinutes());
        if (task == null) {
            return;
        }

        try {
            dispatchExecutionService.execute(task);
            dispatchTaskStateService.markCompleted(taskId);
        } catch (Exception ex) {
            handleFailure(task, ex);
        }
    }

    public List<DispatchTask> listReplayableTasks(Long projectId, int limit) {
        return dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .in(DispatchTask::getStatus, List.of(DispatchTaskStatus.FAILED.value(), DispatchTaskStatus.DEAD_LETTER.value()))
                        .orderByDesc(DispatchTask::getUpdatedAt)
                        .last("LIMIT " + Math.max(limit, 1))
        );
    }

    public DispatchTask requireTask(Long taskId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "Task not found");
        }
        return task;
    }

    @Transactional
    public void replayTask(Long taskId) {
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "Task not found");
        }
        if (!List.of(DispatchTaskStatus.FAILED.value(), DispatchTaskStatus.DEAD_LETTER.value()).contains(task.getStatus())) {
            throw new BizException(400, "Only failed/dead_letter task can replay");
        }

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(DispatchTaskStatus.PENDING.value());
        task.setRetryCount(0);
        task.setResourceWaitCount(0);
        task.setFirstStartedAt(null);
        task.setLastStartedAt(null);
        task.setTimeoutAt(now.plusMinutes(dispatchProperties.getTaskTimeoutMinutes()));
        task.setNextRetryAt(null);
        task.setFinishedAt(null);
        task.setLastError(null);
        if (DispatchTaskType.BI_DAILY_POLL.name().equalsIgnoreCase(task.getTaskType())
                || DispatchTaskType.CONTENT_GENERATION.name().equalsIgnoreCase(task.getTaskType())) {
            Map<String, Object> payload = new HashMap<>();
            if (task.getPayloadJson() != null && !task.getPayloadJson().isBlank()) {
                JSONUtil.parseObj(task.getPayloadJson()).forEach((k, v) -> payload.put(String.valueOf(k), v));
            }
            int oldBatchNo = 1;
            Object rawBatchNo = payload.get("batchNo");
            if (rawBatchNo != null) {
                try {
                    oldBatchNo = Integer.parseInt(String.valueOf(rawBatchNo));
                } catch (NumberFormatException ignore) {
                    oldBatchNo = 1;
                }
            }
            payload.put("batchNo", Math.max(oldBatchNo, 1) + 1);
            task.setPayloadJson(JSONUtil.toJsonStr(payload));
        }
        dispatchTaskMapper.updateById(task);
        enqueueIfNeeded(task);
    }

    public void enqueueRecoveryTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<DispatchTask> tasks = dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .and(w -> w
                                .and(t -> t.eq(DispatchTask::getStatus, DispatchTaskStatus.PENDING.value())
                                        .le(DispatchTask::getDueTime, now))
                                .or()
                                .and(t -> t.eq(DispatchTask::getStatus, DispatchTaskStatus.RETRY_PENDING.value())
                                        .le(DispatchTask::getNextRetryAt, now))
                        )
                        .orderByAsc(DispatchTask::getPriorityLevel, DispatchTask::getCreatedAt)
                        .last("LIMIT " + Math.max(dispatchProperties.getRecoverBatchSize(), 1))
        );

        for (DispatchTask task : tasks) {
            if (!dispatchQueueService.existsInQueue(task.getId())) {
                enqueueIfNeeded(task);
            }
        }
    }

    @Transactional
    public void releaseTask(Long taskId, String reason) {
        currentUserService.ensurePermission("dispatch.task.release");
        SysUser operator = currentUserService.requireCurrentUser();
        DispatchTask task = dispatchTaskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw new BizException(404, "Task not found");
        }
        if (!DispatchTaskType.CONTENT_GENERATION.name().equalsIgnoreCase(task.getTaskType())) {
            throw new BizException(400, "Only CONTENT_GENERATION task can be released");
        }
        if (DispatchTaskStatus.CANCELLED.value().equals(task.getStatus())) {
            throw new BizException(400, "Task already cancelled");
        }
        if (DispatchTaskStatus.COMPLETED.value().equals(task.getStatus())) {
            throw new BizException(400, "Completed task cannot be released");
        }

        String oldStatus = task.getStatus();
        String oldKey = task.getIdempotencyKey();
        String normalizedKey = oldKey == null || oldKey.isBlank() ? "_legacy" : oldKey.trim();
        String normalizedReason = trimError(reason == null || reason.isBlank() ? "manual release" : reason);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(DispatchTaskStatus.CANCELLED.value());
        task.setFinishedAt(now);
        task.setNextRetryAt(null);
        task.setTimeoutAt(null);
        task.setLastError(normalizedReason);
        task.setErrorContext(JSONUtil.toJsonStr(buildErrorContext(
                normalizedReason,
                null,
                task.getCurrentChannel(),
                task.getPlatformCode(),
                now
        )));
        task.setIdempotencyKey("cancelled:" + normalizedKey + ":" + task.getId());
        dispatchTaskMapper.updateById(task);
        dispatchQueueService.clearQueueMark(task.getId());

        activityLogService.logAction(
                operator.getId(),
                "dispatch.task.release",
                "dispatch_task",
                task.getId(),
                Map.of("status", oldStatus, "idempotencyKey", normalizedKey),
                Map.of("status", task.getStatus(), "idempotencyKey", task.getIdempotencyKey()),
                Map.of(
                        "projectId", task.getProjectId(),
                        "targetChannel", task.getTargetChannel(),
                        "generationSlotNo", task.getGenerationSlotNo(),
                        "reason", normalizedReason
                )
        );
    }

    public void cleanupHistory() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(dispatchProperties.getTaskRetentionDays());
        dispatchTaskMapper.delete(
                new LambdaQueryWrapper<DispatchTask>()
                        .in(DispatchTask::getStatus, List.of(
                                DispatchTaskStatus.COMPLETED.value(),
                                DispatchTaskStatus.FAILED.value(),
                                DispatchTaskStatus.DEAD_LETTER.value(),
                                DispatchTaskStatus.CANCELLED.value()))
                        .le(DispatchTask::getUpdatedAt, deadline)
        );
    }

    public void reclaimTimedOutRunningTasks() {
        dispatchTaskStateService.reclaimTimedOutRunningTasks(dispatchProperties.getRecoverBatchSize());
    }

    private void handleFailure(DispatchTask task, Exception ex) {
        LocalDateTime now = LocalDateTime.now();
        if (ex instanceof DispatchResourceBusyException) {
            String lastError = trimError(ex.getMessage());
            String errorContext = JSONUtil.toJsonStr(buildErrorContext(
                    lastError,
                    ex.getClass().getName(),
                    task.getCurrentChannel(),
                    task.getPlatformCode(),
                    now
            ));
            int nextResourceWaitCount = (task.getResourceWaitCount() == null ? 0 : task.getResourceWaitCount()) + 1;
            if (nextResourceWaitCount > dispatchProperties.getResourceBusyMaxAttempts()) {
                markDeadLetter(task, "resource unavailable after " + nextResourceWaitCount + " wait attempts: " + lastError);
                return;
            }
            int retryDelaySeconds = dispatchProperties.getResourceBusyRetryMinSeconds();
            int retryJitterSeconds = dispatchProperties.getResourceBusyRetryJitterSeconds();
            if (retryJitterSeconds > 0) {
                retryDelaySeconds += ThreadLocalRandom.current().nextInt(retryJitterSeconds + 1);
            }
            dispatchTaskStateService.markResourceWaiting(
                    task.getId(),
                    nextResourceWaitCount,
                    now.plusSeconds(retryDelaySeconds),
                    lastError,
                    errorContext,
                    task.getPayloadJson(),
                    task.getProjectId()
            );
            log.info("Task {} postponed for dispatch resource: {}", task.getId(), lastError);
            return;
        }
        int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setRetryCount(nextRetryCount);
        task.setLastError(trimError(ex.getMessage()));
        task.setErrorContext(JSONUtil.toJsonStr(buildErrorContext(
                trimError(ex.getMessage()),
                ex.getClass().getName(),
                task.getCurrentChannel(),
                task.getPlatformCode(),
                now
        )));

        if (task.getTimeoutAt() != null && now.isAfter(task.getTimeoutAt())) {
            markDeadLetter(task, "task execution timeout");
            return;
        }

        if (nextRetryCount > (task.getMaxRetry() == null ? 3 : task.getMaxRetry())) {
            markDeadLetter(task, ex.getMessage());
            return;
        }

        dispatchTaskStateService.markRetryPending(
                task.getId(),
                nextRetryCount,
                now.plus(resolveRetryDelay(nextRetryCount)),
                task.getLastError(),
                task.getErrorContext(),
                task.getPayloadJson(),
                task.getProjectId(),
                ex
        );
        log.warn("Task {} failed, retry count={}", task.getId(), nextRetryCount, ex);
    }

    private void markDeadLetter(DispatchTask task, String reason) {
        String normalizedReason = trimError(reason);
        String errorContext = task.getErrorContext();
        if (errorContext == null) {
            errorContext = JSONUtil.toJsonStr(buildErrorContext(
                    normalizedReason,
                    null,
                    task.getCurrentChannel(),
                    task.getPlatformCode(),
                    LocalDateTime.now()
            ));
        }
        dispatchTaskStateService.markDeadLetter(
                task.getId(),
                normalizedReason,
                errorContext,
                task.getRetryCount(),
                task.getPayloadJson(),
                task.getProjectId()
        );
        Long shardId = resolveShardId(task);
        if (shardId != null) {
            Long batchId = pollShardPersistenceService.markShardFailed(shardId, normalizedReason);
            pollAggregationService.tryAggregateBatch(batchId);
        }
    }

    private Duration resolveRetryDelay(int retryCount) {
        String[] values = dispatchProperties.getRetryDelays().split(",");
        if (values.length == 0) {
            return Duration.ofMinutes(1);
        }
        int idx = Math.min(Math.max(retryCount - 1, 0), values.length - 1);
        String value = values[idx].trim().toLowerCase();
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
        }
        return Duration.ofMinutes(Long.parseLong(value));
    }

    private String trimError(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 900 ? message : message.substring(0, 900);
    }

    private String defaultIdempotencyKey(DispatchTaskType taskType, Map<String, Object> payload) {
        if (taskType == DispatchTaskType.BRAND_STATEMENT_GENERATION && payload != null && payload.get("brandId") != null) {
            return "brand:" + payload.get("brandId");
        }
        if (payload != null && payload.get("mode") != null) {
            return String.valueOf(payload.get("mode"));
        }
        return taskType.name();
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
