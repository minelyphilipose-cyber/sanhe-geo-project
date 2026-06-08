package com.huanjing.geo.module.extension.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.ActorType;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.service.CompanyChannelQuotaService;
import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.extension.dto.ExtensionTaskPublishReportRequest;
import com.huanjing.geo.module.extension.dto.ExtensionTaskStateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_NOT_FOUND;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_RATE_LIMITED;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.TASK_STATE_CONFLICT;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_BINDING_MISMATCH;
import static com.huanjing.geo.module.extension.ExtensionErrorCodes.FILL_TOKEN_OPERATOR_MISMATCH;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionTaskStateService {

    private static final String STATUS_TOKEN_ISSUED = "token_issued";
    private static final String STATUS_FILLED = "filled";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_FAILED = "failed";
    private static final String ARTICLE_STATUS_APPROVED = "approved";
    private static final String ARTICLE_STATUS_DISTRIBUTING = "distributing";
    private static final Duration HEARTBEAT_RATE_LIMIT_TTL = Duration.ofSeconds(30);
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    private static final Duration SCHEDULE_RETRY_BACKOFF = Duration.ofMinutes(3);
    private static final int RECLAIM_BATCH_LIMIT = 100;
    private static final Set<String> SCHEDULE_RETRYABLE_FAILURE_CODES = Set.of(
            "PAGE_LOAD_TIMEOUT",
            "EDITOR_NOT_READY",
            "COVER_UPLOAD_TIMEOUT",
            "SCHEDULE_DIALOG_NOT_READY",
            "PREVIEW_PAGE_NOT_READY",
            "WORKS_LIST_VERIFY_TIMEOUT",
            "LOCAL_HELPER_TEMPORARY_ERROR"
    );

    private final DistributionTaskMapper taskMapper;
    private final ArticleDraftMapper articleDraftMapper;
    private final SemiAutoTaskAccessService semiAutoTaskAccessService;
    private final InternalScopeService internalScopeService;
    private final CompanyChannelQuotaService companyChannelQuotaService;
    private final SelfMediaPublishScheduleService selfMediaPublishScheduleService;
    private final ExtensionRedisStore redisStore;
    private final ExtensionAuditSupport auditSupport;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
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

    public ExtensionTaskStateResponse ackFilled(Long taskId, Long operatorId, Long extensionSessionId) {
        return ackFilled(taskId, operatorId, extensionSessionId, null);
    }

    @Transactional
    public ExtensionTaskStateResponse ackFilled(Long taskId,
                                                Long operatorId,
                                                Long extensionSessionId,
                                                Map<String, Object> request) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_FILLED");
        LocalDateTime now = now();
        int affected = taskMapper.markSemiAutoFilled(taskId, now);
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_FILLED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        auditSuccess("SEMI_AUTO_TASK_FILLED", context, operatorId, extensionSessionId, detail("filledAt", now));
        String diagnosticsJson = diagnosticsJson(request);
        if (hasVerifiedImmediatePublishResult(request)) {
            selfMediaPublishScheduleService.markDistributionTaskPublishedConfirmed(
                    taskId,
                    extractPublishVerificationUrl(request),
                    diagnosticsJson
            );
        } else if (hasVerifiedScheduledPublishResult(request)) {
            selfMediaPublishScheduleService.markDistributionTaskScheduled(taskId, diagnosticsJson);
        } else {
            selfMediaPublishScheduleService.markDistributionTaskFilled(taskId, diagnosticsJson);
        }
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
        if (shouldAuditHeartbeatStarted(context.task())) {
            auditSuccess("SEMI_AUTO_TASK_HEARTBEAT_STARTED", context, operatorId, extensionSessionId,
                    detail("heartbeatStartedAt", now));
        }
        return new ExtensionTaskStateResponse(taskId, context.task().getStatus());
    }

    @Transactional
    public ExtensionTaskStateResponse published(Long taskId, Long operatorId, Long extensionSessionId) {
        return published(taskId, operatorId, extensionSessionId, null);
    }

    @Transactional
    public ExtensionTaskStateResponse published(
            Long taskId,
            Long operatorId,
            Long extensionSessionId,
            ExtensionTaskPublishReportRequest request
    ) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_PUBLISHED");
        LocalDateTime now = now();
        if (STATUS_FILLED.equals(context.task().getStatus()) && !hasPublishFailure(request)) {
            int affected = taskMapper.markSemiAutoPublished(taskId, now, operatorId);
            if (affected != 1) {
                auditDenied("SEMI_AUTO_TASK_PUBLISHED", context, operatorId, extensionSessionId, "STALE_STATE");
                throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
            }
            auditSuccess("SEMI_AUTO_TASK_PUBLISHED", context, operatorId, extensionSessionId, publishDetail(now, request));
            return new ExtensionTaskStateResponse(taskId, STATUS_PUBLISHED);
        }
        auditSuccess("SEMI_AUTO_TASK_PUBLISHED", context, operatorId, extensionSessionId, publishDetail(now, request));
        return new ExtensionTaskStateResponse(taskId, context.task().getStatus());
    }

    @Transactional
    public ExtensionTaskStateResponse abandon(Long taskId, Long operatorId, Long extensionSessionId) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_ABANDONED");
        LocalDateTime now = now();
        int affected = taskMapper.abandonSemiAutoTask(taskId, "用户关闭平台编辑器，已取消本次半自动分发", now);
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_ABANDONED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        restoreArticleApproved(context.task());
        companyChannelQuotaService.refundDistribution(taskId);
        auditSuccess("SEMI_AUTO_TASK_ABANDONED", context, operatorId, extensionSessionId, detail("abandonedAt", now));
        return new ExtensionTaskStateResponse(taskId, STATUS_FAILED);
    }

    @Transactional
    public ExtensionTaskStateResponse fail(Long taskId,
                                           Long operatorId,
                                           Long extensionSessionId,
                                           Map<String, Object> request) {
        TaskContext context = requireOperableTask(taskId, operatorId, extensionSessionId, "SEMI_AUTO_TASK_FILL_FAILED");
        LocalDateTime now = now();
        String errorMessage = extractFailureMessage(request);
        String failureKind = extractFailureCode(request, errorMessage);
        int affected = taskMapper.markSemiAutoFailed(taskId, failureKind, errorMessage, now);
        if (affected != 1) {
            auditDenied("SEMI_AUTO_TASK_FILL_FAILED", context, operatorId, extensionSessionId, "STALE_STATE");
            throw new BizException(TASK_STATE_CONFLICT, "task state conflict");
        }
        restoreArticleApproved(context.task());
        companyChannelQuotaService.refundDistribution(taskId);
        LocalDateTime nextAttemptAt = isScheduleRetryableFailure(failureKind)
                ? now.plus(SCHEDULE_RETRY_BACKOFF)
                : null;
        selfMediaPublishScheduleService.markDistributionTaskScheduleFailed(
                taskId,
                failureKind,
                errorMessage,
                diagnosticsJson(request),
                nextAttemptAt
        );
        auditSuccess("SEMI_AUTO_TASK_FILL_FAILED", context, operatorId, extensionSessionId,
                detail("failedAt", now, "failureKind", failureKind, "errorMessage", errorMessage));
        return new ExtensionTaskStateResponse(taskId, STATUS_FAILED);
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
            Long reassignedOperatorId = resolveReclaimOperatorId(task);
            int affected = taskMapper.reclaimSemiAutoTask(task.getId(), task.getStatus(), reassignedOperatorId, now);
            if (affected == 1) {
                restoreArticleApproved(task);
                reclaimed++;
                auditReclaimed(task, tokenIssuedBefore, heartbeatBefore, reassignedOperatorId);
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
        SemiAutoTaskAccessService.SemiAutoTaskContext accessContext;
        try {
            accessContext = semiAutoTaskAccessService.requireOperableTask(taskId, operatorId);
        } catch (BizException ex) {
            auditSupport.record(
                    eventType,
                    auditResultFor(ex),
                    AuditMode.SYNC,
                    false,
                    operatorId,
                    null,
                    null,
                    taskId,
                    extensionSessionId,
                    "DISTRIBUTION_TASK",
                    String.valueOf(taskId),
                    String.valueOf(ex.getCode()),
                    ex.getMessage(),
                    null
            );
            throw ex;
        }
        return new TaskContext(accessContext.task(), accessContext.brandId());
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

    private AuditResult auditResultFor(BizException ex) {
        if (ex.getCode() == TASK_NOT_FOUND) {
            return AuditResult.NOT_FOUND;
        }
        if (ex.getCode() == TASK_STATE_CONFLICT
                || ex.getCode() == FILL_TOKEN_OPERATOR_MISMATCH
                || ex.getCode() == FILL_TOKEN_BINDING_MISMATCH
                || ex.getCode() == TASK_RATE_LIMITED) {
            return AuditResult.DENIED;
        }
        return AuditResult.FAILURE;
    }

    private boolean shouldAuditHeartbeatStarted(DistributionTask task) {
        return task.getLastHeartbeatAt() == null;
    }

    private void restoreArticleApproved(DistributionTask task) {
        if (task.getArticleId() == null) {
            throw new BizException(TASK_NOT_FOUND, "task article not found");
        }
        ArticleDraft update = new ArticleDraft();
        update.setStatus(ARTICLE_STATUS_APPROVED);
        articleDraftMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleDraft>()
                        .eq(ArticleDraft::getId, task.getArticleId())
                        .eq(ArticleDraft::getStatus, ARTICLE_STATUS_DISTRIBUTING));
    }

    private void auditReclaimed(
            DistributionTask task,
            LocalDateTime tokenIssuedBefore,
            LocalDateTime heartbeatBefore,
            Long reassignedOperatorId
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
                "heartbeatBefore", heartbeatBefore,
                "newStatus", STATUS_TOKEN_ISSUED,
                "tokenReissued", true,
                "previousOperatorId", task.getOperatorId(),
                "operatorId", reassignedOperatorId,
                "operatorReassigned", task.getOperatorId() != null && !task.getOperatorId().equals(reassignedOperatorId)
        ));
        auditService.record(event);
    }

    private Long resolveReclaimOperatorId(DistributionTask task) {
        try {
            Long currentOwnerId = internalScopeService.resolveProjectOwnerId(task.getProjectId());
            if (currentOwnerId == null) {
                log.warn("stale semi-auto task has NULL project owner, keep original operator. taskId={}, projectId={}",
                        task.getId(), task.getProjectId());
                return task.getOperatorId();
            }
            return currentOwnerId;
        } catch (BizException ex) {
            log.warn("failed to resolve project owner for stale semi-auto task, keep original operator. taskId={}, projectId={}, code={}",
                    task.getId(), task.getProjectId(), ex.getCode());
            return task.getOperatorId();
        }
    }

    private Map<String, Object> detail(Object... values) {
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            detail.put(String.valueOf(values[i]), values[i + 1]);
        }
        return detail;
    }

    private boolean hasVerifiedScheduledPublishResult(Map<String, Object> request) {
        Object fillResult = request == null ? null : request.get("fillResult");
        if (!(fillResult instanceof Map<?, ?> fillResultMap)) {
            return false;
        }
        Object publishOptions = fillResultMap.get("publishOptions");
        if (!(publishOptions instanceof Map<?, ?> options) || !Boolean.TRUE.equals(options.get("scheduled"))) {
            return false;
        }
        Object verification = options.get("publishVerification");
        if (verification instanceof Map<?, ?> verificationMap) {
            return Boolean.TRUE.equals(verificationMap.get("verified"));
        }
        return false;
    }

    private boolean hasVerifiedImmediatePublishResult(Map<String, Object> request) {
        Object fillResult = request == null ? null : request.get("fillResult");
        if (!(fillResult instanceof Map<?, ?> fillResultMap)) {
            return false;
        }
        Object publishOptions = fillResultMap.get("publishOptions");
        if (!(publishOptions instanceof Map<?, ?> options) || !Boolean.TRUE.equals(options.get("published"))) {
            return false;
        }
        Object verification = options.get("publishVerification");
        if (verification instanceof Map<?, ?> verificationMap) {
            return Boolean.TRUE.equals(verificationMap.get("verified"));
        }
        return false;
    }

    private String extractPublishVerificationUrl(Map<String, Object> request) {
        Object fillResult = request == null ? null : request.get("fillResult");
        if (!(fillResult instanceof Map<?, ?> fillResultMap)) {
            return null;
        }
        Object publishOptions = fillResultMap.get("publishOptions");
        if (!(publishOptions instanceof Map<?, ?> options)) {
            return null;
        }
        Object verification = options.get("publishVerification");
        if (!(verification instanceof Map<?, ?> verificationMap)) {
            return null;
        }
        Object pageUrl = verificationMap.get("pageUrl");
        return pageUrl == null ? null : String.valueOf(pageUrl);
    }

    private String extractFailureCode(Map<String, Object> request, String message) {
        Object error = request == null ? null : request.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            Object code = errorMap.get("code");
            if (code != null && StringUtils.hasText(String.valueOf(code))) {
                return String.valueOf(code).trim();
            }
        }
        return classifyFailureKind(message);
    }

    private String extractFailureMessage(Map<String, Object> request) {
        Object error = request == null ? null : request.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            Object message = errorMap.get("message");
            if (message != null && StringUtils.hasText(String.valueOf(message))) {
                return String.valueOf(message).trim();
            }
        }
        Object message = request == null ? null : request.get("message");
        if (message != null && StringUtils.hasText(String.valueOf(message))) {
            return String.valueOf(message).trim();
        }
        return "页面填充失败";
    }

    private String classifyFailureKind(String message) {
        String text = message == null ? "" : message;
        if (text.contains("定时发布时间") || text.contains("定时发布")) {
            return "SCHEDULE_TIME_OR_SELECTOR_FAILED";
        }
        if (text.contains("账号不一致")) {
            return "ACCOUNT_MISMATCH";
        }
        if (text.contains("未登录") || text.contains("需登录")) {
            return "LOGIN_REQUIRED";
        }
        if (text.contains("未找到") || text.contains("超时")) {
            return "EDITOR_NOT_FOUND";
        }
        return "FILL_FAILED";
    }

    private boolean isScheduleRetryableFailure(String code) {
        return SCHEDULE_RETRYABLE_FAILURE_CODES.contains(code);
    }

    private String diagnosticsJson(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private Map<String, Object> publishDetail(
            LocalDateTime publishedAt,
            ExtensionTaskPublishReportRequest request
    ) {
        Map<String, Object> detail = detail("publishedAt", publishedAt);
        if (request == null) {
            return detail;
        }
        detail.put("action", request.action());
        detail.put("platform", request.platform());
        detail.put("href", request.href());
        detail.put("detectedText", request.detectedText());
        detail.put("errorCode", request.errorCode());
        detail.put("errorMessage", request.errorMessage());
        return detail;
    }

    private boolean hasPublishFailure(ExtensionTaskPublishReportRequest request) {
        return request != null && StringUtils.hasText(request.errorCode());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record TaskContext(DistributionTask task, Long brandId) {
    }
}
