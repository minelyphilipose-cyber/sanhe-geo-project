package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.generate.llm.AnalyzeParseException;
import com.huanjing.geo.module.presale.generate.llm.AnalyzePromptTemplates;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.generate.l3.PresalePage03DoubaoService;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Presale generation orchestrator.
 * mockEnabled=true: fixture flow.
 * mockEnabled=false: PR-3 real pipeline full flow.
 */
@Component
public class PresaleGenerateOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PresaleGenerateOrchestrator.class);
    private static final int FAILURE_REASON_MAX_LEN = 500;
    private static final long PROGRESS_UPDATE_THROTTLE_MS = 1000L;

    private static final String STAGE_BATCH1 = "BATCH1";
    private static final String STAGE_COMPETITOR_EXTRACT = "COMPETITOR_EXTRACT";
    private static final String STAGE_BATCH2 = "BATCH2";
    private static final String STAGE_JUDGE_COGNITIVE = "JUDGE_COGNITIVE";
    private static final String STAGE_JUDGE_COMPARISON = "JUDGE_COMPARISON";
    private static final String STAGE_L1_AGGREGATE = "L1_AGGREGATE";
    private static final String STAGE_L2_COMPUTE = "L2_COMPUTE";
    private static final String STAGE_L3_INIT = "L3_INIT";
    private static final int PAGE03_DOUBAO_CALLS = 1;
    private static final int EVALUATION_MODEL_BUSY_ATTEMPTS = 3;
    private static final long EVALUATION_MODEL_BUSY_RETRY_INTERVAL_MS = 100L;
    private static final int EVALUATION_MODEL_BUSY_COMPENSATION_ATTEMPTS = 2;
    private static final long EVALUATION_MODEL_BUSY_COMPENSATION_INTERVAL_MS = 1000L;
    private static final String CATEGORY_COGNITIVE = "认知型";
    private static final String CATEGORY_COMPARISON = "对比型";
    private static final String FAILURE_CATEGORY_CONFIG_MISSING = "CONFIG_MISSING";
    private static final String FAILURE_CATEGORY_TOO_MANY_DEGRADED = "TOO_MANY_DEGRADED_PLATFORMS";
    private static final String FAILURE_CATEGORY_INTERRUPTED = "INTERRUPTED";
    private static final String FAILURE_CATEGORY_UNEXPECTED_ERROR = "UNEXPECTED_ERROR";
    private static final String FAILURE_CATEGORY_SNAPSHOT_BUILD_ERROR = "SNAPSHOT_BUILD_ERROR";
    private static final String FAILURE_CATEGORY_STAGE_D_CHECKPOINT = "STAGE_D_CHECKPOINT";
    private static final String FAILURE_CATEGORY_COMPETITOR_EXTRACT_EMPTY = "COMPETITOR_EXTRACT_EMPTY";
    private static final String FAILURE_CATEGORY_L1_SERIALIZATION_ERROR = "L1_SERIALIZATION_ERROR";
    private static final String FAILURE_CATEGORY_L2_COMPUTE_ERROR = "L2_COMPUTE_ERROR";
    private static final String FAILURE_CATEGORY_L3_INIT_ERROR = "L3_INIT_ERROR";
    private static final int ANALYZE_TOP_KEYWORDS_MAX = 5;
    private static final String DICT_TYPE_PRESALE_INDUSTRY = "presale_industry";
    private static final String DICT_TYPE_PRESALE_INDUSTRY_ROLE = "presale_industry_role";

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleReportMapper reportMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final ReuseDecisionService reuseDecisionService;
    private final PresaleReusePersistenceService reusePersistenceService;
    private final PresaleLlmInvoker llmInvoker;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final PresaleRawSnapshotAssembler rawSnapshotAssembler;
    private final PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    private final PresaleL3InitService l3InitService;
    private final PresalePage03DoubaoService page03DoubaoService;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final PresaleCompetitorNormalizationService competitorNormalizationService;
    private final PresaleJudgeService presaleJudgeService;
    private final PresaleEvaluationModelRouter evaluationModelRouter;
    private final PresaleGenerateCancellationRegistry cancellationRegistry;
    private final ObjectMapper objectMapper;
    private final Executor platformExecutor;
    private final Map<Long, AtomicLong> lastProgressUpdateAtByVersion = new ConcurrentHashMap<>();
    private final Map<Long, StageTiming> stageTimingByVersion = new ConcurrentHashMap<>();
    private final Map<String, CallModelSnapshot> modelSnapshotByPlatformCode = new ConcurrentHashMap<>();
    private volatile Semaphore dbWriteSemaphore;

    @Value("${presale.generate.mock}")
    private boolean mockEnabled;

    @Value("${presale.generate.mock-delay-ms}")
    private long mockDelayMs;

    @Value("${presale.generate.mock-fixture-path}")
    private String mockFixturePath;

    @Value("${presale.generate.allow-synthetic-fallback.mock}")
    private boolean allowSyntheticFallbackMock;

    @Value("${presale.generate.allow-synthetic-fallback.real}")
    private boolean allowSyntheticFallbackReal;

    @Value("${presale.generate.prompt-max-concurrency-per-report:24}")
    private int promptMaxConcurrencyPerReport = 24;

    @Value("${presale.generate.db-write-max-concurrency:12}")
    private int dbWriteMaxConcurrency = 12;

    @Value("${presale.generate.max-concurrent-reports:1}")
    private int maxConcurrentReports = 1;

    public PresaleGenerateOrchestrator(PresaleReportVersionMapper versionMapper,
                                       PresaleReportMapper reportMapper,
                                       AiPlatformConfigMapper aiPlatformConfigMapper,
                                       SysDictItemMapper sysDictItemMapper,
                                       PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper,
                                       PresaleAiCallMapper aiCallMapper,
                                       PresaleAiPromptResultMapper aiPromptResultMapper,
                                       ReuseDecisionService reuseDecisionService,
                                       PresaleReusePersistenceService reusePersistenceService,
                                       PresaleLlmInvoker llmInvoker,
                                       PromptTemplateRenderer promptTemplateRenderer,
                                       PresaleRawSnapshotAssembler rawSnapshotAssembler,
                                       PresaleComputedSnapshotEnricher computedSnapshotEnricher,
                                       PresaleL3InitService l3InitService,
                                       PresalePage03DoubaoService page03DoubaoService,
                                       PresaleCompetitorAggregator competitorAggregator,
                                       PresaleCompetitorNormalizationService competitorNormalizationService,
                                       PresaleJudgeService presaleJudgeService,
                                       PresaleEvaluationModelRouter evaluationModelRouter,
                                       PresaleGenerateCancellationRegistry cancellationRegistry,
                                       ObjectMapper objectMapper,
                                       @Qualifier("presalePlatformExecutor") Executor platformExecutor) {
        this.versionMapper = versionMapper;
        this.reportMapper = reportMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.sysDictItemMapper = sysDictItemMapper;
        this.versionPromptTemplateMapper = versionPromptTemplateMapper;
        this.aiCallMapper = aiCallMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.reuseDecisionService = reuseDecisionService;
        this.reusePersistenceService = reusePersistenceService;
        this.llmInvoker = llmInvoker;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.rawSnapshotAssembler = rawSnapshotAssembler;
        this.computedSnapshotEnricher = computedSnapshotEnricher;
        this.l3InitService = l3InitService;
        this.page03DoubaoService = page03DoubaoService;
        this.competitorAggregator = competitorAggregator;
        this.competitorNormalizationService = competitorNormalizationService;
        this.presaleJudgeService = presaleJudgeService;
        this.evaluationModelRouter = evaluationModelRouter;
        this.cancellationRegistry = cancellationRegistry;
        this.objectMapper = objectMapper;
        this.platformExecutor = Objects.requireNonNull(platformExecutor, "presalePlatformExecutor must not be null");
    }

    private Semaphore dbWriteSemaphore() {
        Semaphore local = dbWriteSemaphore;
        if (local == null) {
            synchronized (this) {
                local = dbWriteSemaphore;
                if (local == null) {
                    local = new Semaphore(Math.max(1, dbWriteMaxConcurrency));
                    dbWriteSemaphore = local;
                }
            }
        }
        return local;
    }

    private <T> T withDbWritePermit(String operation, Supplier<T> action) {
        Semaphore semaphore = dbWriteSemaphore();
        semaphore.acquireUninterruptibly();
        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }

    private void withDbWritePermit(String operation, Runnable action) {
        withDbWritePermit(operation, () -> {
            action.run();
            return null;
        });
    }

    private int updateVersionById(PresaleReportVersion update, String operation) {
        return withDbWritePermit(operation, () -> versionMapper.updateById(update));
    }

    private int updateReportById(PresaleReport report, String operation) {
        return withDbWritePermit(operation, () -> reportMapper.updateById(report));
    }

    private int insertAiCall(PresaleAiCall row, String operation) {
        return withDbWritePermit(operation, () -> aiCallMapper.insert(row));
    }

    private int insertAiPromptResult(PresaleAiPromptResult row, String operation) {
        return withDbWritePermit(operation, () -> aiPromptResultMapper.insert(row));
    }

    @Async("presaleGenerateExecutor")
    public void triggerGenerate(Long versionId, Long operatorUserId, boolean isManager) {
        try {
            doTriggerGenerate(versionId, operatorUserId, isManager);
        } catch (Throwable t) {
            log.error("Presale generate fatal error, versionId={}", versionId, t);
            try {
                if (isInterruptedFailure(t) && !isGenerationActive(versionId)) {
                    cancellationRegistry.clear(versionId);
                    lastProgressUpdateAtByVersion.remove(versionId);
                    log.info("Presale generate stopped by inactive status, versionId={}", versionId);
                    return;
                }
                String failureCategory = isInterruptedFailure(t)
                        ? FAILURE_CATEGORY_INTERRUPTED
                        : FAILURE_CATEGORY_UNEXPECTED_ERROR;
                markFailed(versionId, failureCategory,
                        truncateReason("Unexpected error: " + t.getClass().getSimpleName()));
            } catch (Throwable markFailedError) {
                log.error("Failed to mark presale version FAILED after fatal error, versionId={}", versionId, markFailedError);
            }
        }
    }

    private void doTriggerGenerate(Long versionId, Long operatorUserId, boolean isManager) {
        int updated = versionMapper.tryTransitionToRunning(versionId, safeMaxConcurrentReports());
        if (updated == 0) {
            PresaleReportVersion current = versionMapper.selectById(versionId);
            if (current != null && PresaleGenerateStatus.QUEUED.name().equals(current.getGenerationStatus())) {
                log.info("version={} remains QUEUED because presale generation capacity is full, maxConcurrentReports={}",
                        versionId, safeMaxConcurrentReports());
            } else {
                log.info("version={} not in QUEUED state, skip duplicate trigger", versionId);
            }
            return;
        }
        cancellationRegistry.clear(versionId);
        modelSnapshotByPlatformCode.clear();
        if (mockEnabled) {
            runMockFlow(versionId);
            return;
        }
        runRealFullFlow(versionId, operatorUserId, isManager);
    }

    private int safeMaxConcurrentReports() {
        return Math.max(1, maxConcurrentReports);
    }

    private void runMockFlow(Long versionId) {
        log.info("Presale mock generate start, versionId={}, delay={}ms", versionId, mockDelayMs);
        markRunningForMock(versionId);

        try {
            Thread.sleep(mockDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(versionId, FAILURE_CATEGORY_INTERRUPTED, truncateReason("Generation interrupted"));
            return;
        }
        ensureGenerationStillRunning(versionId, "mock");

        String rawJson;
        String computedJson;
        String editableJson;
        try {
            FixturePayload payload = loadFixturePayload();
            rawJson = payload.rawJson();
            computedJson = computedSnapshotEnricher.enrichAndValidate(
                    versionId, rawJson, payload.computedJson(), resolveAllowSyntheticFallback()
            );
            editableJson = l3InitService.derive(rawJson, computedJson);
        } catch (Exception e) {
            log.error("Failed to build presale snapshot, fixturePath={}", mockFixturePath, e);
            markFailed(versionId, FAILURE_CATEGORY_SNAPSHOT_BUILD_ERROR,
                    truncateReason("Snapshot build failed: " + e.getMessage()));
            return;
        }

        ensureGenerationStillRunning(versionId, "mock");
        PresaleReportVersion current = versionMapper.selectById(versionId);
        int totalCalls = current == null || current.getTotalLlmCalls() == null
                ? 0 : current.getTotalLlmCalls();

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.DONE.name());
        update.setGenerationStage(null);
        update.setCompletedLlmCalls(totalCalls);
        update.setTotalLlmCalls(totalCalls);
        update.setBatch1CompletedCalls(current == null ? null : current.getBatch1TotalCalls());
        update.setBatch2CompletedCalls(current == null ? null : current.getBatch2TotalCalls());
        update.setIsDegraded(false);
        update.setRawSnapshotJson(rawJson);
        update.setComputedSnapshotJson(computedJson);
        update.setEditableContentJson(editableJson);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        syncReportGenerationStatus(versionId, PresaleGenerateStatus.DONE.name());

        log.info("Presale mock generate done, versionId={}", versionId);
    }

    private void runRealFullFlow(Long versionId, Long operatorUserId, boolean isManager) {
        PreflightResult preflight = preflight(versionId);
        if (!preflight.success()) {
            markFailed(versionId, FAILURE_CATEGORY_CONFIG_MISSING,
                    truncateReason("CONFIG_MISSING: " + preflight.failureReason()));
            return;
        }

        markRunning(versionId, preflight.totalUpperBoundCalls(), preflight.batch1TotalCalls());
        PresaleReportVersion version = versionMapper.selectById(versionId);
        PresaleReport report = version == null ? null : reportMapper.selectById(version.getReportId());
        if (version == null || report == null) {
            markFailed(versionId, FAILURE_CATEGORY_CONFIG_MISSING, "CONFIG_MISSING: report/version not found during batch1");
            return;
        }
        PresaleReport promptRenderReport = buildPromptRenderReport(report);
        Batch1ExecutionResult batch1Result = executeBatch1(version, report, promptRenderReport, operatorUserId, isManager, preflight);
        if (batch1Result.stopPipeline) {
            return;
        }
        ensureGenerationStillRunning(versionId, "batch1");

        enterStage(versionId, STAGE_JUDGE_COGNITIVE, "judge cognitive");
        presaleJudgeService.judgeCognitiveAfterBatch1(
                versionId,
                report.getBrandName(),
                operatorUserId,
                isManager,
                judgeProgressCallback(versionId, preflight.batch1TotalCalls(), preflight.totalUpperBoundCalls())
        );
        ensureGenerationStillRunning(versionId, STAGE_JUDGE_COGNITIVE);

        Set<String> allDegraded = new LinkedHashSet<>(batch1Result.degradedPlatforms());

        enterStage(versionId, STAGE_COMPETITOR_EXTRACT, "extract competitors");

        List<String> specifiedCompetitors = parseSpecifiedCompetitors(report);
        List<String> selfBrandNames = selfBrandNames(report);
        PresaleCompetitorNormalizationService.NormalizationOutcome normalizationOutcome =
                specifiedCompetitors.isEmpty()
                        ? extractTopCompetitorsFromBatch1(versionId, report.getBrandName(), selfBrandNames, operatorUserId, isManager)
                        : specifiedCompetitorsFromBatch1Stats(versionId, selfBrandNames, specifiedCompetitors);
        List<PresaleCompetitorAggregator.ExtractedCompetitor> extractedCompetitorStats =
                normalizationOutcome.competitors();
        ensureGenerationStillRunning(versionId, STAGE_COMPETITOR_EXTRACT);
        int competitorNormalizationCalls = normalizationOutcome.llmCalled() ? 1 : 0;
        if (competitorNormalizationCalls > 0) {
            updateCompletedLlmCalls(versionId,
                    preflight.batch1TotalCalls() + preflight.cognitiveJudgeTotalCalls() + competitorNormalizationCalls);
        }
        List<String> extractedCompetitors = extractedCompetitorStats.stream()
                .map(PresaleCompetitorAggregator.ExtractedCompetitor::name)
                .toList();
        int extractedCompetitorCount = extractedCompetitors.size();
        int batch2TotalCalls = extractedCompetitorCount > 0
                ? preflight.platformCount() * preflight.competitorPromptCount() * 2
                : 0;
        int comparisonJudgeTotalCalls = extractedCompetitorCount > 0
                ? preflight.comparisonJudgeTotalCalls()
                : 0;
        updateAfterCompetitorExtract(
                versionId,
                extractedCompetitorCount,
                batch2TotalCalls,
                preflight.batch1TotalCalls() + preflight.cognitiveJudgeTotalCalls() + competitorNormalizationCalls
                        + batch2TotalCalls + comparisonJudgeTotalCalls
        );

        if (extractedCompetitorCount > 0) {
            Batch2ExecutionResult batch2Result = executeBatch2(
                    versionId,
                    report,
                    promptRenderReport,
                    operatorUserId,
                    isManager,
                    extractedCompetitors,
                    preflight.competitorPromptCount(),
                    batch1Result.degradedPlatforms()
            );
            if (batch2Result.stopPipeline) {
                return;
            }
            allDegraded.addAll(batch2Result.batch2DegradedPlatforms());
            ensureGenerationStillRunning(versionId, "batch2");
        } else {
            markCompetitorExtractEmpty(versionId);
            log.info("Skip batch2 because extracted competitors is 0, versionId={}", versionId);
        }

        enterStage(versionId, STAGE_JUDGE_COMPARISON, "judge comparison");
        presaleJudgeService.judgeComparisonAfterBatch2(
                versionId,
                report.getBrandName(),
                operatorUserId,
                isManager,
                judgeProgressCallback(
                        versionId,
                        preflight.batch1TotalCalls() + preflight.cognitiveJudgeTotalCalls()
                                + competitorNormalizationCalls + batch2TotalCalls,
                        preflight.batch1TotalCalls() + preflight.cognitiveJudgeTotalCalls()
                                + competitorNormalizationCalls
                                + batch2TotalCalls + comparisonJudgeTotalCalls
                )
        );
        ensureGenerationStillRunning(versionId, STAGE_JUDGE_COMPARISON);

        String rawJson;
        enterStage(versionId, STAGE_L1_AGGREGATE, "assemble raw snapshot");
        try {
            rawJson = assembleRawSnapshot(
                    versionId, report, version, allDegraded, extractedCompetitorStats, extractedCompetitors);
        } catch (IllegalStateException ex) {
            markFailed(versionId, FAILURE_CATEGORY_CONFIG_MISSING,
                    truncateReason("L1 aggregate failed: " + ex.getMessage()));
            return;
        } catch (BizException ex) {
            String msg = ex.getMessage();
            String category = msg != null && msg.contains("BENCHMARK_MISSING")
                    ? FAILURE_CATEGORY_CONFIG_MISSING
                    : FAILURE_CATEGORY_L1_SERIALIZATION_ERROR;
            markFailed(versionId, category, truncateReason("L1 aggregate failed: " + msg));
            return;
        }
        ensureGenerationStillRunning(versionId, STAGE_L1_AGGREGATE);
        writeRawSnapshotJson(versionId, rawJson);

        String computedJson;
        enterStage(versionId, STAGE_L2_COMPUTE, "compute computed snapshot");
        try {
            PresaleReportVersion current = versionMapper.selectById(versionId);
            String currentComputedJson = current == null ? null : current.getComputedSnapshotJson();
            computedJson = computedSnapshotEnricher.enrichAndValidate(
                    versionId, rawJson, currentComputedJson, allowSyntheticFallbackReal);
        } catch (BizException ex) {
            markFailed(versionId, FAILURE_CATEGORY_L2_COMPUTE_ERROR,
                    truncateReason("L2 compute failed: " + ex.getMessage()));
            return;
        }
        ensureGenerationStillRunning(versionId, STAGE_L2_COMPUTE);
        writeComputedSnapshotJson(versionId, computedJson);

        String editableJson;
        enterStage(versionId, STAGE_L3_INIT, "derive editable content");
        try {
            editableJson = l3InitService.derive(rawJson, computedJson);
            editableJson = page03DoubaoService.generateAndApply(
                    versionId, rawJson, editableJson, operatorUserId, isManager);
        } catch (BizException ex) {
            markFailed(versionId, FAILURE_CATEGORY_L3_INIT_ERROR,
                    truncateReason("L3 init failed: " + ex.getMessage()));
            return;
        }
        ensureGenerationStillRunning(versionId, STAGE_L3_INIT);
        writeEditableContentJson(versionId, editableJson);

        ensureGenerationStillRunning(versionId, "markDone");
        markDone(versionId);
        log.info("Presale real full flow done, versionId={}, operatorUserId={}, isManager={}",
                versionId, operatorUserId, isManager);
    }

    private PreflightResult preflight(Long versionId) {
        PresaleReportVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            return PreflightResult.fail("version not found: " + versionId);
        }
        PresaleReport report = reportMapper.selectById(version.getReportId());
        if (report == null) {
            return PreflightResult.fail("report not found: " + version.getReportId());
        }
        if (report.getBrandName() == null || report.getBrandName().isBlank()) {
            return PreflightResult.fail("report.brand_name is blank");
        }

        int platformCount = countWhitelistedPlatforms();
        if (platformCount < 1) {
            return PreflightResult.fail("whitelisted platform count is 0");
        }

        int genericPromptCount = countPromptTemplates(versionId, 0);
        if (genericPromptCount < 1) {
            return PreflightResult.fail("generic prompt count is 0");
        }

        int competitorPromptCount = countPromptTemplates(versionId, 1);
        int cognitivePromptCount = countPromptTemplates(versionId, CATEGORY_COGNITIVE, 0);
        int comparisonPromptCount = countPromptTemplates(versionId, CATEGORY_COMPARISON, 1);
        int batch1TotalCalls = platformCount * genericPromptCount * 2;
        int cognitiveJudgeTotalCalls = platformCount * cognitivePromptCount;
        int comparisonJudgeTotalCalls = platformCount * comparisonPromptCount;
        int totalUpperBoundCalls = batch1TotalCalls + cognitiveJudgeTotalCalls
                + (platformCount * competitorPromptCount * 2) + comparisonJudgeTotalCalls
                + PAGE03_DOUBAO_CALLS;
        return PreflightResult.success(platformCount, competitorPromptCount, batch1TotalCalls,
                cognitiveJudgeTotalCalls, comparisonJudgeTotalCalls, totalUpperBoundCalls);
    }

    private Batch1ExecutionResult executeBatch1(PresaleReportVersion version,
                                                PresaleReport report,
                                                PresaleReport promptRenderReport,
                                                Long operatorUserId,
                                                boolean isManager,
                                                PreflightResult preflight) {
        Long versionId = version.getId();
        enterStage(versionId, STAGE_BATCH1, "batch1 executing");

        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(PresalePlatformConfigQueries.presaleEnabledWrapper());
        List<PresaleReportVersionPromptTemplate> templates = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, 0)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getSortOrderInVersion)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getId)
        );

        Set<String> degradedPlatforms = ConcurrentHashMap.newKeySet();
        AtomicInteger degradedCount = new AtomicInteger(0);
        AtomicInteger completedCalls = new AtomicInteger(0);
        AtomicInteger skippedCalls = new AtomicInteger(0);
        AtomicInteger lastWrittenCompleted = new AtomicInteger(0);
        AtomicInteger submittedCount = new AtomicInteger(0);
        AtomicReference<Throwable> interruptedFailure = new AtomicReference<>();
        Semaphore reportPromptSemaphore = new Semaphore(promptMaxConcurrencyPerReport());
        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache =
                reuseDecisionService.preloadByVersionAndBatch(versionId, 1);
        List<CompletableFuture<PlatformBatchResult>> futures = new ArrayList<>();
        long batchStartAt = System.currentTimeMillis();
        log.info("batch=1 versionId={} platformCode=- threadName={} starting platformCount={} degradedBefore={}",
                versionId, Thread.currentThread().getName(), platforms.size(), degradedCount.get());

        for (AiPlatformConfig platform : platforms) {
            String platformCode = platform == null ? null : platform.getPlatformCode();
            if (platformCode == null || platformCode.isBlank()) {
                continue;
            }
            if (interruptedFailure.get() != null) {
                break;
            }
            try {
                submittedCount.incrementAndGet();
                CompletableFuture<PlatformBatchResult> future = CompletableFuture
                        .supplyAsync(() -> executePlatformBatch1(
                                platform,
                                versionId,
                                templates,
                                reuseCache,
                                degradedPlatforms,
                                degradedCount,
                                report,
                                promptRenderReport,
                                operatorUserId,
                                isManager,
                                completedCalls,
                                skippedCalls,
                                lastWrittenCompleted,
                                reportPromptSemaphore
                        ), platformExecutor)
                        .handle((result, ex) -> {
                            if (ex == null) {
                                if (result != null) {
                                    log.info("batch=1 versionId={} platformCode={} threadName={} status={} progressDelta={}",
                                            versionId,
                                            result.platformCode(),
                                            Thread.currentThread().getName(),
                                            result.status(),
                                            result.progressDelta());
                                }
                                return result;
                            }
                            Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                                    ? ex.getCause()
                                    : ex;
                            if (isInterruptedFailure(cause)) {
                                interruptedFailure.compareAndSet(null, cause);
                            }
                            String failedCode = platform.getPlatformCode();
                            degradedPlatforms.add(failedCode);
                            degradedCount.incrementAndGet();
                            return PlatformBatchResult.degraded(failedCode, cause);
                        });
                futures.add(future);
            } catch (RejectedExecutionException ex) {
                degradedPlatforms.add(platformCode);
                degradedCount.incrementAndGet();
                futures.add(CompletableFuture.completedFuture(
                                new PlatformBatchResult(platformCode, PlatformStatus.SKIPPED, 0, 0, 0, ex)
                ));
                log.warn("batch=1 versionId={} platformCode={} threadName={} presalePlatformExecutor rejected queueSize={} activeCount={}",
                        versionId,
                        platformCode,
                        Thread.currentThread().getName(),
                        getPlatformExecutorQueueSize(),
                        getPlatformExecutorActiveCount());
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<PlatformBatchResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        int sumProgressDelta = results.stream().mapToInt(PlatformBatchResult::progressDelta).sum();
        if (sumProgressDelta != completedCalls.get()) {
            log.warn("batch=1 versionId={} platformCode=- threadName={} progressDelta mismatch sumProgressDelta={} completedCalls={}",
                    versionId, Thread.currentThread().getName(), sumProgressDelta, completedCalls.get());
        }

        int overRunCount = Math.max(0, submittedCount.get() - 4);
        long degradedPlatformCount = results.stream().filter(r -> r.status() == PlatformStatus.DEGRADED).count();
        long skippedPlatformCount = results.stream().filter(r -> r.status() == PlatformStatus.SKIPPED).count();
        long succeededPlatformCount = results.stream().filter(r -> r.status() == PlatformStatus.DONE).count();
        log.info("batch=1 versionId={} platformCode=- threadName={} done succeeded={} degraded={} skipped={} skippedCallsWithinPlatform={} totalDurationMs={} overRunBy={}",
                versionId,
                Thread.currentThread().getName(),
                succeededPlatformCount,
                degradedPlatformCount,
                skippedPlatformCount,
                skippedCalls.get(),
                System.currentTimeMillis() - batchStartAt,
                overRunCount);
        if (interruptedFailure.get() != null) {
            log.warn("batch=1 versionId={} platformCode=- threadName={} interruptedFailureDetected={}", versionId,
                    Thread.currentThread().getName(), interruptedFailure.get().getClass().getSimpleName());
            throw new BatchInterruptedException("batch1 interrupted in async execution");
        }

        updateBatch1ProgressRolling(versionId, completedCalls.get(), degradedPlatforms, true, lastWrittenCompleted);
        if (degradedCount.get() >= 4) {
            log.info("batch=1 versionId={} platformCode=- threadName={} degradeThresholdReached degradedPlatforms={} overRunCount={}",
                    versionId,
                    Thread.currentThread().getName(),
                    degradedPlatforms,
                    overRunCount);
            markTooManyDegradedFailed(versionId, degradedPlatforms);
            return Batch1ExecutionResult.stop(degradedPlatforms, overRunCount, results);
        }
        return Batch1ExecutionResult.continuePipeline(degradedPlatforms, overRunCount, results);
    }

    PlatformBatchResult executePlatformBatch1(AiPlatformConfig platform,
                                              Long versionId,
                                              List<PresaleReportVersionPromptTemplate> templates,
                                              Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache,
                                              Set<String> degradedPlatforms,
                                              AtomicInteger degradedCount,
                                              PresaleReport report,
                                              PresaleReport promptRenderReport,
                                              Long operatorUserId,
                                              boolean isManager,
                                              AtomicInteger completedCalls,
                                              AtomicInteger skippedCalls,
                                              AtomicInteger lastWrittenCompleted,
                                              Semaphore reportPromptSemaphore) {
        String platformCode = platform.getPlatformCode();
        PlatformBatchState state = new PlatformBatchState(platformCode, templates.size());

        AtomicReference<Throwable> interruptedFailure = new AtomicReference<>();
        runPlatformPromptTasks(
                versionId,
                1,
                platformCode,
                platformConcurrency(platform),
                reportPromptSemaphore,
                templates,
                template -> processBatch1Template(
                        platformCode,
                        versionId,
                        template,
                        reuseCache,
                        state,
                        degradedPlatforms,
                        degradedCount,
                        report,
                        promptRenderReport,
                        operatorUserId,
                        isManager,
                        completedCalls,
                        skippedCalls,
                        lastWrittenCompleted
                ),
                interruptedFailure
        );
        if (interruptedFailure.get() != null) {
            throw new BatchInterruptedException("batch1 interrupted during question-level execution");
        }

        PlatformStatus status = state.isDegraded() ? PlatformStatus.DEGRADED : PlatformStatus.DONE;
        return new PlatformBatchResult(
                platformCode,
                status,
                state.processedPrompts(),
                state.failedPrompts(),
                state.processedPrompts() * 2,
                null
        );
    }

    private void processBatch1Template(String platformCode,
                                       Long versionId,
                                       PresaleReportVersionPromptTemplate template,
                                       Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache,
                                       PlatformBatchState state,
                                       Set<String> degradedPlatforms,
                                       AtomicInteger degradedCount,
                                       PresaleReport report,
                                       PresaleReport promptRenderReport,
                                       Long operatorUserId,
                                       boolean isManager,
                                       AtomicInteger completedCalls,
                                       AtomicInteger skippedCalls,
                                       AtomicInteger lastWrittenCompleted) {
        ensureGenerationStillRunning(versionId, "batch1");
        if (state.isDegraded()) {
            insertSkippedCall(versionId, 1, platformCode, template.getId(), "", "QUERY");
            insertSkippedCall(versionId, 1, platformCode, template.getId(), "", "ANALYZE");
            state.incrementProcessed();
            skippedCalls.addAndGet(2);
            int nextCompleted = completedCalls.addAndGet(2);
            updateBatch1ProgressRolling(versionId, nextCompleted, degradedPlatforms, false, lastWrittenCompleted);
            return;
        }

        PlatformCallContext ctx = new PlatformCallContext(
                versionId,
                1,
                platformCode,
                template.getId(),
                "",
                report.getBrandName(),
                operatorUserId,
                isManager
        );
        String renderedPrompt = promptTemplateRenderer.render(
                template.getPromptContent(),
                promptTemplateRenderer.variables(ctx, promptRenderReport)
        );

        ReuseDecision reuseDecision = reuseDecisionService.decide(ctx, reuseCache);
        if (reuseDecision == ReuseDecision.SKIP_ALL) {
            state.incrementProcessed();
            int nextCompleted = completedCalls.addAndGet(2);
            updateBatch1ProgressRolling(versionId, nextCompleted, degradedPlatforms, false, lastWrittenCompleted);
            maybeDegradeBatch1Platform(state, degradedPlatforms, degradedCount);
            return;
        }

        if (reuseDecision == ReuseDecision.REUSE_QUERY_ONLY) {
            ReuseSnapshot snapshot = reuseDecisionService.snapshotOf(ctx, reuseCache);
            PresaleAiCall reusedQueryCall = snapshot == null ? null : snapshot.querySuccessCall();
            if (reusedQueryCall != null && reusedQueryCall.getRawResponse() != null) {
                boolean interruptedInAnalyze = false;
                String analyzeRequestPrompt = buildAnalyzeRequestPrompt(ctx, renderedPrompt, reusedQueryCall.getRawResponse());
                try {
                    LlmCallResult analyzeResult = analyzeWithEvaluationModel(ctx, renderedPrompt, reusedQueryCall.getRawResponse(), Set.of());
                    PresaleAiCall analyzeCall = buildCall(
                            versionId, 1, platformCode, template.getId(), "",
                            "ANALYZE", reusedQueryCall.getId(), analyzeRequestPrompt, analyzeResult, null
                    );
                    PresaleAiPromptResult promptResult = buildPromptResultSuccess(
                            versionId, 1, platformCode, template.getId(), "",
                            reusedQueryCall.getId(), renderedPrompt, analyzeResult.rawResponse()
                    );
                    withDbWritePermit("presale.reuse.replace_failed_analyze", () ->
                            reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult));
                } catch (LlmInvokeException | AnalyzeParseException ex) {
                    PresaleAiCall analyzeCall = buildFailedCall(
                            versionId, 1, platformCode, template.getId(), "",
                            "ANALYZE", reusedQueryCall.getId(), analyzeRequestPrompt, ex.getMessage()
                    );
                    PresaleAiPromptResult promptResult = buildPromptResultAnalyzeFailed(
                            versionId, 1, platformCode, template.getId(), "",
                            reusedQueryCall.getId(), renderedPrompt
                    );
                    withDbWritePermit("presale.reuse.replace_failed_analyze", () ->
                            reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult));
                    interruptedInAnalyze = isInterruptedFailure(ex);
                }
                state.incrementProcessed();
                int nextCompleted = completedCalls.addAndGet(2);
                updateBatch1ProgressRolling(versionId, nextCompleted, degradedPlatforms, false, lastWrittenCompleted);
                maybeDegradeBatch1Platform(state, degradedPlatforms, degradedCount);
                if (interruptedInAnalyze) {
                    throw new BatchInterruptedException("batch1 interrupted during reused analyze");
                }
                return;
            }
        }

        PresaleAiCall queryCall;
        LlmCallResult queryResult;
        try {
            queryResult = llmInvoker.query(ctx, renderedPrompt);
            queryCall = insertCall(
                    versionId, 1, platformCode, template.getId(), "",
                    "QUERY", null, renderedPrompt, queryResult, null
            );
        } catch (LlmInvokeException ex) {
            insertFailedCall(versionId, 1, platformCode, template.getId(), "",
                    "QUERY", null, renderedPrompt, ex.getMessage());
            state.incrementProcessed();
            state.incrementFailed();
            int nextCompleted = completedCalls.addAndGet(2);
            updateBatch1ProgressRolling(versionId, nextCompleted, degradedPlatforms, false, lastWrittenCompleted);
            maybeDegradeBatch1Platform(state, degradedPlatforms, degradedCount);
            if (isInterruptedFailure(ex)) {
                throw new BatchInterruptedException("batch1 interrupted during query");
            }
            return;
        }

        try {
            String analyzeRequestPrompt = buildAnalyzeRequestPrompt(ctx, renderedPrompt, queryResult.rawResponse());
            LlmCallResult analyzeResult = analyzeWithEvaluationModel(ctx, renderedPrompt, queryResult.rawResponse(), Set.of());
            PresaleAiCall analyzeCall = insertCall(
                    versionId, 1, platformCode, template.getId(), "",
                    "ANALYZE", queryCall.getId(), analyzeRequestPrompt, analyzeResult, null
            );
            insertPromptResultSuccess(versionId, 1, platformCode, template.getId(), "",
                    queryCall.getId(), analyzeCall.getId(), renderedPrompt, analyzeResult.rawResponse());
        } catch (LlmInvokeException | AnalyzeParseException ex) {
            PresaleAiCall analyzeCall = insertFailedCall(versionId, 1, platformCode, template.getId(), "",
                    "ANALYZE", queryCall.getId(),
                    buildAnalyzeRequestPrompt(ctx, renderedPrompt, queryResult.rawResponse()), ex.getMessage());
            insertPromptResultAnalyzeFailed(versionId, 1, platformCode, template.getId(), "",
                    queryCall.getId(), analyzeCall.getId(), renderedPrompt);
        }

        state.incrementProcessed();
        int nextCompleted = completedCalls.addAndGet(2);
        updateBatch1ProgressRolling(versionId, nextCompleted, degradedPlatforms, false, lastWrittenCompleted);
        maybeDegradeBatch1Platform(state, degradedPlatforms, degradedCount);
    }

    PlatformBatchResult executePlatformBatch2(AiPlatformConfig platform,
                                              Long versionId,
                                              List<PresaleReportVersionPromptTemplate> templates,
                                              List<String> topCompetitors,
                                              Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache,
                                              Set<String> degradedPlatforms,
                                              AtomicInteger degradedCount,
                                              PresaleReport report,
                                              PresaleReport promptRenderReport,
                                              Long operatorUserId,
                                              boolean isManager,
                                              AtomicInteger completedCalls,
                                              AtomicInteger skippedCalls,
                                              AtomicInteger lastWrittenCompleted,
                                              int batch1Completed,
                                              int completedOffset,
                                              Semaphore reportPromptSemaphore) {
        String platformCode = platform.getPlatformCode();
        String competitorGroupName = CompetitorGroupKeyUtils.storageKey(topCompetitors);
        PlatformBatchState state = new PlatformBatchState(platformCode, templates.size());
        if (degradedPlatforms.contains(platformCode)) {
            state.markDegraded();
        }

        AtomicReference<Throwable> interruptedFailure = new AtomicReference<>();
        runPlatformPromptTasks(
                versionId,
                2,
                platformCode,
                platformConcurrency(platform),
                reportPromptSemaphore,
                templates,
                template -> processBatch2Template(
                        platformCode,
                        versionId,
                        template,
                        competitorGroupName,
                        reuseCache,
                        state,
                        degradedPlatforms,
                        degradedCount,
                        report,
                        promptRenderReport,
                        operatorUserId,
                        isManager,
                        completedCalls,
                        skippedCalls,
                        lastWrittenCompleted,
                        batch1Completed,
                        completedOffset
                ),
                interruptedFailure
        );
        if (interruptedFailure.get() != null) {
            throw new BatchInterruptedException("batch2 interrupted during question-level execution");
        }

        PlatformStatus status = state.isDegraded() ? PlatformStatus.DEGRADED : PlatformStatus.DONE;
        return new PlatformBatchResult(
                platformCode,
                status,
                state.processedPrompts(),
                state.failedPrompts(),
                state.processedPrompts() * 2,
                null
        );
    }

    private void processBatch2Template(String platformCode,
                                       Long versionId,
                                       PresaleReportVersionPromptTemplate template,
                                       String competitorGroupName,
                                       Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache,
                                       PlatformBatchState state,
                                       Set<String> degradedPlatforms,
                                       AtomicInteger degradedCount,
                                       PresaleReport report,
                                       PresaleReport promptRenderReport,
                                       Long operatorUserId,
                                       boolean isManager,
                                       AtomicInteger completedCalls,
                                       AtomicInteger skippedCalls,
                                       AtomicInteger lastWrittenCompleted,
                                       int batch1Completed,
                                       int completedOffset) {
        ensureGenerationStillRunning(versionId, "batch2");
        if (state.isDegraded()) {
            insertSkippedCall(versionId, 2, platformCode, template.getId(), competitorGroupName, "QUERY");
            insertSkippedCall(versionId, 2, platformCode, template.getId(), competitorGroupName, "ANALYZE");
            state.incrementProcessed();
            skippedCalls.addAndGet(2);
            int nextCompleted = completedCalls.addAndGet(2);
            updateBatch2ProgressRolling(versionId, nextCompleted, degradedPlatforms, false,
                    lastWrittenCompleted, batch1Completed, completedOffset);
            return;
        }

        PlatformCallContext ctx = new PlatformCallContext(
                versionId,
                2,
                platformCode,
                template.getId(),
                competitorGroupName,
                report.getBrandName(),
                operatorUserId,
                isManager
        );
        String renderedPrompt = promptTemplateRenderer.render(
                template.getPromptContent(),
                promptTemplateRenderer.variables(ctx, promptRenderReport)
        );

        ReuseDecision reuseDecision = reuseDecisionService.decide(ctx, reuseCache);
        if (reuseDecision == ReuseDecision.SKIP_ALL) {
            state.incrementProcessed();
            int nextCompleted = completedCalls.addAndGet(2);
            updateBatch2ProgressRolling(versionId, nextCompleted, degradedPlatforms, false,
                    lastWrittenCompleted, batch1Completed, completedOffset);
            maybeDegradeBatch2Platform(state, degradedPlatforms, degradedCount);
            return;
        }

        if (reuseDecision == ReuseDecision.REUSE_QUERY_ONLY) {
            ReuseSnapshot snapshot = reuseDecisionService.snapshotOf(ctx, reuseCache);
            PresaleAiCall reusedQueryCall = snapshot == null ? null : snapshot.querySuccessCall();
            if (reusedQueryCall != null && reusedQueryCall.getRawResponse() != null) {
                boolean interruptedInAnalyze = false;
                String analyzeRequestPrompt = buildAnalyzeRequestPrompt(ctx, renderedPrompt, reusedQueryCall.getRawResponse());
                try {
                    LlmCallResult analyzeResult = analyzeWithEvaluationModel(ctx, renderedPrompt, reusedQueryCall.getRawResponse(), degradedPlatforms);
                    PresaleAiCall analyzeCall = buildCall(
                            versionId, 2, platformCode, template.getId(), competitorGroupName,
                            "ANALYZE", reusedQueryCall.getId(), analyzeRequestPrompt, analyzeResult, null
                    );
                    PresaleAiPromptResult promptResult = buildPromptResultSuccess(
                            versionId, 2, platformCode, template.getId(), competitorGroupName,
                            reusedQueryCall.getId(), renderedPrompt, analyzeResult.rawResponse()
                    );
                    withDbWritePermit("presale.reuse.replace_failed_analyze", () ->
                            reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult));
                } catch (LlmInvokeException | AnalyzeParseException ex) {
                    PresaleAiCall analyzeCall = buildFailedCall(
                            versionId, 2, platformCode, template.getId(), competitorGroupName,
                            "ANALYZE", reusedQueryCall.getId(), analyzeRequestPrompt, ex.getMessage()
                    );
                    PresaleAiPromptResult promptResult = buildPromptResultAnalyzeFailed(
                            versionId, 2, platformCode, template.getId(), competitorGroupName,
                            reusedQueryCall.getId(), renderedPrompt
                    );
                    withDbWritePermit("presale.reuse.replace_failed_analyze", () ->
                            reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult));
                    interruptedInAnalyze = isInterruptedFailure(ex);
                }
                state.incrementProcessed();
                int nextCompleted = completedCalls.addAndGet(2);
                updateBatch2ProgressRolling(versionId, nextCompleted, degradedPlatforms, false,
                        lastWrittenCompleted, batch1Completed, completedOffset);
                maybeDegradeBatch2Platform(state, degradedPlatforms, degradedCount);
                if (interruptedInAnalyze) {
                    throw new BatchInterruptedException("batch2 interrupted during reused analyze");
                }
                return;
            }
        }

        PresaleAiCall queryCall;
        LlmCallResult queryResult;
        try {
            queryResult = llmInvoker.query(ctx, renderedPrompt);
            queryCall = insertCall(
                    versionId, 2, platformCode, template.getId(), competitorGroupName,
                    "QUERY", null, renderedPrompt, queryResult, null
            );
        } catch (LlmInvokeException ex) {
            insertFailedCall(versionId, 2, platformCode, template.getId(), competitorGroupName,
                    "QUERY", null, renderedPrompt, ex.getMessage());
            state.incrementProcessed();
            state.incrementFailed();
            int nextCompleted = completedCalls.addAndGet(2);
            updateBatch2ProgressRolling(versionId, nextCompleted, degradedPlatforms, false,
                    lastWrittenCompleted, batch1Completed, completedOffset);
            maybeDegradeBatch2Platform(state, degradedPlatforms, degradedCount);
            if (isInterruptedFailure(ex)) {
                throw new BatchInterruptedException("batch2 interrupted during query");
            }
            return;
        }

        try {
            String analyzeRequestPrompt = buildAnalyzeRequestPrompt(ctx, renderedPrompt, queryResult.rawResponse());
            LlmCallResult analyzeResult = analyzeWithEvaluationModel(ctx, renderedPrompt, queryResult.rawResponse(), degradedPlatforms);
            PresaleAiCall analyzeCall = insertCall(
                    versionId, 2, platformCode, template.getId(), competitorGroupName,
                    "ANALYZE", queryCall.getId(), analyzeRequestPrompt, analyzeResult, null
            );
            insertPromptResultSuccess(versionId, 2, platformCode, template.getId(), competitorGroupName,
                    queryCall.getId(), analyzeCall.getId(), renderedPrompt, analyzeResult.rawResponse());
        } catch (LlmInvokeException | AnalyzeParseException ex) {
            PresaleAiCall analyzeCall = insertFailedCall(versionId, 2, platformCode, template.getId(), competitorGroupName,
                    "ANALYZE", queryCall.getId(),
                    buildAnalyzeRequestPrompt(ctx, renderedPrompt, queryResult.rawResponse()), ex.getMessage());
            insertPromptResultAnalyzeFailed(versionId, 2, platformCode, template.getId(), competitorGroupName,
                    queryCall.getId(), analyzeCall.getId(), renderedPrompt);
        }

        state.incrementProcessed();
        int nextCompleted = completedCalls.addAndGet(2);
        updateBatch2ProgressRolling(versionId, nextCompleted, degradedPlatforms, false,
                lastWrittenCompleted, batch1Completed, completedOffset);
        maybeDegradeBatch2Platform(state, degradedPlatforms, degradedCount);
    }

    /**
     * 执行 batch2 · 平台间并发 · 跨 batch 延续阈值语义(D79)。
     *
     * 注意：degradedCount 起步值 = batch1 降级数，batch2 内阈值检查为“累计 >= 4”。
     * 如 batch1 已降 3 个，batch2 只要再降 1 个即触发阈值，后续平台不再提交。
     * 这与原串行版一致 —— 一次 report 全周期降级累计上限为 4。
     */
    private Batch2ExecutionResult executeBatch2(Long versionId,
                                                PresaleReport report,
                                                PresaleReport promptRenderReport,
                                                Long operatorUserId,
                                                boolean isManager,
                                                List<String> competitors,
                                                int competitorPromptCount,
                                                Set<String> batch1DegradedPlatforms) {
        enterStage(versionId, STAGE_BATCH2, "batch2 executing");

        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(PresalePlatformConfigQueries.presaleEnabledWrapper());
        List<PresaleReportVersionPromptTemplate> templates = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, 1)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getSortOrderInVersion)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getId)
        );

        String competitorGroupName = CompetitorGroupKeyUtils.storageKey(competitors);
        withDbWritePermit("presale.reuse.cleanup_legacy_batch2", () ->
                reusePersistenceService.cleanupLegacySingleCompetitorBatch2Rows(versionId, competitorGroupName));

        PresaleReportVersion current = versionMapper.selectById(versionId);
        int batch1Completed = current != null && current.getBatch1CompletedCalls() != null
                ? current.getBatch1CompletedCalls() : 0;
        int existingBatch2Completed = current != null && current.getBatch2CompletedCalls() != null
                ? current.getBatch2CompletedCalls() : 0;
        int completedOverall = current != null && current.getCompletedLlmCalls() != null
                ? current.getCompletedLlmCalls() : 0;
        int completedOffset = Math.max(0, completedOverall - batch1Completed - existingBatch2Completed);

        Set<String> batch1DegradedBefore = Set.copyOf(batch1DegradedPlatforms);
        Set<String> displayDegradedPlatforms = ConcurrentHashMap.newKeySet();
        displayDegradedPlatforms.addAll(batch1DegradedBefore);
        AtomicInteger degradedCount = new AtomicInteger(batch1DegradedBefore.size());
        AtomicInteger completedCalls = new AtomicInteger(existingBatch2Completed);
        AtomicInteger skippedCalls = new AtomicInteger(0);
        AtomicInteger lastWrittenCompleted = new AtomicInteger(existingBatch2Completed);
        AtomicInteger submittedCount = new AtomicInteger(0);
        AtomicReference<Throwable> interruptedFailure = new AtomicReference<>();
        Semaphore reportPromptSemaphore = new Semaphore(promptMaxConcurrencyPerReport());
        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache =
                reuseDecisionService.preloadByVersionAndBatch(versionId, 2);
        List<CompletableFuture<PlatformBatchResult>> futures = new ArrayList<>();
        long batchStartAt = System.currentTimeMillis();
        log.info("batch=2 versionId={} platformCode=- threadName={} starting platformCount={} degradedBefore={}",
                versionId, Thread.currentThread().getName(), platforms.size(), batch1DegradedBefore.size());

        for (AiPlatformConfig platform : platforms) {
            String platformCode = platform == null ? null : platform.getPlatformCode();
            if (platformCode == null || platformCode.isBlank()) {
                continue;
            }
            if (interruptedFailure.get() != null) {
                break;
            }
            try {
                submittedCount.incrementAndGet();
                CompletableFuture<PlatformBatchResult> future = CompletableFuture
                        .supplyAsync(() -> executePlatformBatch2(
                                platform,
                                versionId,
                                templates,
                                competitors,
                                reuseCache,
                                displayDegradedPlatforms,
                                degradedCount,
                                report,
                                promptRenderReport,
                                operatorUserId,
                                isManager,
                                completedCalls,
                                skippedCalls,
                                lastWrittenCompleted,
                                batch1Completed,
                                completedOffset,
                                reportPromptSemaphore
                        ), platformExecutor)
                        .handle((result, ex) -> {
                            if (ex == null) {
                                if (result != null) {
                                    log.info("batch=2 versionId={} platformCode={} threadName={} status={} progressDelta={}",
                                            versionId,
                                            result.platformCode(),
                                            Thread.currentThread().getName(),
                                            result.status(),
                                            result.progressDelta());
                                }
                                return result;
                            }
                            Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                                    ? ex.getCause()
                                    : ex;
                            if (isInterruptedFailure(cause)) {
                                interruptedFailure.compareAndSet(null, cause);
                            }
                            String failedCode = platform.getPlatformCode();
                            displayDegradedPlatforms.add(failedCode);
                            degradedCount.incrementAndGet();
                            return PlatformBatchResult.degraded(failedCode, cause);
                        });
                futures.add(future);
            } catch (RejectedExecutionException ex) {
                displayDegradedPlatforms.add(platformCode);
                degradedCount.incrementAndGet();
                futures.add(CompletableFuture.completedFuture(
                        new PlatformBatchResult(platformCode, PlatformStatus.SKIPPED, 0, 0, 0, ex)
                ));
                log.warn("batch=2 versionId={} platformCode={} threadName={} presalePlatformExecutor rejected queueSize={} activeCount={}",
                        versionId,
                        platformCode,
                        Thread.currentThread().getName(),
                        getPlatformExecutorQueueSize(),
                        getPlatformExecutorActiveCount());
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<PlatformBatchResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        int sumProgressDelta = results.stream().mapToInt(PlatformBatchResult::progressDelta).sum();
        if (sumProgressDelta != completedCalls.get() - existingBatch2Completed) {
            log.warn("batch=2 versionId={} platformCode=- threadName={} progressDelta mismatch sumProgressDelta={} completedCallsDelta={}",
                    versionId, Thread.currentThread().getName(), sumProgressDelta, completedCalls.get() - existingBatch2Completed);
        }

        int overRunCount = Math.max(0, batch1DegradedBefore.size() + submittedCount.get() - 4);
        Set<String> batch2DegradedPlatforms = ConcurrentHashMap.newKeySet();
        for (String code : displayDegradedPlatforms) {
            if (!batch1DegradedBefore.contains(code)) {
                batch2DegradedPlatforms.add(code);
            }
        }
        long degradedPlatformCount = results.stream().filter(r -> r.status() == PlatformStatus.DEGRADED).count();
        long skippedPlatformCount = results.stream().filter(r -> r.status() == PlatformStatus.SKIPPED).count();
        long succeededPlatformCount = results.stream().filter(r -> r.status() == PlatformStatus.DONE).count();
        log.info("batch=2 versionId={} platformCode=- threadName={} done succeeded={} degraded={} skipped={} skippedCallsWithinPlatform={} totalDurationMs={} overRunBy={} batch2NewDegradedCount={}",
                versionId,
                Thread.currentThread().getName(),
                succeededPlatformCount,
                degradedPlatformCount,
                skippedPlatformCount,
                skippedCalls.get(),
                System.currentTimeMillis() - batchStartAt,
                overRunCount,
                batch2DegradedPlatforms.size());

        if (interruptedFailure.get() != null) {
            log.warn("batch=2 versionId={} platformCode=- threadName={} interruptedFailureDetected={}", versionId,
                    Thread.currentThread().getName(), interruptedFailure.get().getClass().getSimpleName());
            throw new BatchInterruptedException("batch2 interrupted in async execution");
        }

        updateBatch2ProgressRolling(versionId, completedCalls.get(), displayDegradedPlatforms, true,
                lastWrittenCompleted, batch1Completed, completedOffset);
        if (degradedCount.get() >= 4) {
            log.info("batch=2 versionId={} platformCode=- threadName={} degradeThresholdReached degradedPlatforms={} overRunCount={}",
                    versionId,
                    Thread.currentThread().getName(),
                    displayDegradedPlatforms,
                    overRunCount);
            markTooManyDegradedFailed(versionId, displayDegradedPlatforms);
            return Batch2ExecutionResult.stop(
                    batch2DegradedPlatforms,
                    displayDegradedPlatforms,
                    overRunCount,
                    results
            );
        }

        return Batch2ExecutionResult.continuePipeline(
                batch2DegradedPlatforms,
                displayDegradedPlatforms,
                overRunCount,
                results
        );
    }

    private void updateBatch2ProgressRolling(Long versionId,
                                             int candidateBatch2Completed,
                                             Set<String> degradedPlatforms,
                                             boolean forceFlush,
                                             AtomicInteger lastWrittenCompleted,
                                             int batch1Completed,
                                             int completedOffset) {
        int current;
        while (true) {
            current = lastWrittenCompleted.get();
            if (candidateBatch2Completed <= current) {
                if (forceFlush) {
                    updateBatchProgress(versionId, batch1Completed, current, degradedPlatforms, true, completedOffset);
                }
                return;
            }
            if (lastWrittenCompleted.compareAndSet(current, candidateBatch2Completed)) {
                break;
            }
        }
        updateBatchProgress(versionId, batch1Completed, candidateBatch2Completed, degradedPlatforms,
                forceFlush, completedOffset);
    }

    private void maybeDegradeBatch2Platform(PlatformBatchState state,
                                            Set<String> degradedPlatforms,
                                            AtomicInteger degradedCount) {
        if (!state.isDegraded() && shouldDegrade(state)) {
            state.markDegraded();
            if (degradedPlatforms.add(state.platformCode)) {
                degradedCount.incrementAndGet();
            }
        }
    }

    private void markRunningForMock(Long versionId) {
        PresaleReportVersion current = versionMapper.selectById(versionId);
        int totalCalls = current == null || current.getTotalLlmCalls() == null
                ? 0 : current.getTotalLlmCalls();

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.RUNNING.name());
        update.setGenerationStage(STAGE_BATCH1);
        update.setCompletedLlmCalls(0);
        update.setTotalLlmCalls(totalCalls);
        update.setBatch1CompletedCalls(0);
        update.setBatch2CompletedCalls(0);
        update.setFailureReason(null);
        update.setFailureCategory(null);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        syncReportGenerationStatus(versionId, "GENERATING");
    }

    private void markRunning(Long versionId, int totalLlmCalls, int batch1TotalCalls) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.RUNNING.name());
        update.setGenerationStage(STAGE_BATCH1);
        update.setCompletedLlmCalls(0);
        update.setTotalLlmCalls(totalLlmCalls);
        update.setBatch1TotalCalls(batch1TotalCalls);
        update.setBatch1CompletedCalls(0);
        update.setBatch2TotalCalls(null);
        update.setBatch2CompletedCalls(0);
        update.setExtractedCompetitorCount(null);
        update.setFailureReason(null);
        update.setFailureCategory(null);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        syncReportGenerationStatus(versionId, "GENERATING");
    }

    private void enterStage(Long versionId, String stage, String note) {
        logPreviousStageDuration(versionId, stage);
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStage(stage);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        log.info("Presale generation stage entered, versionId={}, stage={}, note={}", versionId, stage, note);
    }

    private void updateAfterCompetitorExtract(Long versionId,
                                              int extractedCompetitorCount,
                                              int batch2TotalCalls,
                                              int totalCalls) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setExtractedCompetitorCount(extractedCompetitorCount);
        update.setBatch2TotalCalls(batch2TotalCalls);
        update.setTotalLlmCalls(totalCalls);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    private void markFailed(Long versionId, String failureCategory, String reason) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.FAILED.name());
        update.setGenerationStage(null);
        update.setFailureCategory(failureCategory);
        update.setFailureReason(truncateReason(reason));
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        syncReportGenerationStatus(versionId, PresaleGenerateStatus.FAILED.name());
        lastProgressUpdateAtByVersion.remove(versionId);
        logTerminalStageDuration(versionId, PresaleGenerateStatus.FAILED.name());
    }

    private void markDone(Long versionId) {
        PresaleReportVersion current = versionMapper.selectById(versionId);
        int totalCalls = current == null || current.getTotalLlmCalls() == null
                ? 0 : current.getTotalLlmCalls();

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.DONE.name());
        update.setGenerationStage(null);
        update.setCompletedLlmCalls(totalCalls);
        update.setBatch1CompletedCalls(current == null ? null : current.getBatch1TotalCalls());
        update.setBatch2CompletedCalls(current == null ? null : current.getBatch2TotalCalls());
        update.setFailureCategory(null);
        update.setFailureReason(null);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        syncReportGenerationStatus(versionId, PresaleGenerateStatus.DONE.name());
        lastProgressUpdateAtByVersion.remove(versionId);
        logTerminalStageDuration(versionId, PresaleGenerateStatus.DONE.name());
    }

    private void syncReportGenerationStatus(Long versionId, String reportStatus) {
        PresaleReportVersion version = versionMapper.selectById(versionId);
        if (version == null || version.getReportId() == null) {
            return;
        }
        PresaleReport report = new PresaleReport();
        report.setId(version.getReportId());
        report.setLatestVersionId(version.getId());
        report.setCurrentVersionNo(version.getVersionNo());
        report.setStatus(reportStatus);
        report.setUpdatedAt(LocalDateTime.now());
        updateReportById(report, "presale.report.update");
    }

    private void logPreviousStageDuration(Long versionId, String nextStage) {
        long now = System.currentTimeMillis();
        StageTiming previous = stageTimingByVersion.put(versionId, new StageTiming(nextStage, now));
        if (previous != null) {
            log.info("Presale generation stage completed, versionId={}, stage={}, durationMs={}, nextStage={}",
                    versionId, previous.stage(), now - previous.startedAtMs(), nextStage);
        }
    }

    private void logTerminalStageDuration(Long versionId, String terminalStatus) {
        long now = System.currentTimeMillis();
        StageTiming previous = stageTimingByVersion.remove(versionId);
        if (previous != null) {
            log.info("Presale generation stage completed, versionId={}, stage={}, durationMs={}, terminalStatus={}",
                    versionId, previous.stage(), now - previous.startedAtMs(), terminalStatus);
        }
    }

    private void writeRawSnapshotJson(Long versionId, String rawJson) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setRawSnapshotJson(rawJson);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    private void writeComputedSnapshotJson(Long versionId, String computedJson) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setComputedSnapshotJson(computedJson);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    private void writeEditableContentJson(Long versionId, String editableJson) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setEditableContentJson(editableJson);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    @Async("presaleGenerateExecutor")
    public void triggerGenerate(Long versionId) {
        triggerGenerate(versionId, null, false);
    }

    private FixturePayload loadFixturePayload() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(mockFixturePath)) {
            if (is == null) {
                throw new IOException("Mock fixture not found on classpath: " + mockFixturePath);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }

            JsonNode root = objectMapper.readTree(content);
            JsonNode effective = (root != null && root.has("input") && root.get("input").isObject())
                    ? root.get("input")
                    : root;

            JsonNode rawNode = (effective != null && effective.has("raw") && effective.get("raw").isObject())
                    ? effective.get("raw")
                    : effective;
            String rawJson = objectMapper.writeValueAsString(rawNode);

            JsonNode computedNode = (effective != null && effective.has("computed") && effective.get("computed").isObject())
                    ? effective.get("computed")
                    : objectMapper.createObjectNode();
            String computedJson = objectMapper.writeValueAsString(computedNode);

            JsonNode editableNode = (effective != null && effective.has("editable") && effective.get("editable").isObject())
                    ? effective.get("editable")
                    : objectMapper.createObjectNode();
            String editableJson = objectMapper.writeValueAsString(editableNode);

            return new FixturePayload(rawJson, computedJson, editableJson);
        }
    }

    private int countWhitelistedPlatforms() {
        Long count = aiPlatformConfigMapper.selectCount(PresalePlatformConfigQueries.presaleEnabledWrapper());
        return count == null ? 0 : count.intValue();
    }

    private List<String> loadWhitelistedPlatformCodes() {
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(PresalePlatformConfigQueries.presaleEnabledWrapper());
        if (platforms == null) {
            return List.of();
        }
        return platforms.stream()
                .map(AiPlatformConfig::getPlatformCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
    }

    private int countPromptTemplates(Long versionId, int hasCompetitorVar) {
        Long count = versionPromptTemplateMapper.selectCount(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, hasCompetitorVar)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countPromptTemplates(Long versionId, String category, int hasCompetitorVar) {
        Long count = versionPromptTemplateMapper.selectCount(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .eq(PresaleReportVersionPromptTemplate::getCategory, category)
                        .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, hasCompetitorVar)
        );
        return count == null ? 0 : count.intValue();
    }

    private Runnable judgeProgressCallback(Long versionId, int startCompletedCalls, int totalCalls) {
        AtomicInteger completedCalls = new AtomicInteger(startCompletedCalls);
        Object writeLock = new Object();
        return () -> {
            int nextCompleted = Math.min(totalCalls, completedCalls.incrementAndGet());
            synchronized (writeLock) {
                updateCompletedLlmCalls(versionId, nextCompleted);
            }
        };
    }

    private void updateCompletedLlmCalls(Long versionId, int completedCalls) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setCompletedLlmCalls(completedCalls);
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    private void updateBatchProgress(Long versionId,
                                     int batch1CompletedCalls,
                                     int batch2CompletedCalls,
                                     Set<String> degradedPlatforms) {
        updateBatchProgress(versionId, batch1CompletedCalls, batch2CompletedCalls, degradedPlatforms, false);
    }

    private void updateBatchProgress(Long versionId,
                                     int batch1CompletedCalls,
                                     int batch2CompletedCalls,
                                     Set<String> degradedPlatforms,
                                     boolean forceFlush) {
        updateBatchProgress(versionId, batch1CompletedCalls, batch2CompletedCalls, degradedPlatforms, forceFlush, 0);
    }

    private void updateBatchProgress(Long versionId,
                                     int batch1CompletedCalls,
                                     int batch2CompletedCalls,
                                     Set<String> degradedPlatforms,
                                     boolean forceFlush,
                                     int completedOffset) {
        AtomicLong lastUpdateAt = lastProgressUpdateAtByVersion.computeIfAbsent(versionId, v -> new AtomicLong(0L));
        if (!forceFlush) {
            long now = System.currentTimeMillis();
            long lastAt = lastUpdateAt.get();
            if (now - lastAt < PROGRESS_UPDATE_THROTTLE_MS) {
                return;
            }
            if (!lastUpdateAt.compareAndSet(lastAt, now)) {
                return;
            }
        } else {
            lastUpdateAt.set(System.currentTimeMillis());
        }

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setBatch1CompletedCalls(batch1CompletedCalls);
        update.setBatch2CompletedCalls(batch2CompletedCalls);
        update.setCompletedLlmCalls(batch1CompletedCalls + batch2CompletedCalls + completedOffset);
        update.setIsDegraded(!degradedPlatforms.isEmpty());
        update.setDegradedPlatforms(toJsonArray(degradedPlatforms));
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    private void updateBatch1ProgressRolling(Long versionId,
                                             int candidateCompleted,
                                             Set<String> degradedPlatforms,
                                             boolean forceFlush,
                                             AtomicInteger lastWrittenCompleted) {
        int current;
        while (true) {
            current = lastWrittenCompleted.get();
            if (candidateCompleted <= current) {
                if (forceFlush) {
                    updateBatchProgress(versionId, current, 0, degradedPlatforms, true);
                }
                return;
            }
            if (lastWrittenCompleted.compareAndSet(current, candidateCompleted)) {
                break;
            }
        }
        updateBatchProgress(versionId, candidateCompleted, 0, degradedPlatforms, forceFlush);
    }

    private void maybeDegradeBatch1Platform(PlatformBatchState state,
                                            Set<String> degradedPlatforms,
                                            AtomicInteger degradedCount) {
        if (!state.isDegraded() && shouldDegrade(state)) {
            state.markDegraded();
            if (degradedPlatforms.add(state.platformCode)) {
                degradedCount.incrementAndGet();
            }
        }
    }

    private PresaleCompetitorNormalizationService.NormalizationOutcome extractTopCompetitorsFromBatch1(
            Long versionId,
            String brandName,
            List<String> selfBrandNames,
            Long operatorUserId,
            boolean isManager) {
        if (competitorNormalizationService == null) {
            List<PresaleCompetitorAggregator.ExtractedCompetitor> competitors =
                    extractTopCompetitorsFromBatch1(versionId, selfBrandNames).stream()
                            .map(name -> new PresaleCompetitorAggregator.ExtractedCompetitor(name, 0, List.of(name)))
                            .toList();
            return new PresaleCompetitorNormalizationService.NormalizationOutcome(competitors, false);
        }
        List<PresaleCompetitorAggregator.RawCompetitorMention> rawTop =
                competitorAggregator.extractTopRawCompetitorMentions(versionId, selfBrandNames, 10);
        if ((rawTop == null || rawTop.isEmpty()) && competitorAggregator != null) {
            List<PresaleCompetitorAggregator.ExtractedCompetitor> competitors =
                    extractTopCompetitorsFromBatch1(versionId, selfBrandNames).stream()
                            .map(name -> new PresaleCompetitorAggregator.ExtractedCompetitor(name, 0, List.of(name)))
                            .toList();
            return new PresaleCompetitorNormalizationService.NormalizationOutcome(competitors, false);
        }
        return competitorNormalizationService.normalize(versionId, brandName, rawTop, operatorUserId, isManager);
    }

    private PresaleCompetitorNormalizationService.NormalizationOutcome specifiedCompetitorsFromBatch1Stats(
            Long versionId,
            List<String> selfBrandNames,
            List<String> specifiedCompetitors) {
        PresaleCompetitorAggregator.Batch1MentionStats stats =
                competitorAggregator.aggregateBatch1MentionStats(versionId, selfBrandNames);
        List<PresaleCompetitorAggregator.ExtractedCompetitor> competitors = specifiedCompetitors.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> {
                    String displayName = name.trim();
                    int mentionCount = stats.countByNormalized()
                            .getOrDefault(competitorAggregator.normalizeName(displayName), 0);
                    return new PresaleCompetitorAggregator.ExtractedCompetitor(
                            displayName, mentionCount, List.of(displayName));
                })
                .toList();
        return new PresaleCompetitorNormalizationService.NormalizationOutcome(competitors, false);
    }

    private List<String> extractTopCompetitorsFromBatch1(Long versionId, List<String> selfBrandNames) {
        List<String> competitors = competitorAggregator.extractTopCompetitorsFromBatch1(versionId, selfBrandNames);
        return competitors == null ? List.of() : competitors;
    }

    private List<String> parseSpecifiedCompetitors(PresaleReport report) {
        if (report == null || report.getSpecifiedCompetitors() == null || report.getSpecifiedCompetitors().isBlank()) {
            return List.of();
        }
        return parseJsonStringArray(report.getSpecifiedCompetitors(), "specified_competitors", report.getId());
    }

    private List<String> parseBrandFormerNames(PresaleReport report) {
        return report == null
                ? List.of()
                : parseJsonStringArray(report.getBrandFormerNames(), "brand_former_names", report.getId());
    }

    private List<String> parseJsonStringArray(String json, String fieldName, Long reportId) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    out.add(item.asText().trim());
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("ignore invalid {} json, reportId={}", fieldName, reportId, ex);
            return List.of();
        }
    }

    private List<String> selfBrandNames(PresaleReport report) {
        if (report == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (report.getBrandName() != null && !report.getBrandName().isBlank()) {
            out.add(report.getBrandName().trim());
        }
        out.addAll(parseBrandFormerNames(report));
        return out;
    }

    private String assembleRawSnapshot(Long versionId,
                                       PresaleReport report,
                                       PresaleReportVersion version,
                                       Set<String> degradedPlatforms,
                                       List<PresaleCompetitorAggregator.ExtractedCompetitor> extractedCompetitorStats,
                                       List<String> extractedCompetitors) {
        String rawJson = rawSnapshotAssembler.assembleWithCompetitorStats(
                versionId, report, version, degradedPlatforms, extractedCompetitorStats);
        if (rawJson != null) {
            return rawJson;
        }
        return rawSnapshotAssembler.assemble(versionId, report, version, degradedPlatforms, extractedCompetitors);
    }

    private String normalizeName(String input) {
        return competitorAggregator.normalizeName(input);
    }

    private void markCompetitorExtractEmpty(Long versionId) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setFailureCategory(FAILURE_CATEGORY_COMPETITOR_EXTRACT_EMPTY);
        update.setFailureReason("batch1 yielded 0 usable competitors, skip BATCH2");
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
    }

    private String buildAnalyzeRequestPrompt(PlatformCallContext ctx, String originalPrompt, String queryAnswer) {
        return AnalyzePromptTemplates.renderUserPrompt(
                originalPrompt,
                queryAnswer,
                ctx == null ? null : ctx.brandName()
        );
    }

    private LlmCallResult analyzeWithEvaluationModel(PlatformCallContext sourceCtx,
                                                     String originalPrompt,
                                                     String queryAnswer)
            throws LlmInvokeException, AnalyzeParseException {
        return analyzeWithEvaluationModel(sourceCtx, originalPrompt, queryAnswer, Set.of());
    }

    private LlmCallResult analyzeWithEvaluationModel(PlatformCallContext sourceCtx,
                                                     String originalPrompt,
                                                     String queryAnswer,
                                                     Set<String> excludedEvaluationPlatforms)
            throws LlmInvokeException, AnalyzeParseException {
        LlmPermitUnavailableException lastBusy = null;
        Exception lastEvaluationFailure = null;
        Set<String> excludedPlatforms = excludedEvaluationPlatforms == null ? Set.of() : excludedEvaluationPlatforms;
        List<PlatformCallContext> candidates = evaluationModelRouter.routeContexts(sourceCtx).stream()
                .filter(candidate -> candidate != null && !excludedPlatforms.contains(candidate.platformCode()))
                .toList();
        if (candidates.isEmpty()) {
            throw new LlmInvokeException("No presale evaluation model enabled after excluding degraded platforms");
        }
        for (int attempt = 1; attempt <= EVALUATION_MODEL_BUSY_ATTEMPTS; attempt++) {
            for (PlatformCallContext candidate : candidates) {
                try {
                    return llmInvoker.analyze(candidate, originalPrompt, queryAnswer);
                } catch (LlmPermitUnavailableException ex) {
                    lastBusy = ex;
                    log.debug("presale analyze evaluation model busy, versionId={}, platformCode={}, attempt={}/{}",
                            sourceCtx.versionId(), candidate.platformCode(), attempt, EVALUATION_MODEL_BUSY_ATTEMPTS);
                } catch (LlmInvokeException | AnalyzeParseException ex) {
                    lastEvaluationFailure = ex;
                    log.warn("presale analyze evaluation model failed, versionId={}, sourcePlatform={}, evaluationPlatform={}, attempt={}/{}, reason={}",
                            sourceCtx.versionId(),
                            sourceCtx.platformCode(),
                            candidate.platformCode(),
                            attempt,
                            EVALUATION_MODEL_BUSY_ATTEMPTS,
                            ex.getMessage());
                }
            }
            if (attempt < EVALUATION_MODEL_BUSY_ATTEMPTS) {
                try {
                    TimeUnit.MILLISECONDS.sleep(EVALUATION_MODEL_BUSY_RETRY_INTERVAL_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new BatchInterruptedException("Interrupted while retrying presale evaluation model");
                }
            }
        }
        for (int attempt = 1; attempt <= EVALUATION_MODEL_BUSY_COMPENSATION_ATTEMPTS; attempt++) {
            sleepBeforeEvaluationCompensation(sourceCtx, attempt);
            for (PlatformCallContext candidate : candidates) {
                try {
                    return llmInvoker.analyze(candidate, originalPrompt, queryAnswer);
                } catch (LlmPermitUnavailableException ex) {
                    lastBusy = ex;
                    log.debug("presale analyze evaluation model compensation busy, versionId={}, platformCode={}, attempt={}/{}",
                            sourceCtx.versionId(), candidate.platformCode(), attempt,
                            EVALUATION_MODEL_BUSY_COMPENSATION_ATTEMPTS);
                } catch (LlmInvokeException | AnalyzeParseException ex) {
                    lastEvaluationFailure = ex;
                    log.warn("presale analyze evaluation model compensation failed, versionId={}, sourcePlatform={}, evaluationPlatform={}, attempt={}/{}, reason={}",
                            sourceCtx.versionId(),
                            sourceCtx.platformCode(),
                            candidate.platformCode(),
                            attempt,
                            EVALUATION_MODEL_BUSY_COMPENSATION_ATTEMPTS,
                            ex.getMessage());
                }
            }
        }
        if (lastEvaluationFailure instanceof AnalyzeParseException ex) {
            throw ex;
        }
        if (lastEvaluationFailure instanceof LlmInvokeException ex) {
            throw new LlmInvokeException("All presale evaluation models failed after bounded retries", ex);
        }
        throw new LlmInvokeException("All presale evaluation models are busy after bounded retries", lastBusy);
    }

    private void sleepBeforeEvaluationCompensation(PlatformCallContext sourceCtx, int attempt) {
        long sleepMs = EVALUATION_MODEL_BUSY_COMPENSATION_INTERVAL_MS * attempt;
        try {
            TimeUnit.MILLISECONDS.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BatchInterruptedException("Interrupted while compensating presale evaluation model busy state");
        }
        log.debug("presale analyze evaluation model compensation retry, versionId={}, sourcePlatform={}, sleepMs={}",
                sourceCtx.versionId(), sourceCtx.platformCode(), sleepMs);
    }

    private void markTooManyDegradedFailed(Long versionId, Set<String> degradedPlatforms) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.FAILED.name());
        update.setGenerationStage(null);
        update.setFailureCategory(FAILURE_CATEGORY_TOO_MANY_DEGRADED);
        update.setIsDegraded(true);
        update.setDegradedPlatforms(toJsonArray(degradedPlatforms));
        update.setFailureReason(truncateReason(FAILURE_CATEGORY_TOO_MANY_DEGRADED));
        update.setUpdatedAt(LocalDateTime.now());
        updateVersionById(update, "presale.version.update");
        syncReportGenerationStatus(versionId, PresaleGenerateStatus.FAILED.name());
        lastProgressUpdateAtByVersion.remove(versionId);
    }

    private PresaleAiCall insertCall(Long versionId,
                                     int batchNo,
                                     String platformCode,
                                     Long promptTemplateId,
                                     String competitorName,
                                     String stage,
                                     Long parentCallId,
                                     String requestPromptContent,
                                     LlmCallResult result,
                                     String failureReason) {
        PresaleAiCall row = buildCall(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, stage,
                parentCallId, requestPromptContent, result, failureReason
        );
        insertAiCall(row, "presale.ai_call.insert");
        return row;
    }

    private PresaleAiCall insertFailedCall(Long versionId,
                                           int batchNo,
                                           String platformCode,
                                            Long promptTemplateId,
                                            String competitorName,
                                            String stage,
                                            Long parentCallId,
                                            String requestPromptContent,
                                            String failureReason) {
        PresaleAiCall row = buildFailedCall(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, stage,
                parentCallId, requestPromptContent, failureReason
        );
        insertAiCall(row, "presale.ai_call.insert");
        return row;
    }

    private PresaleAiCall buildCall(Long versionId,
                                    int batchNo,
                                    String platformCode,
                                    Long promptTemplateId,
                                     String competitorName,
                                     String stage,
                                     Long parentCallId,
                                     String requestPromptContent,
                                     LlmCallResult result,
                                     String failureReason) {
        PresaleAiCall row = new PresaleAiCall();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPlatformCodeSnapshot(result.platformCode());
        row.setPlatformNameSnapshot(result.platformName());
        row.setModelIdSnapshot(result.modelId());
        row.setModelNameSnapshot(result.modelName());
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setStage(stage);
        row.setParentCallId(parentCallId);
        row.setRequestPromptContent(requestPromptContent);
        row.setCallStatus(result.callStatus().name());
        row.setRetryCount(result.retryCount() == null ? 0 : result.retryCount());
        row.setRawResponse(result.rawResponse());
        row.setFailureReason(failureReason);
        row.setPromptTokens(result.promptTokens());
        row.setCompletionTokens(result.completionTokens());
        row.setDurationMs(toIntDuration(result.durationMs()));
        return row;
    }

    private PresaleAiCall buildFailedCall(Long versionId,
                                          int batchNo,
                                          String platformCode,
                                          Long promptTemplateId,
                                           String competitorName,
                                           String stage,
                                           Long parentCallId,
                                           String requestPromptContent,
                                           String failureReason) {
        PresaleAiCall row = new PresaleAiCall();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        CallModelSnapshot snapshot = resolveCurrentModelSnapshot(platformCode);
        row.setPlatformCodeSnapshot(snapshot.platformCode());
        row.setPlatformNameSnapshot(snapshot.platformName());
        row.setModelIdSnapshot(snapshot.modelId());
        row.setModelNameSnapshot(snapshot.modelName());
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setStage(stage);
        row.setParentCallId(parentCallId);
        row.setRequestPromptContent(requestPromptContent);
        row.setCallStatus(CallStatus.FAILED.name());
        row.setRetryCount(0);
        row.setRawResponse(null);
        row.setFailureReason(truncateReason(failureReason));
        row.setPromptTokens(null);
        row.setCompletionTokens(null);
        row.setDurationMs(null);
        return row;
    }

    private void insertSkippedCall(Long versionId,
                                   int batchNo,
                                   String platformCode,
                                   Long promptTemplateId,
                                   String competitorName,
                                   String stage) {
        PresaleAiCall row = new PresaleAiCall();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setStage(stage);
        row.setParentCallId(null);
        row.setRequestPromptContent(null);
        row.setCallStatus(CallStatus.SKIPPED_DEGRADED.name());
        row.setRetryCount(0);
        row.setRawResponse(null);
        row.setFailureReason("SKIPPED_DEGRADED");
        row.setPromptTokens(null);
        row.setCompletionTokens(null);
        row.setDurationMs(null);
        insertAiCall(row, "presale.ai_call.insert");
    }

    private CallModelSnapshot resolveCurrentModelSnapshot(String platformCode) {
        if (platformCode == null || platformCode.isBlank()) {
            return new CallModelSnapshot(platformCode, null, null, null);
        }
        return modelSnapshotByPlatformCode.computeIfAbsent(platformCode, this::loadCurrentModelSnapshot);
    }

    private CallModelSnapshot loadCurrentModelSnapshot(String platformCode) {
        AiPlatformConfig config = aiPlatformConfigMapper.selectOne(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getPlatformCode, platformCode)
                        .last("LIMIT 1")
        );
        if (config == null) {
            return new CallModelSnapshot(platformCode, null, null, null);
        }
        String modelId = hasText(config.getLowModelId()) ? config.getLowModelId().trim() : config.getModelId();
        String platformName = hasText(config.getPlatformName()) ? config.getPlatformName().trim() : config.getPlatformCode();
        String modelName = buildModelDisplayName(platformName, hasText(config.getModelName()) ? config.getModelName().trim() : modelId);
        return new CallModelSnapshot(config.getPlatformCode(), platformName, modelId, modelName);
    }

    private String buildModelDisplayName(String platformName, String displayModel) {
        if (!hasText(platformName)) {
            return displayModel;
        }
        if (!hasText(displayModel)) {
            return platformName;
        }
        return platformName + " / " + displayModel;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record CallModelSnapshot(String platformCode, String platformName, String modelId, String modelName) {
    }

    private void insertPromptResultAnalyzeFailed(Long versionId,
                                                 int batchNo,
                                                 String platformCode,
                                                 Long promptTemplateId,
                                                 String competitorName,
                                                 Long queryCallId,
                                                 Long analyzeCallId,
                                                 String requestPromptContent) {
        PresaleAiPromptResult row = buildPromptResultAnalyzeFailed(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, queryCallId, requestPromptContent
        );
        row.setAnalyzeCallId(analyzeCallId);
        try {
            insertAiPromptResult(row, "presale.ai_prompt_result.insert");
        } catch (DuplicateKeyException ex) {
            log.warn("batch={} versionId={} platformCode={} threadName={} duplicate key on presale_ai_prompt_result templateId={} competitorName={}",
                    batchNo, versionId, platformCode, Thread.currentThread().getName(), promptTemplateId, competitorName, ex);
        }
    }

    private void insertPromptResultSuccess(Long versionId,
                                           int batchNo,
                                           String platformCode,
                                           Long promptTemplateId,
                                           String competitorName,
                                           Long queryCallId,
                                           Long analyzeCallId,
                                           String requestPromptContent,
                                           String analyzeJson) throws AnalyzeParseException {
        PresaleAiPromptResult row = buildPromptResultSuccess(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, queryCallId, requestPromptContent, analyzeJson
        );
        row.setAnalyzeCallId(analyzeCallId);
        try {
            insertAiPromptResult(row, "presale.ai_prompt_result.insert");
        } catch (DuplicateKeyException ex) {
            log.warn("batch={} versionId={} platformCode={} threadName={} duplicate key on presale_ai_prompt_result templateId={} competitorName={}",
                    batchNo, versionId, platformCode, Thread.currentThread().getName(), promptTemplateId, competitorName, ex);
        }
    }

    private PresaleAiPromptResult buildPromptResultAnalyzeFailed(Long versionId,
                                                                 int batchNo,
                                                                 String platformCode,
                                                                 Long promptTemplateId,
                                                                 String competitorName,
                                                                 Long queryCallId,
                                                                 String requestPromptContent) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setQueryCallId(queryCallId);
        row.setAnalyzeCallId(null);
        row.setRequestPromptContent(requestPromptContent);
        row.setIsMentioned(null);
        row.setRanking(null);
        row.setSentiment(null);
        row.setMentionedCompetitors(null);
        row.setSceneAdvantages(null);
        row.setTopKeywordsJson("[]");
        row.setNegativeEvidenceJson("{}");
        return row;
    }

    private PresaleAiPromptResult buildPromptResultSuccess(Long versionId,
                                                           int batchNo,
                                                           String platformCode,
                                                           Long promptTemplateId,
                                                           String competitorName,
                                                           Long queryCallId,
                                                           String requestPromptContent,
                                                           String analyzeJson) throws AnalyzeParseException {
        try {
            JsonNode node = objectMapper.readTree(analyzeJson);
            PresaleAiPromptResult row = new PresaleAiPromptResult();
            row.setVersionId(versionId);
            row.setBatchNo(batchNo);
            row.setPlatformCode(platformCode);
            row.setPromptTemplateId(promptTemplateId);
            row.setCompetitorName(competitorName);
            row.setQueryCallId(queryCallId);
            row.setAnalyzeCallId(null);
            row.setRequestPromptContent(requestPromptContent);
            row.setIsMentioned(node.get("is_mentioned").asBoolean() ? 1 : 0);
            row.setRanking(node.get("ranking") == null || node.get("ranking").isNull() ? null : node.get("ranking").asInt());
            row.setSentiment(node.get("sentiment").asText());
            row.setMentionedCompetitors(objectMapper.writeValueAsString(node.get("mentioned_competitors")));
            row.setSceneAdvantages(objectMapper.writeValueAsString(node.get("scene_advantages")));
            row.setTopKeywordsJson(writeTopKeywords(node.get("top_keywords")));
            row.setNegativeEvidenceJson(writeNegativeEvidence(node.get("negative_evidence")));
            return row;
        } catch (Exception ex) {
            throw new AnalyzeParseException("failed to persist analyze success payload", ex);
        }
    }

    private String writeTopKeywords(JsonNode topKeywordsNode) throws JsonProcessingException {
        if (topKeywordsNode == null || !topKeywordsNode.isArray()) {
            return "[]";
        }
        List<JsonNode> limited = new ArrayList<>();
        for (int i = 0; i < topKeywordsNode.size() && i < ANALYZE_TOP_KEYWORDS_MAX; i++) {
            limited.add(topKeywordsNode.get(i));
        }
        return objectMapper.writeValueAsString(limited);
    }

    private String writeNegativeEvidence(JsonNode negativeEvidenceNode) throws JsonProcessingException {
        if (negativeEvidenceNode == null || !negativeEvidenceNode.isObject()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(negativeEvidenceNode);
    }

    private ProgressCounts applyCompletedForOnePromptPair(Long versionId,
                                                          ProgressCounts counts,
                                                          int batchNo,
                                                          Set<String> degradedPlatforms,
                                                          boolean forceFlush) {
        ProgressCounts next = batchNo == 1
                ? new ProgressCounts(counts.batch1CompletedCalls() + 2, counts.batch2CompletedCalls())
                : new ProgressCounts(counts.batch1CompletedCalls(), counts.batch2CompletedCalls() + 2);
        updateBatchProgress(versionId, next.batch1CompletedCalls(), next.batch2CompletedCalls(), degradedPlatforms, forceFlush);
        return next;
    }

    /**
     * spec §7.4: 平台仅在"已执行 prompt 达到总量一半"后开始判降级,
     * 且判定条件为"已执行中的失败率 >= 50%"。
     */
    private boolean shouldDegrade(PlatformBatchState state) {
        int processedPrompts = state.processedPrompts();
        if (processedPrompts * 2 < state.totalPrompts) {
            return false;
        }
        return state.failedPrompts() * 2 >= processedPrompts;
    }

    private int platformConcurrency(AiPlatformConfig platform) {
        if (platform == null || platform.getConcurrencyLimit() == null || platform.getConcurrencyLimit() <= 0) {
            return 1;
        }
        return Math.max(1, platform.getConcurrencyLimit());
    }

    private int promptMaxConcurrencyPerReport() {
        return Math.max(1, promptMaxConcurrencyPerReport);
    }

    private void runPlatformPromptTasks(Long versionId,
                                        int batchNo,
                                        String platformCode,
                                        int concurrency,
                                        Semaphore reportPromptSemaphore,
                                        List<PresaleReportVersionPromptTemplate> templates,
                                        PromptTask task,
                                        AtomicReference<Throwable> interruptedFailure) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        int poolSize = Math.max(1, Math.min(concurrency, templates.size()));
        ExecutorService executor = Executors.newFixedThreadPool(
                poolSize,
                new PresalePromptThreadFactory(versionId, batchNo, platformCode)
        );
        try {
            List<CompletableFuture<Void>> futures = templates.stream()
                    .map(template -> CompletableFuture.runAsync(() -> {
                        if (interruptedFailure.get() != null) {
                            return;
                        }
                        boolean acquired = false;
                        try {
                            reportPromptSemaphore.acquire();
                            acquired = true;
                            task.run(template);
                        } catch (BatchInterruptedException ex) {
                            interruptedFailure.compareAndSet(null, ex);
                            throw ex;
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            BatchInterruptedException interrupted =
                                    new BatchInterruptedException("prompt task interrupted while waiting for report concurrency permit");
                            interruptedFailure.compareAndSet(null, interrupted);
                            throw interrupted;
                        } finally {
                            if (acquired) {
                                reportPromptSemaphore.release();
                            }
                        }
                    }, executor))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (isInterruptedFailure(cause)) {
                interruptedFailure.compareAndSet(null, cause);
                return;
            }
            throw ex;
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("presale prompt executor did not stop quickly, versionId={}, batchNo={}, platformCode={}",
                            versionId, batchNo, platformCode);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                interruptedFailure.compareAndSet(null, ex);
            }
        }
    }

    private void ensureGenerationStillRunning(Long versionId, String stage) {
        if (cancellationRegistry.isCanceled(versionId)) {
            throw new BatchInterruptedException(stage + " canceled: cancellation requested");
        }
        PresaleReportVersion current = versionMapper.selectById(versionId);
        if (current == null) {
            return;
        }
        String status = current.getGenerationStatus();
        if (status != null && !PresaleGenerateStatus.RUNNING.name().equals(status)) {
            throw new BatchInterruptedException(stage + " canceled: generation status is " + status);
        }
    }

    private boolean isGenerationActive(Long versionId) {
        PresaleReportVersion current = versionMapper.selectById(versionId);
        if (current == null || cancellationRegistry.isCanceled(versionId)) {
            return false;
        }
        String status = current.getGenerationStatus();
        return status == null || PresaleGenerateStatus.RUNNING.name().equals(status);
    }

    private Integer toIntDuration(Long durationMs) {
        if (durationMs == null) {
            return null;
        }
        if (durationMs <= 0) {
            log.warn("Non-positive duration from invoker, clamp to 1ms, durationMs={}", durationMs);
            return 1;
        }
        if (durationMs > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return durationMs.intValue();
    }

    private String toJsonArray(Set<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Set<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return Set.of();
            }
            Set<String> values = new LinkedHashSet<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    String text = item.asText();
                    if (text != null && !text.isBlank()) {
                        values.add(text);
                    }
                }
            }
            return values;
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private boolean resolveAllowSyntheticFallback() {
        return mockEnabled ? allowSyntheticFallbackMock : allowSyntheticFallbackReal;
    }

    /**
     * 判定异常是否由中断引起,归类为 INTERRUPTED 而非 UNEXPECTED_ERROR。
     *
     * 判定优先级(任一满足即归 INTERRUPTED):
     * 1. 异常类型是 InterruptedException 或 BatchInterruptedException
     * 2. 当前线程的中断标志为 true(读而不消费)
     * 3. cause 链内(最大深度 10)存在 InterruptedException
     *
     * 注意条件 2 的副作用:如果线程被中断后抛出无关 RuntimeException(例如 NPE),
     * 也会归为 INTERRUPTED。这是有意设计——中断信号是强优先级,用户主动中断的意图
     * 优先于无关异常。排障时看日志的 exception type,不只看 failure_category。
     */
    private boolean isInterruptedFailure(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof InterruptedException) {
            return true;
        }
        if (throwable instanceof BatchInterruptedException) {
            return true;
        }
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable current = throwable.getCause();
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record FixturePayload(String rawJson, String computedJson, String editableJson) {
    }

    private record StageTiming(String stage, long startedAtMs) {
    }

    private static final class PreflightResult {
        private final boolean success;
        private final String failureReason;
        private final int platformCount;
        private final int competitorPromptCount;
        private final int batch1TotalCalls;
        private final int cognitiveJudgeTotalCalls;
        private final int comparisonJudgeTotalCalls;
        private final int totalUpperBoundCalls;

        private PreflightResult(boolean success,
                                String failureReason,
                                int platformCount,
                                int competitorPromptCount,
                                int batch1TotalCalls,
                                int cognitiveJudgeTotalCalls,
                                int comparisonJudgeTotalCalls,
                                int totalUpperBoundCalls) {
            this.success = success;
            this.failureReason = failureReason;
            this.platformCount = platformCount;
            this.competitorPromptCount = competitorPromptCount;
            this.batch1TotalCalls = batch1TotalCalls;
            this.cognitiveJudgeTotalCalls = cognitiveJudgeTotalCalls;
            this.comparisonJudgeTotalCalls = comparisonJudgeTotalCalls;
            this.totalUpperBoundCalls = totalUpperBoundCalls;
        }

        static PreflightResult fail(String reason) {
            return new PreflightResult(false, reason, 0, 0, 0, 0, 0, 0);
        }

        static PreflightResult success(int platformCount,
                                       int competitorPromptCount,
                                       int batch1TotalCalls,
                                       int cognitiveJudgeTotalCalls,
                                       int comparisonJudgeTotalCalls,
                                       int totalUpperBoundCalls) {
            return new PreflightResult(true, null, platformCount, competitorPromptCount,
                    batch1TotalCalls, cognitiveJudgeTotalCalls, comparisonJudgeTotalCalls, totalUpperBoundCalls);
        }

        boolean success() {
            return success;
        }

        String failureReason() {
            return failureReason;
        }

        int platformCount() {
            return platformCount;
        }

        int competitorPromptCount() {
            return competitorPromptCount;
        }

        int batch1TotalCalls() {
            return batch1TotalCalls;
        }

        int cognitiveJudgeTotalCalls() {
            return cognitiveJudgeTotalCalls;
        }

        int comparisonJudgeTotalCalls() {
            return comparisonJudgeTotalCalls;
        }

        int totalUpperBoundCalls() {
            return totalUpperBoundCalls;
        }
    }

    private static final class PlatformBatchState {
        private final String platformCode;
        private final int totalPrompts;
        private int processedPrompts;
        private int failedPrompts;
        private boolean degraded;

        private PlatformBatchState(String platformCode, int totalPrompts) {
            this.platformCode = platformCode;
            this.totalPrompts = totalPrompts;
        }

        synchronized void incrementProcessed() {
            processedPrompts++;
        }

        synchronized void incrementFailed() {
            failedPrompts++;
        }

        synchronized int processedPrompts() {
            return processedPrompts;
        }

        synchronized int failedPrompts() {
            return failedPrompts;
        }

        synchronized boolean isDegraded() {
            return degraded;
        }

        synchronized void markDegraded() {
            degraded = true;
        }
    }

    @FunctionalInterface
    private interface PromptTask {
        void run(PresaleReportVersionPromptTemplate template);
    }

    private static final class PresalePromptThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String prefix;

        private PresalePromptThreadFactory(Long versionId, int batchNo, String platformCode) {
            this.prefix = "presale-prompt-" + versionId + "-b" + batchNo + "-" + platformCode + "-";
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Batch1ExecutionResult {
        private final boolean stopPipeline;
        private final Set<String> degradedPlatforms;
        private final int overRunCount;
        private final List<PlatformBatchResult> platformResults;

        private Batch1ExecutionResult(boolean stopPipeline,
                                      Set<String> degradedPlatforms,
                                      int overRunCount,
                                      List<PlatformBatchResult> platformResults) {
            this.stopPipeline = stopPipeline;
            this.degradedPlatforms = degradedPlatforms == null ? Set.of() : Set.copyOf(degradedPlatforms);
            this.overRunCount = overRunCount;
            this.platformResults = platformResults == null ? List.of() : List.copyOf(platformResults);
        }

        static Batch1ExecutionResult stop(Set<String> degradedPlatforms,
                                          int overRunCount,
                                          List<PlatformBatchResult> platformResults) {
            return new Batch1ExecutionResult(true, degradedPlatforms, overRunCount, platformResults);
        }

        static Batch1ExecutionResult continuePipeline(Set<String> degradedPlatforms,
                                                      int overRunCount,
                                                      List<PlatformBatchResult> platformResults) {
            return new Batch1ExecutionResult(false, degradedPlatforms, overRunCount, platformResults);
        }

        boolean stopPipeline() {
            return stopPipeline;
        }

        Set<String> degradedPlatforms() {
            return degradedPlatforms;
        }

        int overRunCount() {
            return overRunCount;
        }

        List<PlatformBatchResult> platformResults() {
            return platformResults;
        }
    }

    /**
     * Batch2 执行结果。
     * - batch2DegradedPlatforms：D80 delta 集合，仅 batch2 新增的降级平台
     * - displayDegradedPlatforms：全量展示集(batch1 + batch2 新增)，用于 Assembler
     * - overRunCount：达阈值后仍被启动的超跑平台数(已考虑 batch1 起步量)
     */
    private static final class Batch2ExecutionResult {
        private final boolean stopPipeline;
        private final Set<String> batch2DegradedPlatforms;
        private final Set<String> displayDegradedPlatforms;
        private final int overRunCount;
        private final List<PlatformBatchResult> platformResults;

        private Batch2ExecutionResult(boolean stopPipeline,
                                      Set<String> batch2DegradedPlatforms,
                                      Set<String> displayDegradedPlatforms,
                                      int overRunCount,
                                      List<PlatformBatchResult> platformResults) {
            this.stopPipeline = stopPipeline;
            this.batch2DegradedPlatforms = batch2DegradedPlatforms == null ? Set.of() : Set.copyOf(batch2DegradedPlatforms);
            this.displayDegradedPlatforms = displayDegradedPlatforms == null ? Set.of() : Set.copyOf(displayDegradedPlatforms);
            this.overRunCount = overRunCount;
            this.platformResults = platformResults == null ? List.of() : List.copyOf(platformResults);
        }

        static Batch2ExecutionResult stop(Set<String> batch2DegradedPlatforms,
                                          Set<String> displayDegradedPlatforms,
                                          int overRunCount,
                                          List<PlatformBatchResult> platformResults) {
            return new Batch2ExecutionResult(true, batch2DegradedPlatforms, displayDegradedPlatforms, overRunCount, platformResults);
        }

        static Batch2ExecutionResult continuePipeline(Set<String> batch2DegradedPlatforms,
                                                      Set<String> displayDegradedPlatforms,
                                                      int overRunCount,
                                                      List<PlatformBatchResult> platformResults) {
            return new Batch2ExecutionResult(false, batch2DegradedPlatforms, displayDegradedPlatforms, overRunCount, platformResults);
        }

        boolean stopPipeline() {
            return stopPipeline;
        }

        Set<String> batch2DegradedPlatforms() {
            return batch2DegradedPlatforms;
        }

        Set<String> displayDegradedPlatforms() {
            return displayDegradedPlatforms;
        }

        int overRunCount() {
            return overRunCount;
        }

        List<PlatformBatchResult> platformResults() {
            return platformResults;
        }

    }

    private record ProgressCounts(int batch1CompletedCalls, int batch2CompletedCalls) {
    }

    private String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        if (reason.length() <= FAILURE_REASON_MAX_LEN) {
            return reason;
        }
        return reason.substring(0, FAILURE_REASON_MAX_LEN);
    }

    private int getPlatformExecutorQueueSize() {
        if (platformExecutor instanceof ThreadPoolTaskExecutor taskExecutor) {
            ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();
            return threadPoolExecutor == null ? -1 : threadPoolExecutor.getQueue().size();
        }
        return -1;
    }

    private int getPlatformExecutorActiveCount() {
        if (platformExecutor instanceof ThreadPoolTaskExecutor taskExecutor) {
            ThreadPoolExecutor threadPoolExecutor = taskExecutor.getThreadPoolExecutor();
            return threadPoolExecutor == null ? -1 : threadPoolExecutor.getActiveCount();
        }
        return -1;
    }

    private PresaleReport buildPromptRenderReport(PresaleReport report) {
        if (report == null) {
            return null;
        }
        PresaleReport out = new PresaleReport();
        out.setId(report.getId());
        out.setBrandName(report.getBrandName());
        out.setIndustry(resolveDictValue(DICT_TYPE_PRESALE_INDUSTRY, report.getIndustry()));
        out.setIndustryRole(resolveDictValue(DICT_TYPE_PRESALE_INDUSTRY_ROLE, report.getIndustryRole()));
        out.setRegion(report.getRegion());
        out.setUserDemand(report.getUserDemand());
        out.setLatestVersionId(report.getLatestVersionId());
        out.setCreatedAt(report.getCreatedAt());
        out.setUpdatedAt(report.getUpdatedAt());
        out.setCreatedBy(report.getCreatedBy());
        return out;
    }

    private String resolveDictValue(String dictType, String dictKey) {
        if (dictKey == null || dictKey.isBlank()) {
            return "";
        }
        List<SysDictItem> rows = sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictType, dictType)
                        .eq(SysDictItem::getDictKey, dictKey)
                        .eq(SysDictItem::getEnabled, true)
                        .last("limit 1")
        );
        if (rows == null || rows.isEmpty()) {
            return dictKey;
        }
        String dictValue = rows.get(0).getDictValue();
        return (dictValue == null || dictValue.isBlank()) ? dictKey : dictValue;
    }
}

