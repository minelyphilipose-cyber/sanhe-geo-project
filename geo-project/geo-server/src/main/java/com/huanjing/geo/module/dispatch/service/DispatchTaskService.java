package com.huanjing.geo.module.dispatch.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailure;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailureClassifier;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import jakarta.annotation.PostConstruct;
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
import java.util.Locale;
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
    private final LlmCapacityFailureClassifier capacityFailureClassifier;
    private final DispatchAlertService dispatchAlertService;

    @PostConstruct
    public void validateStaggerConfiguration() {
        DispatchProperties.Stagger stagger = dispatchProperties.getStagger();
        if (stagger == null || !stagger.isEnabled()) {
            return;
        }
        int retrySpreadSeconds = dispatchProperties.getResourceBusyRetryMinSeconds()
                + dispatchProperties.getResourceBusyRetryJitterSeconds();
        if (stagger.getJitterSeconds() < retrySpreadSeconds) {
            log.warn("Dispatch stagger jitter is smaller than resource-busy retry spread, staggerJitterSeconds={}, retrySpreadSeconds={}",
                    stagger.getJitterSeconds(), retrySpreadSeconds);
        }
    }

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
        task.setTimeoutAt(null);

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
                    existing.setTimeoutAt(null);
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
        enqueueIfNeeded(task, defaultAvailableAtMillis(task));
    }

    public void enqueueIfNeeded(Long taskId) {
        if (taskId == null) {
            return;
        }
        DispatchTask task = dispatchTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        enqueueIfNeeded(task);
    }

    public void enqueueQuestionPollShardTasksWithStagger(List<DispatchTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        if (!isStaggerEnabledFor(DispatchTaskType.QUESTION_POLL)) {
            tasks.forEach(this::enqueueIfNeeded);
            return;
        }
        long baseMillis = System.currentTimeMillis();
        Map<String, List<DispatchTask>> byPlatform = new java.util.LinkedHashMap<>();
        for (DispatchTask task : tasks) {
            if (task == null) {
                continue;
            }
            String platformCode = task.getPlatformCode();
            String platformKey = platformCode == null || platformCode.isBlank() ? "_unknown" : platformCode.trim();
            byPlatform.computeIfAbsent(platformKey, ignored -> new java.util.ArrayList<>()).add(task);
        }
        byPlatform.forEach((platformCode, platformTasks) -> enqueueStaggeredPlatformTasks(platformCode, platformTasks, baseMillis));
    }

    public void updateTaskPlatform(DispatchTask update) {
        if (update == null || update.getId() == null) {
            return;
        }
        DispatchTask patch = new DispatchTask();
        patch.setId(update.getId());
        patch.setPlatformCode(update.getPlatformCode());
        dispatchTaskMapper.updateById(patch);
    }

    public void enqueueIfNeeded(DispatchTask task, long availableAtMillis) {
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
        if (!guardQueueDepth(task)) {
            return;
        }
        dispatchQueueService.enqueueTask(
                task.getId(),
                task.getPriorityLevel(),
                defaultAvailableAtMillis(task),
                Math.max(0L, availableAtMillis)
        );
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

    private void enqueueStaggeredPlatformTasks(String platformCode, List<DispatchTask> tasks, long baseMillis) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        tasks.sort(java.util.Comparator.comparing(DispatchTask::getId, java.util.Comparator.nullsLast(Long::compareTo)));
        DispatchProperties.Stagger stagger = dispatchProperties.getStagger();
        StaggerTiming timing = resolveStaggerTiming(stagger, platformCode);
        long windowMs = Duration.ofMinutes(timing.windowMinutes()).toMillis();
        long maxDelayMs = Duration.ofMinutes(timing.maxDelayMinutes()).toMillis();
        long jitterMs = Duration.ofSeconds(timing.jitterSeconds()).toMillis();
        long capJitterMs = Duration.ofSeconds(timing.capJitterSeconds()).toMillis();
        boolean overflow = false;
        int count = tasks.size();
        for (int i = 0; i < count; i++) {
            long offsetMs = count <= 1 ? 0L : (windowMs * i) / count;
            if (jitterMs > 0L) {
                offsetMs += ThreadLocalRandom.current().nextLong(jitterMs + 1L);
            }
            long availableAt;
            if (offsetMs > maxDelayMs) {
                overflow = true;
                long tailJitter = capJitterMs <= 0L ? 0L : ThreadLocalRandom.current().nextLong(Math.min(capJitterMs, maxDelayMs) + 1L);
                availableAt = baseMillis + Math.max(0L, maxDelayMs - tailJitter);
            } else {
                availableAt = baseMillis + offsetMs;
            }
            enqueueIfNeeded(tasks.get(i), availableAt);
        }
        if (overflow) {
            alertStaggerOverflow(platformCode, count, windowMs, maxDelayMs);
        }
    }

    private StaggerTiming resolveStaggerTiming(DispatchProperties.Stagger stagger, String platformCode) {
        DispatchProperties.PlatformOverride override = null;
        if (stagger.getPlatforms() != null && platformCode != null && !platformCode.isBlank()) {
            override = stagger.getPlatforms().get(platformCode.trim().toLowerCase(Locale.ROOT));
        }
        return new StaggerTiming(
                override == null || override.getWindowMinutes() == null ? stagger.getWindowMinutes() : override.getWindowMinutes(),
                override == null || override.getMaxDelayMinutes() == null ? stagger.getMaxDelayMinutes() : override.getMaxDelayMinutes(),
                override == null || override.getJitterSeconds() == null ? stagger.getJitterSeconds() : override.getJitterSeconds(),
                override == null || override.getCapJitterSeconds() == null ? stagger.getCapJitterSeconds() : override.getCapJitterSeconds()
        );
    }

    private boolean guardQueueDepth(DispatchTask task) {
        DispatchProperties.Stagger stagger = dispatchProperties.getStagger();
        long maxQueueSize = stagger == null ? 0L : stagger.getMaxQueueSize();
        if (maxQueueSize <= 0L) {
            return true;
        }
        long queued = dispatchQueueService.queuedTaskCount();
        if (queued < maxQueueSize) {
            return true;
        }
        Map<String, Object> context = new HashMap<>();
        context.put("taskType", task.getTaskType());
        context.put("taskId", task.getId());
        context.put("queuedTaskCount", queued);
        context.put("maxQueueSize", maxQueueSize);
        dispatchAlertService.createOrRefreshAlert(
                task.getId(),
                task.getProjectId(),
                "DISPATCH_QUEUE_DEPTH_LIMIT:" + task.getTaskType(),
                DispatchAlertSeverity.WARN,
                "Dispatch queue depth limit reached",
                "Dispatch enqueue detected queue depth at or above configured limit",
                task.getRetryCount(),
                JSONUtil.toJsonStr(context)
        );
        return !"REJECT".equalsIgnoreCase(stagger.getOverflowPolicy());
    }

    private record StaggerTiming(int windowMinutes, int maxDelayMinutes, int jitterSeconds, int capJitterSeconds) {
    }

    private void alertStaggerOverflow(String platformCode, int taskCount, long windowMs, long maxDelayMs) {
        Map<String, Object> context = new HashMap<>();
        context.put("taskType", DispatchTaskType.QUESTION_POLL.name());
        context.put("platformCode", platformCode);
        context.put("taskCount", taskCount);
        context.put("windowMinutes", Duration.ofMillis(windowMs).toMinutes());
        context.put("maxDelayMinutes", Duration.ofMillis(maxDelayMs).toMinutes());
        context.put("overflowPolicy", "CAP_AND_ALERT");
        dispatchAlertService.createOrRefreshAlert(
                null,
                null,
                "DISPATCH_STAGGER_OVERFLOW:" + platformCode,
                DispatchAlertSeverity.WARN,
                "Dispatch stagger overflow",
                "Dispatch stagger could not spread all tasks within the configured max delay; capped tasks were placed in the tail jitter band",
                0,
                JSONUtil.toJsonStr(context)
        );
    }

    private boolean isStaggerEnabledFor(DispatchTaskType taskType) {
        DispatchProperties.Stagger stagger = dispatchProperties.getStagger();
        if (stagger == null || !stagger.isEnabled() || taskType == null) {
            return false;
        }
        String raw = stagger.getTaskTypes();
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String item : raw.split(",")) {
            DispatchTaskType configuredType;
            try {
                configuredType = DispatchTaskType.fromValue(item.trim());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            if (taskType == configuredType) {
                return true;
            }
        }
        return false;
    }

    private long defaultAvailableAtMillis(DispatchTask task) {
        return task.getCreatedAt() == null
                ? System.currentTimeMillis()
                : task.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
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
        return listReplayableTasks(projectId, limit, null);
    }

    public List<DispatchTask> listReplayableTasks(Long projectId, int limit, String projectScopeSql) {
        return dispatchTaskMapper.selectList(
                new LambdaQueryWrapper<DispatchTask>()
                        .eq(projectId != null, DispatchTask::getProjectId, projectId)
                        .inSql(projectScopeSql != null, DispatchTask::getProjectId, projectScopeSql)
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
        task.setTimeoutAt(null);
        task.setNextRetryAt(null);
        task.setFinishedAt(null);
        task.setLastError(null);
        if (DispatchTaskType.isQuestionPoll(task.getTaskType())
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

    public boolean isHistoryCleanupEnabled() {
        return dispatchProperties.isTaskCleanupEnabled();
    }

    public void reclaimTimedOutRunningTasks() {
        dispatchTaskStateService.reclaimTimedOutRunningTasks(dispatchProperties.getRecoverBatchSize());
    }

    private void handleFailure(DispatchTask task, Exception ex) {
        LocalDateTime now = LocalDateTime.now();
        if (ex instanceof DispatchResourceBusyException resourceBusyException) {
            String lastError = trimError(ex.getMessage());
            LlmCapacityFailure capacityFailure = resolveCapacityFailure(resourceBusyException);
            int nextResourceWaitCount = (task.getResourceWaitCount() == null ? 0 : task.getResourceWaitCount()) + 1;
            if (nextResourceWaitCount > dispatchProperties.getResourceBusyMaxAttempts()) {
                String reason = dispatchProperties.isResourceBusyRetryAfterEnabled()
                        ? "CAPACITY_RETRY_EXHAUSTED: resource unavailable after " + nextResourceWaitCount
                        + " wait attempts: " + lastError
                        : "resource unavailable after " + nextResourceWaitCount + " wait attempts: " + lastError;
                markDeadLetter(task, reason);
                return;
            }
            int retryDelaySeconds = resolveResourceBusyRetryDelaySeconds(nextResourceWaitCount, capacityFailure);
            Map<String, Object> errorContext = buildErrorContext(
                    lastError,
                    ex.getClass().getName(),
                    task.getCurrentChannel(),
                    task.getPlatformCode(),
                    now
            );
            if (dispatchProperties.isCapacityFailureClassificationEnabled()
                    || dispatchProperties.isResourceBusyRetryAfterEnabled()) {
                enrichCapacityErrorContext(errorContext, capacityFailure, retryDelaySeconds);
            }
            dispatchTaskStateService.markResourceWaiting(
                    task.getId(),
                    nextResourceWaitCount,
                    now.plusSeconds(retryDelaySeconds),
                    lastError,
                    JSONUtil.toJsonStr(errorContext),
                    task.getPayloadJson(),
                    task.getProjectId()
            );
            log.info("Task {} postponed for dispatch resource, delaySeconds={}, category={}, retryAfterMs={}",
                    task.getId(),
                    retryDelaySeconds,
                    capacityFailure == null ? null : capacityFailure.errorCategory(),
                    capacityFailure == null ? null : capacityFailure.retryAfterMs());
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

    private LlmCapacityFailure resolveCapacityFailure(DispatchResourceBusyException ex) {
        if (ex.getCapacityFailure() != null) {
            return ex.getCapacityFailure();
        }
        if (!dispatchProperties.isCapacityFailureClassificationEnabled()) {
            return null;
        }
        return capacityFailureClassifier.classify(ex).orElse(null);
    }

    private int resolveResourceBusyRetryDelaySeconds(int resourceWaitCount, LlmCapacityFailure capacityFailure) {
        if (!dispatchProperties.isResourceBusyRetryAfterEnabled()) {
            int retryDelaySeconds = dispatchProperties.getResourceBusyRetryMinSeconds();
            int retryJitterSeconds = dispatchProperties.getResourceBusyRetryJitterSeconds();
            if (retryJitterSeconds > 0) {
                retryDelaySeconds += ThreadLocalRandom.current().nextInt(retryJitterSeconds + 1);
            }
            return retryDelaySeconds;
        }
        int maxSeconds = dispatchProperties.getResourceBusyRetryMaxSeconds();
        if (capacityFailure != null && capacityFailure.hasRetryAfter()) {
            long retryAfterSeconds = (capacityFailure.retryAfterMs() + 999L) / 1000L;
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, retryAfterSeconds));
        }
        long baseSeconds = Math.max(1L, dispatchProperties.getResourceBusyRetryMinSeconds());
        int exponent = Math.min(Math.max(resourceWaitCount - 1, 0), 20);
        long delaySeconds = baseSeconds * (1L << exponent);
        int retryJitterSeconds = dispatchProperties.getResourceBusyRetryJitterSeconds();
        if (retryJitterSeconds > 0) {
            delaySeconds += ThreadLocalRandom.current().nextInt(retryJitterSeconds + 1);
        }
        return (int) Math.min(maxSeconds, Math.max(1L, delaySeconds));
    }

    private void enrichCapacityErrorContext(Map<String, Object> context,
                                            LlmCapacityFailure capacityFailure,
                                            int retryDelaySeconds) {
        context.put("capacityRetryDelaySeconds", retryDelaySeconds);
        context.put("capacityRetryAfterEnabled", dispatchProperties.isResourceBusyRetryAfterEnabled());
        if (capacityFailure == null) {
            return;
        }
        context.put("capacityErrorCategory", capacityFailure.errorCategory() == null ? null : capacityFailure.errorCategory().name());
        context.put("capacityRetryAfterMs", capacityFailure.retryAfterMs());
        context.put("capacityReason", capacityFailure.reason());
        context.put("capacitySource", capacityFailure.source());
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
