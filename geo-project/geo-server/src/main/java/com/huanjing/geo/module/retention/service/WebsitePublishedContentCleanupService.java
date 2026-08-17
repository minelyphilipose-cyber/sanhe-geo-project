package com.huanjing.geo.module.retention.service;

import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder.CanonicalValue;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.dto.WebsitePublishedCleanupRequest;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDryRunReader;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDryRunReader.DryRunCandidate;
import com.huanjing.geo.module.retention.website.WebsiteRetentionDryRunReader.DryRunPage;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebsitePublishedContentCleanupService {
    private static final int DEFAULT_RETENTION_HOURS = 24;
    private static final int MIN_RETENTION_HOURS = 24;
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1_000;
    private static final int MAX_PAGES = 1_000;
    private static final String DEFAULT_MANUAL_REASON = "website published content dry-run";

    private final WebsiteRetentionDryRunReader reader;
    private final DataRetentionP0AuditService audit;
    private final DataRetentionCanonicalEncoder encoder;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Autowired
    public WebsitePublishedContentCleanupService(WebsiteRetentionDryRunReader reader,
                                                 DataRetentionP0AuditService audit,
                                                 DataRetentionCanonicalEncoder encoder,
                                                 CurrentUserService currentUserService) {
        this(reader, audit, encoder, currentUserService, Clock.systemUTC());
    }

    WebsitePublishedContentCleanupService(WebsiteRetentionDryRunReader reader,
                                          DataRetentionP0AuditService audit,
                                          DataRetentionCanonicalEncoder encoder,
                                          CurrentUserService currentUserService,
                                          Clock clock) {
        this.reader = reader;
        this.audit = audit;
        this.encoder = encoder;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public CleanupBatchResult dryRun(WebsitePublishedCleanupRequest request) {
        WebsitePublishedCleanupRequest safe = request == null ? new WebsitePublishedCleanupRequest() : request;
        currentUserService.ensurePermission("dispatch.task.release");
        SysUser operator = currentUserService.requireCurrentUser();
        return runDryRun(safe.getRetentionHours(), safe.getLimit(), safe.getCursorArticleId(), 1,
                "manual", operator.getId(), safe.getReason());
    }

    public CleanupBatchResult runScheduledDryRun(Integer retentionHours,
                                                  Integer requestedLimit,
                                                  Integer requestedMaxPages,
                                                  String reason,
                                                  long requestedBy) {
        if (requestedBy <= 0) {
            throw new IllegalArgumentException("Website dry-run scheduler operator is invalid");
        }
        return runDryRun(retentionHours, requestedLimit, null, normalizeMaxPages(requestedMaxPages),
                "scheduler", requestedBy, reason);
    }

    private CleanupBatchResult runDryRun(Integer requestedRetentionHours,
                                         Integer requestedLimit,
                                         Long initialCursorArticleId,
                                         int maxPages,
                                         String source,
                                         long requestedBy,
                                         String reason) {
        int retentionHours = normalizeRetentionHours(requestedRetentionHours);
        int limit = normalizeLimit(requestedLimit);
        Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant cutoff = startedAt.minus(retentionHours, ChronoUnit.HOURS);
        CanonicalValue fingerprint = encoder.websiteFingerprint(retentionHours);
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "website_published_content", DataRetentionCanonicalEncoder.SCHEMA_VERSION,
                fingerprint.sha256(), DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION);
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("effectiveConfig", encoder.websiteEffectiveConfig(retentionHours));
        scope.put("configFingerprintSchemaVersion", contract.configFingerprintSchemaVersion());
        scope.put("configFingerprint", DataRetentionCanonicalEncoder.hex(contract.configFingerprint()));
        scope.put("dryRunSchemaVersion", 1);
        scope.put("pageLimit", limit);
        scope.put("maxPages", maxPages);
        scope.put("startsAfterCursor", initialCursorArticleId != null);
        RetentionRunContext run = audit.startRun(new RetentionRunStartCommand(
                UUID.randomUUID().toString(), source, "dry_run", requestedBy,
                normalizeReason(reason, source), null, null, contract, scope,
                null, null, startedAt));

        List<CleanupItem> items = new ArrayList<>();
        long candidateCount = 0;
        long warningCount = 0;
        int completedPages = 0;
        boolean hasMore = false;
        Long cursor = initialCursorArticleId;
        Long expectedLatestAttemptId = null;
        for (int pageNumber = 1; pageNumber <= maxPages; pageNumber++) {
            RetentionAttemptContext attempt = audit.createOperationlessAttempt(run,
                    new RetentionAttemptCommand(UUID.randomUUID().toString(), AttemptType.PAGE,
                            expectedLatestAttemptId, expectedLatestAttemptId,
                            Map.of("dryRunPageSchemaVersion", 1,
                                    "pageNumber", pageNumber,
                                    "pageLimit", limit)));
            audit.markAttemptRunning(attempt.attemptId());
            DryRunPage page;
            try {
                page = reader.readPage(cutoff, cursor, limit);
            } catch (RuntimeException failure) {
                warningCount++;
                audit.completeAttempt(attempt.attemptId(), new RetentionAttemptResult(
                        AttemptStatus.FAILED, 0, 0, 0, 1,
                        Map.of("dryRunPageSchemaVersion", 1, "pageNumber", pageNumber),
                        "WEBSITE_DRY_RUN_FAILED", "Website dry-run page read failed",
                        clock.instant().truncatedTo(ChronoUnit.MILLIS)));
                audit.finishRun(run.runId(), new RunBusinessSummary(candidateCount, 0, 0, warningCount,
                        summaryMetrics(completedPages, true)));
                throw failure;
            }
            int pageCandidates = page.candidates().size();
            audit.completeAttempt(attempt.attemptId(), new RetentionAttemptResult(
                    AttemptStatus.SUCCEEDED, pageCandidates, 0, 0, 0,
                    Map.of("dryRunPageSchemaVersion", 1,
                            "pageNumber", pageNumber,
                            "hasMore", page.hasMore()),
                    null, null, clock.instant().truncatedTo(ChronoUnit.MILLIS)));
            completedPages++;
            candidateCount += pageCandidates;
            page.candidates().stream().map(CleanupItem::pending).forEach(items::add);
            hasMore = page.hasMore();
            cursor = page.nextCursorArticleId();
            expectedLatestAttemptId = attempt.attemptId();
            if (!hasMore || cursor == null) {
                break;
            }
        }
        audit.finishRun(run.runId(), new RunBusinessSummary(candidateCount, 0, 0, warningCount,
                summaryMetrics(completedPages, hasMore)));
        return new CleanupBatchResult(run.runId(), true, retentionHours, Math.toIntExact(candidateCount),
                0, 0, 0, 0, 0, 0, hasMore, cursor, List.copyOf(items));
    }

    private static int normalizeRetentionHours(Integer requestedRetentionHours) {
        return requestedRetentionHours == null
                ? DEFAULT_RETENTION_HOURS
                : Math.max(MIN_RETENTION_HOURS, requestedRetentionHours);
    }

    private static int normalizeLimit(Integer requestedLimit) {
        int value = requestedLimit == null || requestedLimit <= 0 ? DEFAULT_LIMIT : requestedLimit;
        return Math.min(value, MAX_LIMIT);
    }

    private static int normalizeMaxPages(Integer requestedMaxPages) {
        int value = requestedMaxPages == null || requestedMaxPages <= 0 ? 1 : requestedMaxPages;
        return Math.min(value, MAX_PAGES);
    }

    private static String normalizeReason(String reason, String source) {
        if (StringUtils.hasText(reason)) {
            return reason.trim();
        }
        return "scheduler".equals(source)
                ? "scheduled website published content dry-run"
                : DEFAULT_MANUAL_REASON;
    }

    private static Map<String, Object> summaryMetrics(int pageCount, boolean hasMore) {
        return Map.of("dryRunSchemaVersion", 1, "pageCount", pageCount, "hasMore", hasMore);
    }

    public record CleanupItem(Long articleId,
                              Long projectId,
                              String articleStatus,
                              java.time.LocalDateTime lastPublishedAt,
                              int bodyRowCount,
                              long bodyBytes,
                              int payloadRowCount,
                              long payloadBytes,
                              String result,
                              int purgedBodyRows,
                              int purgedPayloadRows,
                              int slimmedPublishRecordRows,
                              String errorMessage) {
        private static CleanupItem pending(DryRunCandidate candidate) {
            return new CleanupItem(candidate.articleId(), candidate.projectId(), candidate.articleStatus(),
                    candidate.lastPublishedAt(), candidate.bodyRowCount(), candidate.bodyBytes(),
                    candidate.payloadRowCount(), candidate.payloadBytes(), "pending", 0, 0, 0, null);
        }
    }

    public record CleanupBatchResult(Long retentionRunId,
                                     boolean dryRun,
                                     int retentionHours,
                                     int candidateCount,
                                     int cleanedCount,
                                     int skippedCount,
                                     int failedCount,
                                     long bodyRows,
                                     long payloadRows,
                                     long publishRecordRows,
                                     boolean hasMore,
                                     Long nextCursorArticleId,
                                     List<CleanupItem> items) {
    }
}
