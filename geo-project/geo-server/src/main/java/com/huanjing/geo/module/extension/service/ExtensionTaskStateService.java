package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.dto.ExtensionTaskStateResponse;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_NOT_FOUND;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_RATE_LIMITED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT;

@Service
@RequiredArgsConstructor
public class ExtensionTaskStateService {

    private static final String DISPATCH_MODE_SEMI_AUTO = "SEMI_AUTO";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_TOKEN_ISSUED = "token_issued";
    private static final String STATUS_FILLING = "filling";
    private static final String STATUS_FILLED = "filled";
    private static final String STATUS_PUBLISHED = "published";
    private static final Duration HEARTBEAT_RATE_LIMIT_TTL = Duration.ofSeconds(30);
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    private static final int RECLAIM_BATCH_LIMIT = 100;

    private final DistributionTaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final BrandAccessService brandAccessService;
    private final ExtensionRedisStore redisStore;
    private final ExtensionAuditSupport auditSupport;
    private final AuditService auditService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public void markFillingFromFillTokenConsume(Long taskId, Long operatorId, Long extensionSessionId) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_FILL_STARTED");
        int affected = taskMapper.markSemiAutoFilling(taskId, now());
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_FILL_STARTED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
    }

    @Transactional
    public ExtensionTaskStateResponse ackFilled(Long taskId, Long operatorId, Long extensionSessionId) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_FILLED");
        LocalDateTime now = now();
        int affected = taskMapper.markSemiAutoFilled(taskId, now);
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_FILLED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        auditSuccess("SEMI_AUTO_TASK_FILLED", context, operatorId, extensionSessionId, detail("filledAt", now));
        return new ExtensionTaskStateResponse(taskId, STATUS_FILLED);
    }

    @Transactional
    public ExtensionTaskStateResponse heartbeat(Long taskId, Long operatorId, Long extensionSessionId) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_HEARTBEAT_DENIED");
        enforceHeartbeatRateLimit(taskId);
        LocalDateTime now = now();
        int affected = taskMapper.touchSemiAutoHeartbeat(taskId, now);
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_HEARTBEAT_DENIED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        return new ExtensionTaskStateResponse(taskId, STATUS_FILLING);
    }

    @Transactional
    public ExtensionTaskStateResponse published(Long taskId, Long operatorId, Long extensionSessionId) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_PUBLISHED");
        LocalDateTime now = now();
        int affected = taskMapper.markSemiAutoPublished(taskId, now, operatorId);
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_PUBLISHED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        auditSuccess("SEMI_AUTO_TASK_PUBLISHED", context, operatorId, extensionSessionId, detail("publishedAt", now));
        return new ExtensionTaskStateResponse(taskId, STATUS_PUBLISHED);
    }

    @Transactional
    public int reclaimStaleTasks() {
        LocalDateTime now = now();
        LocalDateTime tokenIssuedBefore = now.minus(STALE_THRESHOLD);
        LocalDateTime heartbeatBefore = now.minus(STALE_THRESHOLD);
        List<DistributionTask> staleTasks = taskMapper.selectStaleSemiAutoTasks(
                tokenIssuedBefore,
                heartbeatBefore,
                RECLAIM_BATCH_LIMIT
        );
        int reclaimed = 0;
        for (DistributionTask task : staleTasks) {
            int affected = taskMapper.reclaimSemiAutoTask(task.getId(), task.getStatus());
            if (affected == 1) {
                reclaimed++;
                auditReclaimed(task, tokenIssuedBefore, heartbeatBefore);
            }
        }
        return reclaimed;
    }

    private TaskContext requireOperableTask(
            Long taskId,
            Long operatorId,
            Long extensionSessionId,
            String eventType
    ) {
        if (taskId == null) {
            auditSupport.record(
                    eventType,
                    AuditResult.NOT_FOUND,
                    AuditMode.SYNC,
                    false,
                    operatorId,
                    null,
                    null,
                    null,
                    extensionSessionId,
                    "DISTRIBUTION_TASK",
                    null,
                    String.valueOf(TASK_NOT_FOUND),
                    "TASK_ID_REQUIRED",
                    null
            );
            throw new BizException(TASK_NOT_FOUND, "task not found");
        }
        DistributionTask task = taskMapper.selectById(taskId);
        if (task == null) {
            auditSupport.record(
                    eventType,
                    AuditResult.NOT_FOUND,
                    AuditMode.SYNC,
                    false,
                    operatorId,
                    null,
                    null,
                    taskId,
                    extensionSessionId,
                    "DISTRIBUTION_TASK",
                    String.valueOf(taskId),
                    String.valueOf(TASK_NOT_FOUND),
                    "TASK_NOT_FOUND",
                    null
            );
            throw new BizException(TASK_NOT_FOUND, "task not found");
        }
        Long brandId = resolveBrandId(task, operatorId, extensionSessionId, eventType);
        TaskContext context = new TaskContext(task, brandId);
        if (!DISPATCH_MODE_SEMI_AUTO.equals(task.getDispatchMode())) {
            auditDenied(eventType, context, operatorId, extensionSessionId, "NOT_SEMI_AUTO_TASK");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        try {
            brandAccessService.requireBrandAccess(brandId, operatorId, BrandAccessAction.OPERATE);
        } catch (BizException ex) {
            auditAccessFailure(eventType, context, extensionSessionId, operatorId, ex);
            throw ex;
        }
        return context;
    }

    private Long resolveBrandId(DistributionTask task, Long operatorId, Long extensionSessionId, String eventType) {
        Project project = task.getProjectId() == null ? null : projectMapper.selectById(task.getProjectId());
        if (project == null || project.getBrandId() == null) {
            auditSupport.record(
                    eventType,
                    AuditResult.NOT_FOUND,
                    AuditMode.SYNC,
                    false,
                    operatorId,
                    null,
                    task.getSelfMediaAccountId(),
                    task.getId(),
                    extensionSessionId,
                    "DISTRIBUTION_TASK",
                    String.valueOf(task.getId()),
                    String.valueOf(TASK_NOT_FOUND),
                    "TASK_BRAND_NOT_FOUND",
                    detail("projectId", task.getProjectId())
            );
            throw new BizException(TASK_NOT_FOUND, "task brand not found");
        }
        return project.getBrandId();
    }

    private void enforceHeartbeatRateLimit(Long taskId) {
        long count = redisStore.incrementWithTtl(
                "extension:task:heartbeat:" + taskId,
                HEARTBEAT_RATE_LIMIT_TTL
        );
        if (count > 1) {
            throw new BizException(TASK_RATE_LIMITED, "heartbeat too frequent");
        }
    }

    private void auditSuccess(
            String eventType,
            TaskContext context,
            Long operatorId,
            Long extensionSessionId,
            Map<String, Object> detail
    ) {
        auditSupport.record(
                eventType,
                AuditResult.SUCCESS,
                AuditMode.SYNC,
                false,
                operatorId,
                context.brandId(),
                context.task().getSelfMediaAccountId(),
                context.task().getId(),
                extensionSessionId,
                "DISTRIBUTION_TASK",
                String.valueOf(context.task().getId()),
                null,
                null,
                detail
        );
    }

    private void auditDenied(
            String eventType,
            TaskContext context,
            Long operatorId,
            Long extensionSessionId,
            String reason
    ) {
        auditSupport.record(
                eventType,
                AuditResult.DENIED,
                AuditMode.SYNC,
                false,
                operatorId,
                context.brandId(),
                context.task().getSelfMediaAccountId(),
                context.task().getId(),
                extensionSessionId,
                "DISTRIBUTION_TASK",
                String.valueOf(context.task().getId()),
                String.valueOf(TASK_STATE_CONFLICT),
                reason,
                detail("status", context.task().getStatus(), "reason", reason)
        );
    }

    private void auditAccessFailure(
            String eventType,
            TaskContext context,
            Long extensionSessionId,
            Long operatorId,
            BizException ex
    ) {
        auditSupport.record(
                eventType,
                auditResultFor(ex),
                AuditMode.SYNC,
                false,
                operatorId,
                context.brandId(),
                context.task().getSelfMediaAccountId(),
                context.task().getId(),
                extensionSessionId,
                "DISTRIBUTION_TASK",
                String.valueOf(context.task().getId()),
                String.valueOf(ex.getCode()),
                ex.getMessage(),
                detail("status", context.task().getStatus())
        );
    }

    private AuditResult auditResultFor(BizException ex) {
        if (ex.getCode() == BrandAccessErrorCodes.BRAND_ACCESS_NOT_FOUND || ex.getCode() == TASK_NOT_FOUND) {
            return AuditResult.NOT_FOUND;
        }
        if (ex.getCode() == BrandAccessErrorCodes.BRAND_ACCESS_DENIED
                || ex.getCode() == BrandAccessErrorCodes.BRAND_ACCESS_UNAUTHORIZED
                || ex.getCode() == TASK_STATE_CONFLICT
                || ex.getCode() == TASK_RATE_LIMITED) {
            return AuditResult.DENIED;
        }
        return AuditResult.FAILURE;
    }

    private void auditReclaimed(
            DistributionTask task,
            LocalDateTime tokenIssuedBefore,
            LocalDateTime heartbeatBefore
    ) {
        AuditEvent event = new AuditEvent();
        event.setEventType("SEMI_AUTO_TASK_RECLAIMED");
        event.setActorType(ActorType.SYSTEM);
        event.setTaskId(task.getId());
        event.setAccountId(task.getSelfMediaAccountId());
        event.setTargetType("DISTRIBUTION_TASK");
        event.setTargetId(String.valueOf(task.getId()));
        event.setResult(AuditResult.SUCCESS);
        event.setMode(AuditMode.SYNC);
        event.setSensitive(false);
        event.setDetail(detail(
                "previousStatus", task.getStatus(),
                "staleThresholdMinutes", STALE_THRESHOLD.toMinutes(),
                "fillTokenIssuedAt", task.getFillTokenIssuedAt(),
                "lastHeartbeatAt", task.getLastHeartbeatAt(),
                "tokenIssuedBefore", tokenIssuedBefore,
                "heartbeatBefore", heartbeatBefore
        ));
        auditService.record(event);
    }

    private Map<String, Object> detail(Object... values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            detail.put(String.valueOf(values[i]), values[i + 1]);
        }
        return detail;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record TaskContext(DistributionTask task, Long brandId) {
    }
}
