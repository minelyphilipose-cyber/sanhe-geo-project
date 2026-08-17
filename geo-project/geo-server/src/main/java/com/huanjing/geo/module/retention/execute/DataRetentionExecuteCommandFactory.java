package com.huanjing.geo.module.retention.execute;

import com.huanjing.geo.module.dispatch.dto.PollRetentionDryRunRequest;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder.CanonicalValue;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.DeferredRetryOperation;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dto.WebsitePublishedCleanupRequest;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DataRetentionExecuteCommandFactory {
    private final DataRetentionCanonicalEncoder encoder;
    private final DataRetentionExecutionBudgetGate budgetGate;
    private final DataRetentionProperties properties;
    private final Clock clock;

    @Autowired
    public DataRetentionExecuteCommandFactory(DataRetentionCanonicalEncoder encoder,
                                              DataRetentionExecutionBudgetGate budgetGate,
                                              DataRetentionProperties properties) {
        this(encoder, budgetGate, properties, Clock.systemUTC());
    }

    DataRetentionExecuteCommandFactory(DataRetentionCanonicalEncoder encoder,
                                       DataRetentionExecutionBudgetGate budgetGate,
                                       DataRetentionProperties properties,
                                       Clock clock) {
        this.encoder = encoder; this.budgetGate = budgetGate; this.properties = properties; this.clock = clock;
    }

    public RetentionExecuteCommand manualWebsite(WebsitePublishedCleanupRequest request, long operator) {
        int retentionHours = request.getRetentionHours() == null ? 24 : request.getRetentionHours();
        int requestLimit = request.getLimit() == null ? 20 : request.getLimit();
        if (request.getCursorArticleId() != null) {
            throw new DataRetentionExecutionException(400, "WEBSITE_SELECTION_INVALID",
                    "cursorArticleId is only supported by website dry-run");
        }
        if (retentionHours < 24 || requestLimit < 1 || requestLimit > 1000) {
            throw new DataRetentionExecutionException(400, "WEBSITE_SELECTION_INVALID",
                    "Website retentionHours or limit is outside the server policy");
        }
        CanonicalValue fingerprint = encoder.websiteFingerprint(retentionHours);
        Map<String, Object> scope = baseScope(encoder.websiteEffectiveConfig(retentionHours));
        scope.put("requestLimit", requestLimit);
        return new RetentionExecuteCommand(request.getTriggerId(), RetentionDomain.WEBSITE_PUBLISHED_CONTENT,
                ExecutionSource.MANUAL, operator, request.getReason(), request.getApprovalRef(), request.getTicketRef(),
                contract(RetentionDomain.WEBSITE_PUBLISHED_CONTENT, fingerprint,
                        DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION),
                scope, null, null, budgetGate.serverBudget(), null);
    }

    public RetentionExecuteCommand manualPoll(PollRetentionDryRunRequest request, long operator) {
        int hotDays = request.getHotRetentionDays() == null ? 14 : request.getHotRetentionDays();
        int requestLimit = request.getLimit() == null ? 20 : request.getLimit();
        if (request.getCursorBatchDate() != null || request.getCursorProjectId() != null
                || request.getCursorQuestionTier() != null) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll execute cursor is only supported by dry-run");
        }
        if (hotDays < 14 || requestLimit < 1 || requestLimit > 20
                || request.getProjectId() != null && request.getProjectId() <= 0) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll hot window, request limit, or project filter is outside the server policy");
        }
        LocalDate cutoff = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).minusDays(hotDays);
        LocalDate effectiveStart = request.getStartDate();
        LocalDate effectiveEnd = request.getEndDate() == null || request.getEndDate().isAfter(cutoff)
                ? cutoff : request.getEndDate();
        if (effectiveStart != null && effectiveStart.isAfter(effectiveEnd)) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll date range is empty after the retention cutoff clamp");
        }
        String tier = normalizeTierFilter(request.getQuestionTier());
        CanonicalValue fingerprint = encoder.pollFingerprint(hotDays, request.getStuckBatchSealDays());
        Map<String, Object> scope = baseScope(encoder.pollEffectiveConfig(hotDays, request.getStuckBatchSealDays()));
        scope.put("requestLimit", requestLimit);
        scope.put("projectId", request.getProjectId());
        scope.put("questionTier", tier);
        scope.put("requestedStartDate", request.getStartDate() == null ? null : request.getStartDate().toString());
        scope.put("requestedEndDate", request.getEndDate() == null ? null : request.getEndDate().toString());
        scope.put("effectiveStartDate", effectiveStart == null ? null : effectiveStart.toString());
        scope.put("effectiveEndDate", effectiveEnd.toString());
        return new RetentionExecuteCommand(request.getTriggerId(), RetentionDomain.POLL_RESULTS,
                ExecutionSource.MANUAL, operator, request.getReason(), request.getApprovalRef(), request.getTicketRef(),
                contract(RetentionDomain.POLL_RESULTS, fingerprint, DataRetentionCanonicalEncoder.POLL_POLICY_VERSION),
                scope, effectiveStart, effectiveEnd, budgetGate.serverBudget(), null);
    }

    public RetentionExecuteCommand canaryPoll(PollRetentionDryRunRequest request, long operator) {
        RetentionExecuteCommand manual = manualPoll(request, operator);
        if (!Integer.valueOf(1).equals(manual.scope().get("requestLimit"))) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll canary limit must be one");
        }
        return new RetentionExecuteCommand(manual.triggerId(), manual.domain(), ExecutionSource.CANARY,
                manual.requestedBy(), manual.reason(), manual.approvalRef(), manual.ticketRef(),
                manual.compatibilityContract(), manual.scope(), manual.retentionWindowStart(),
                manual.retentionWindowEnd(), manual.budget(), null);
    }

    public RetentionExecuteCommand schedulerWebsite(String triggerId) {
        int hours = properties.getScheduler().getWebsitePublishedRetentionHours();
        CanonicalValue fingerprint = encoder.websiteFingerprint(hours);
        Map<String, Object> scope = baseScope(encoder.websiteEffectiveConfig(hours));
        scope.put("requestLimit", properties.getScheduler().getLimitPerDomain());
        return new RetentionExecuteCommand(triggerId, RetentionDomain.WEBSITE_PUBLISHED_CONTENT,
                ExecutionSource.SCHEDULER, properties.getScheduler().getOperatorUserId(),
                "scheduled website published hot-data cleanup", null, null,
                contract(RetentionDomain.WEBSITE_PUBLISHED_CONTENT, fingerprint,
                        DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION), scope, null, null,
                budgetGate.serverBudget(), null);
    }

    public RetentionExecuteCommand schedulerPoll(String triggerId) {
        int days = properties.getScheduler().getPollHotRetentionDays();
        if (days < 14) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Scheduled poll hot window must be at least 14 days");
        }
        LocalDate cutoff = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).minusDays(days);
        CanonicalValue fingerprint = encoder.pollFingerprint(days, null);
        Map<String, Object> scope = baseScope(encoder.pollEffectiveConfig(days, null));
        scope.put("requestLimit", Math.min(properties.getScheduler().getLimitPerDomain(), 20));
        scope.put("projectId", null);
        scope.put("questionTier", null);
        scope.put("requestedStartDate", null);
        scope.put("requestedEndDate", null);
        scope.put("effectiveStartDate", null);
        scope.put("effectiveEndDate", cutoff.toString());
        return new RetentionExecuteCommand(triggerId, RetentionDomain.POLL_RESULTS,
                ExecutionSource.SCHEDULER, properties.getScheduler().getOperatorUserId(),
                "scheduled poll detail retention", null, null,
                contract(RetentionDomain.POLL_RESULTS, fingerprint, DataRetentionCanonicalEncoder.POLL_POLICY_VERSION),
                scope, null, cutoff,
                budgetGate.serverBudget(), null);
    }

    public RetentionExecuteCommand schedulerDeferredRetry(DeferredRetryOperation operation, long operator) {
        Map<String, Object> scope = new LinkedHashMap<>(operation.originScope());
        scope.remove("executionBudget");
        RetentionDomain retryDomain = domain(operation.domain());
        LocalDate effectiveStart = null;
        LocalDate effectiveEnd = null;
        if (retryDomain == RetentionDomain.POLL_RESULTS) {
            effectiveStart = retryDate(scope, "effectiveStartDate", false);
            effectiveEnd = retryDate(scope, "effectiveEndDate", true);
            if (effectiveStart != null && effectiveStart.isAfter(effectiveEnd)) {
                throw invalidDeferredRetry("poll effective date range is empty");
            }
        }
        scope.put("executionInvocation", Map.of(
                "kind", "scheduler_deferred_retry",
                "originRunId", operation.originRunId(),
                "relatedOperationId", operation.operationId(),
                "schemaVersion", 1));
        return new RetentionExecuteCommand(newTriggerId(),
                retryDomain, ExecutionSource.RETRY, operator,
                "recovery_not_started", null, "retention-recovery:" + operation.operationId(),
                operation.compatibilityContract(), scope, effectiveStart, effectiveEnd,
                budgetGate.serverBudget(), operation.operationId());
    }

    public String newTriggerId() { return UUID.randomUUID().toString(); }

    private static RetentionDomain domain(String value) {
        return java.util.Arrays.stream(RetentionDomain.values())
                .filter(domain -> domain.databaseValue().equals(value))
                .findFirst()
                .orElseThrow(() -> invalidDeferredRetry("unsupported deferred retry domain"));
    }

    private static LocalDate retryDate(Map<String, Object> scope, String key, boolean required) {
        Object value = scope.get(key);
        if (value == null) {
            if (required) throw invalidDeferredRetry("poll " + key + " is missing");
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidDeferredRetry("poll " + key + " is malformed");
        }
        try {
            return LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException malformed) {
            throw invalidDeferredRetry("poll " + key + " is malformed");
        }
    }

    private static DataRetentionExecutionException invalidDeferredRetry(String message) {
        return new DataRetentionExecutionException(503, "DEFERRED_RETRY_EVIDENCE_INVALID", message);
    }

    private static RetentionCompatibilityContract contract(RetentionDomain domain,
                                                            CanonicalValue fingerprint,
                                                            String policy) {
        return new RetentionCompatibilityContract(domain.databaseValue(), 1, fingerprint.sha256(), policy);
    }

    private static Map<String, Object> baseScope(Map<String, Object> effectiveConfig) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("effectiveConfig", effectiveConfig);
        return scope;
    }

    private static String normalizeTierFilter(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() > 16 || !normalized.matches("[A-Z0-9_]+")) {
            throw new DataRetentionExecutionException(400, "POLL_SELECTION_INVALID",
                    "Poll question tier is invalid");
        }
        return normalized;
    }
}
