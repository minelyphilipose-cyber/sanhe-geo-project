package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.generate.llm.AnalyzeParseException;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final String STAGE_L1_AGGREGATE = "L1_AGGREGATE";
    private static final String STAGE_L2_COMPUTE = "L2_COMPUTE";
    private static final String STAGE_L3_INIT = "L3_INIT";
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

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleReportMapper reportMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresalePromptTemplateMapper promptTemplateMapper;
    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final ReuseDecisionService reuseDecisionService;
    private final PresaleReusePersistenceService reusePersistenceService;
    private final PresaleLlmInvoker llmInvoker;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final PresaleRawSnapshotAssembler rawSnapshotAssembler;
    private final PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    private final PresaleL3InitService l3InitService;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final ObjectMapper objectMapper;
    private final Map<Long, Long> lastProgressUpdateAtByVersion = new ConcurrentHashMap<>();

    @Value("${presale.generate.mock:true}")
    private boolean mockEnabled;

    @Value("${presale.generate.mock-delay-ms:5000}")
    private long mockDelayMs;

    @Value("${presale.generate.mock-fixture-path:fixtures/01-mock-sample-v1.2.json}")
    private String mockFixturePath;

    @Value("${presale.generate.allow-synthetic-fallback.mock:true}")
    private boolean allowSyntheticFallbackMock;

    @Value("${presale.generate.allow-synthetic-fallback.real:false}")
    private boolean allowSyntheticFallbackReal;

    public PresaleGenerateOrchestrator(PresaleReportVersionMapper versionMapper,
                                       PresaleReportMapper reportMapper,
                                       AiPlatformConfigMapper aiPlatformConfigMapper,
                                       PresalePromptTemplateMapper promptTemplateMapper,
                                       PresaleAiCallMapper aiCallMapper,
                                       PresaleAiPromptResultMapper aiPromptResultMapper,
                                       ReuseDecisionService reuseDecisionService,
                                       PresaleReusePersistenceService reusePersistenceService,
                                       PresaleLlmInvoker llmInvoker,
                                       PromptTemplateRenderer promptTemplateRenderer,
                                       PresaleRawSnapshotAssembler rawSnapshotAssembler,
                                       PresaleComputedSnapshotEnricher computedSnapshotEnricher,
                                       PresaleL3InitService l3InitService,
                                       PresaleCompetitorAggregator competitorAggregator,
                                       ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.reportMapper = reportMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.aiCallMapper = aiCallMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.reuseDecisionService = reuseDecisionService;
        this.reusePersistenceService = reusePersistenceService;
        this.llmInvoker = llmInvoker;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.rawSnapshotAssembler = rawSnapshotAssembler;
        this.computedSnapshotEnricher = computedSnapshotEnricher;
        this.l3InitService = l3InitService;
        this.competitorAggregator = competitorAggregator;
        this.objectMapper = objectMapper;
    }

    @Async("presaleGenerateExecutor")
    public void triggerGenerate(Long versionId, Long operatorUserId, boolean isManager) {
        try {
            doTriggerGenerate(versionId, operatorUserId, isManager);
        } catch (Throwable t) {
            log.error("Presale generate fatal error, versionId={}", versionId, t);
            try {
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
        if (mockEnabled) {
            runMockFlow(versionId);
            return;
        }
        runRealFullFlow(versionId, operatorUserId, isManager);
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
        versionMapper.updateById(update);

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
        Batch1ExecutionResult batch1Result = executeBatch1(version, report, operatorUserId, isManager, preflight);
        if (batch1Result.stopPipeline) {
            return;
        }
        Set<String> allDegraded = new LinkedHashSet<>(batch1Result.degradedPlatforms());

        enterStage(versionId, STAGE_COMPETITOR_EXTRACT, "extract competitors");

        List<String> extractedCompetitors = extractTopCompetitorsFromBatch1(versionId, report.getBrandName());
        int extractedCompetitorCount = extractedCompetitors.size();
        int batch2TotalCalls = preflight.platformCount() * preflight.competitorPromptCount() * extractedCompetitorCount * 2;
        updateAfterCompetitorExtract(
                versionId,
                extractedCompetitorCount,
                batch2TotalCalls,
                preflight.batch1TotalCalls() + batch2TotalCalls
        );

        if (extractedCompetitorCount > 0) {
            Batch2ExecutionResult batch2Result = executeBatch2(
                    versionId,
                    report,
                    operatorUserId,
                    isManager,
                    extractedCompetitors,
                    preflight.competitorPromptCount()
            );
            if (batch2Result.stopPipeline) {
                return;
            }
            allDegraded.addAll(batch2Result.degradedPlatforms());
        } else {
            markCompetitorExtractEmpty(versionId);
            log.info("Skip batch2 because extracted competitors is 0, versionId={}", versionId);
        }

        String rawJson;
        enterStage(versionId, STAGE_L1_AGGREGATE, "assemble raw snapshot");
        try {
            rawJson = rawSnapshotAssembler.assemble(versionId, report, version, allDegraded, extractedCompetitors);
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
        writeComputedSnapshotJson(versionId, computedJson);

        String editableJson;
        enterStage(versionId, STAGE_L3_INIT, "derive editable content");
        try {
            editableJson = l3InitService.derive(rawJson, computedJson);
        } catch (BizException ex) {
            markFailed(versionId, FAILURE_CATEGORY_L3_INIT_ERROR,
                    truncateReason("L3 init failed: " + ex.getMessage()));
            return;
        }
        writeEditableContentJson(versionId, editableJson);

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

        int platformCount = countEnabledPlatforms();
        if (platformCount < 1) {
            return PreflightResult.fail("enabled platform count is 0");
        }

        int genericPromptCount = countPromptTemplates(0);
        if (genericPromptCount < 1) {
            return PreflightResult.fail("generic prompt count is 0");
        }

        int competitorPromptCount = countPromptTemplates(1);
        int batch1TotalCalls = platformCount * genericPromptCount * 2;
        int totalUpperBoundCalls = batch1TotalCalls + (platformCount * competitorPromptCount * 3 * 2);
        return PreflightResult.success(platformCount, competitorPromptCount, batch1TotalCalls, totalUpperBoundCalls);
    }

    private Batch1ExecutionResult executeBatch1(PresaleReportVersion version,
                                                PresaleReport report,
                                                Long operatorUserId,
                                                boolean isManager,
                                                PreflightResult preflight) {
        Long versionId = version.getId();
        enterStage(versionId, STAGE_BATCH1, "batch1 executing");

        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .orderByAsc(AiPlatformConfig::getPlatformCode)
        );
        List<PresalePromptTemplate> templates = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getHasCompetitorVar, 0)
                        .orderByAsc(PresalePromptTemplate::getSortOrder)
                        .orderByAsc(PresalePromptTemplate::getId)
        );

        int qGen = templates.size();
        ProgressCounts counts = new ProgressCounts(0, 0);
        Set<String> degradedPlatforms = new LinkedHashSet<>();
        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache =
                reuseDecisionService.preloadByVersionAndBatch(versionId, 1);

        for (AiPlatformConfig platform : platforms) {
            PlatformBatchState state = new PlatformBatchState(platform.getPlatformCode(), qGen);

            for (PresalePromptTemplate template : templates) {
                if (state.degraded) {
                    insertSkippedCall(versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            "QUERY");
                    insertSkippedCall(versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            "ANALYZE");
                    state.processedPrompts++;
                    counts = applyCompletedForOnePromptPair(versionId, counts, 1, degradedPlatforms, false);
                    continue;
                }

                PlatformCallContext ctx = new PlatformCallContext(
                        versionId,
                        1,
                        platform.getPlatformCode(),
                        template.getId(),
                        "",
                        report.getBrandName(),
                        operatorUserId,
                        isManager
                );
                String renderedPrompt = promptTemplateRenderer.render(
                        template.getPromptContent(),
                        template.getPromptCode(),
                        ctx,
                        report
                );

                ReuseDecision reuseDecision = reuseDecisionService.decide(ctx, reuseCache);
                if (reuseDecision == ReuseDecision.SKIP_ALL) {
                    state.processedPrompts++;
                    counts = applyCompletedForOnePromptPair(versionId, counts, 1, degradedPlatforms, false);
                    continue;
                }

                if (reuseDecision == ReuseDecision.REUSE_QUERY_ONLY) {
                    ReuseSnapshot snapshot = reuseDecisionService.snapshotOf(ctx, reuseCache);
                    PresaleAiCall reusedQueryCall = snapshot == null ? null : snapshot.querySuccessCall();
                    if (reusedQueryCall != null && reusedQueryCall.getRawResponse() != null) {
                        boolean interruptedInAnalyze = false;
                        // REUSE_QUERY_ONLY: 只捕获 LLM 异常(LlmInvokeException / AnalyzeParseException),
                        // 不捕获通用 Throwable。DB 中断等底层异常直接冒泡到 triggerGenerate 外层 catch (Throwable t),
                        // 由 isInterruptedFailure 统一分类为 INTERRUPTED。
                        // 这样设计避免吞掉非中断类的真实 DB / RuntimeException,保留 bug 暴露面。
                        // 已知副作用:DB 中断时 state.processedPrompts / counts 本对未累加,
                        // 与 LLM 中断路径(能走本 catch 分支完成累加)轻微不一致,但不影响 INTERRUPTED 分类本身。
                        try {
                            LlmCallResult analyzeResult = llmInvoker.analyze(ctx, renderedPrompt, reusedQueryCall.getRawResponse());
                            PresaleAiCall analyzeCall = buildCall(
                                    versionId, 1, platform.getPlatformCode(), template.getId(), "",
                                    "ANALYZE", reusedQueryCall.getId(), analyzeResult, null
                            );
                            PresaleAiPromptResult promptResult = buildPromptResultSuccess(
                                    versionId, 1, platform.getPlatformCode(), template.getId(), "",
                                    reusedQueryCall.getId(), analyzeResult.rawResponse()
                            );
                            reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult);
                        } catch (LlmInvokeException | AnalyzeParseException ex) {
                            PresaleAiCall analyzeCall = buildFailedCall(
                                    versionId, 1, platform.getPlatformCode(), template.getId(), "",
                                    "ANALYZE", reusedQueryCall.getId(), ex.getMessage()
                            );
                            PresaleAiPromptResult promptResult = buildPromptResultAnalyzeFailed(
                                    versionId, 1, platform.getPlatformCode(), template.getId(), "", reusedQueryCall.getId()
                            );
                            reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult);
                            state.failedPrompts++;
                            interruptedInAnalyze = isInterruptedFailure(ex);
                        }

                        state.processedPrompts++;
                        counts = applyCompletedForOnePromptPair(versionId, counts, 1, degradedPlatforms, false);
                        if (shouldDegrade(state)) {
                            state.degraded = true;
                            degradedPlatforms.add(state.platformCode);
                        }
                        if (degradedPlatforms.size() >= 4) {
                            updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), degradedPlatforms, true);
                            markTooManyDegradedFailed(versionId, degradedPlatforms);
                            return Batch1ExecutionResult.stop(degradedPlatforms);
                        }
                        if (interruptedInAnalyze) {
                            throw new BatchInterruptedException("batch1 interrupted during reused analyze");
                        }
                        continue;
                    }
                }

                PresaleAiCall queryCall;
                LlmCallResult queryResult;
                try {
                    queryResult = llmInvoker.query(ctx, renderedPrompt);
                    queryCall = insertCall(
                            versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            "QUERY", null, queryResult, null
                    );
                } catch (LlmInvokeException ex) {
                    insertFailedCall(versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            "QUERY", null, ex.getMessage());
                    state.processedPrompts++;
                    state.failedPrompts++;
                    counts = applyCompletedForOnePromptPair(versionId, counts, 1, degradedPlatforms, false);
                    if (shouldDegrade(state)) {
                        state.degraded = true;
                        degradedPlatforms.add(state.platformCode);
                    }
                    if (degradedPlatforms.size() >= 4) {
                        updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), degradedPlatforms, true);
                        markTooManyDegradedFailed(versionId, degradedPlatforms);
                        return Batch1ExecutionResult.stop(degradedPlatforms);
                    }
                    if (isInterruptedFailure(ex)) {
                        throw new BatchInterruptedException("batch1 interrupted during query");
                    }
                    continue;
                }

                try {
                    LlmCallResult analyzeResult = llmInvoker.analyze(ctx, renderedPrompt, queryResult.rawResponse());
                    PresaleAiCall analyzeCall = insertCall(
                            versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            "ANALYZE", queryCall.getId(), analyzeResult, null
                    );
                    insertPromptResultSuccess(versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            queryCall.getId(), analyzeCall.getId(), analyzeResult.rawResponse());
                } catch (LlmInvokeException | AnalyzeParseException ex) {
                    PresaleAiCall analyzeCall = insertFailedCall(versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            "ANALYZE", queryCall.getId(), ex.getMessage());
                    insertPromptResultAnalyzeFailed(versionId, 1, platform.getPlatformCode(), template.getId(), "",
                            queryCall.getId(), analyzeCall.getId());
                    state.failedPrompts++;
                }

                state.processedPrompts++;
                counts = applyCompletedForOnePromptPair(versionId, counts, 1, degradedPlatforms, false);
                if (shouldDegrade(state)) {
                    state.degraded = true;
                    degradedPlatforms.add(state.platformCode);
                }
                if (degradedPlatforms.size() >= 4) {
                    updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), degradedPlatforms, true);
                    markTooManyDegradedFailed(versionId, degradedPlatforms);
                    return Batch1ExecutionResult.stop(degradedPlatforms);
                }
            }
        }
        updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), degradedPlatforms, true);
        return Batch1ExecutionResult.continuePipeline(degradedPlatforms);
    }

    private Batch2ExecutionResult executeBatch2(Long versionId,
                                                PresaleReport report,
                                                Long operatorUserId,
                                                boolean isManager,
                                                List<String> competitors,
                                                int competitorPromptCount) {
        enterStage(versionId, STAGE_BATCH2, "batch2 executing");

        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .orderByAsc(AiPlatformConfig::getPlatformCode)
        );
        List<PresalePromptTemplate> templates = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getHasCompetitorVar, 1)
                        .orderByAsc(PresalePromptTemplate::getSortOrder)
                        .orderByAsc(PresalePromptTemplate::getId)
        );

        int c = competitors.size();
        int qCmp = competitorPromptCount > 0 ? competitorPromptCount : templates.size();
        int batch2TotalPromptsPerPlatform = qCmp * c;
        PresaleReportVersion current = versionMapper.selectById(versionId);
        ProgressCounts counts = new ProgressCounts(
                current != null && current.getBatch1CompletedCalls() != null ? current.getBatch1CompletedCalls() : 0,
                current != null && current.getBatch2CompletedCalls() != null ? current.getBatch2CompletedCalls() : 0
        );

        Set<String> batch2DegradedPlatforms = new LinkedHashSet<>();
        Set<String> displayDegradedPlatforms = new LinkedHashSet<>(parseJsonArray(current == null ? null : current.getDegradedPlatforms()));
        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> reuseCache =
                reuseDecisionService.preloadByVersionAndBatch(versionId, 2);

        for (AiPlatformConfig platform : platforms) {
            PlatformBatchState state = new PlatformBatchState(platform.getPlatformCode(), batch2TotalPromptsPerPlatform);

            for (PresalePromptTemplate template : templates) {
                for (String competitorName : competitors) {
                    if (state.degraded) {
                        insertSkippedCall(versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                "QUERY");
                        insertSkippedCall(versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                "ANALYZE");
                        state.processedPrompts++;
                        counts = applyCompletedForOnePromptPair(versionId, counts, 2, displayDegradedPlatforms, false);
                        continue;
                    }

                    PlatformCallContext ctx = new PlatformCallContext(
                            versionId,
                            2,
                            platform.getPlatformCode(),
                            template.getId(),
                            competitorName,
                            report.getBrandName(),
                            operatorUserId,
                            isManager
                    );
                    String renderedPrompt = promptTemplateRenderer.render(
                            template.getPromptContent(),
                            template.getPromptCode(),
                            ctx,
                            report
                    );

                    ReuseDecision reuseDecision = reuseDecisionService.decide(ctx, reuseCache);
                    if (reuseDecision == ReuseDecision.SKIP_ALL) {
                        state.processedPrompts++;
                        counts = applyCompletedForOnePromptPair(versionId, counts, 2, displayDegradedPlatforms, false);
                        if (shouldDegrade(state)) {
                            state.degraded = true;
                            batch2DegradedPlatforms.add(state.platformCode);
                            displayDegradedPlatforms.add(state.platformCode);
                        }
                        if (batch2DegradedPlatforms.size() >= 4) {
                            updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), displayDegradedPlatforms, true);
                            markTooManyDegradedFailed(versionId, displayDegradedPlatforms);
                            return Batch2ExecutionResult.stop(displayDegradedPlatforms);
                        }
                        continue;
                    }

                    if (reuseDecision == ReuseDecision.REUSE_QUERY_ONLY) {
                        ReuseSnapshot snapshot = reuseDecisionService.snapshotOf(ctx, reuseCache);
                        PresaleAiCall reusedQueryCall = snapshot == null ? null : snapshot.querySuccessCall();
                        if (reusedQueryCall != null && reusedQueryCall.getRawResponse() != null) {
                            boolean interruptedInAnalyze = false;
                            // REUSE_QUERY_ONLY: 只捕获 LLM 异常(LlmInvokeException / AnalyzeParseException),
                            // 不捕获通用 Throwable。DB 中断等底层异常直接冒泡到 triggerGenerate 外层 catch (Throwable t),
                            // 由 isInterruptedFailure 统一分类为 INTERRUPTED。
                            // 这样设计避免吞掉非中断类的真实 DB / RuntimeException,保留 bug 暴露面。
                            // 已知副作用:DB 中断时 state.processedPrompts / counts 本对未累加,
                            // 与 LLM 中断路径(能走本 catch 分支完成累加)轻微不一致,但不影响 INTERRUPTED 分类本身。
                            try {
                                LlmCallResult analyzeResult = llmInvoker.analyze(ctx, renderedPrompt, reusedQueryCall.getRawResponse());
                                PresaleAiCall analyzeCall = buildCall(
                                        versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                        "ANALYZE", reusedQueryCall.getId(), analyzeResult, null
                                );
                                PresaleAiPromptResult promptResult = buildPromptResultSuccess(
                                        versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                        reusedQueryCall.getId(), analyzeResult.rawResponse()
                                );
                                reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult);
                            } catch (LlmInvokeException | AnalyzeParseException ex) {
                                PresaleAiCall analyzeCall = buildFailedCall(
                                        versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                        "ANALYZE", reusedQueryCall.getId(), ex.getMessage()
                                );
                                PresaleAiPromptResult promptResult = buildPromptResultAnalyzeFailed(
                                        versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                        reusedQueryCall.getId()
                                );
                                reusePersistenceService.replaceFailedAnalyzeAndResult(ctx, reusedQueryCall, analyzeCall, promptResult);
                                state.failedPrompts++;
                                interruptedInAnalyze = isInterruptedFailure(ex);
                            }

                            state.processedPrompts++;
                            counts = applyCompletedForOnePromptPair(versionId, counts, 2, displayDegradedPlatforms, false);
                            if (shouldDegrade(state)) {
                                state.degraded = true;
                                batch2DegradedPlatforms.add(state.platformCode);
                                displayDegradedPlatforms.add(state.platformCode);
                            }
                            if (batch2DegradedPlatforms.size() >= 4) {
                                updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), displayDegradedPlatforms, true);
                                markTooManyDegradedFailed(versionId, displayDegradedPlatforms);
                                return Batch2ExecutionResult.stop(displayDegradedPlatforms);
                            }
                            if (interruptedInAnalyze) {
                                throw new BatchInterruptedException("batch2 interrupted during reused analyze");
                            }
                            continue;
                        }
                    }

                    PresaleAiCall queryCall;
                    LlmCallResult queryResult;
                    try {
                        queryResult = llmInvoker.query(ctx, renderedPrompt);
                        queryCall = insertCall(
                                versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                "QUERY", null, queryResult, null
                        );
                    } catch (LlmInvokeException ex) {
                        insertFailedCall(versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                "QUERY", null, ex.getMessage());
                        state.processedPrompts++;
                        state.failedPrompts++;
                        counts = applyCompletedForOnePromptPair(versionId, counts, 2, displayDegradedPlatforms, false);
                        if (shouldDegrade(state)) {
                            state.degraded = true;
                            batch2DegradedPlatforms.add(state.platformCode);
                            displayDegradedPlatforms.add(state.platformCode);
                        }
                        if (batch2DegradedPlatforms.size() >= 4) {
                            updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), displayDegradedPlatforms, true);
                            markTooManyDegradedFailed(versionId, displayDegradedPlatforms);
                            return Batch2ExecutionResult.stop(displayDegradedPlatforms);
                        }
                        if (isInterruptedFailure(ex)) {
                            throw new BatchInterruptedException("batch2 interrupted during query");
                        }
                        continue;
                    }

                    try {
                        LlmCallResult analyzeResult = llmInvoker.analyze(ctx, renderedPrompt, queryResult.rawResponse());
                        PresaleAiCall analyzeCall = insertCall(
                                versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                "ANALYZE", queryCall.getId(), analyzeResult, null
                        );
                        insertPromptResultSuccess(versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                queryCall.getId(), analyzeCall.getId(), analyzeResult.rawResponse());
                    } catch (LlmInvokeException | AnalyzeParseException ex) {
                        PresaleAiCall analyzeCall = insertFailedCall(versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                "ANALYZE", queryCall.getId(), ex.getMessage());
                        insertPromptResultAnalyzeFailed(versionId, 2, platform.getPlatformCode(), template.getId(), competitorName,
                                queryCall.getId(), analyzeCall.getId());
                        state.failedPrompts++;
                    }

                    state.processedPrompts++;
                    counts = applyCompletedForOnePromptPair(versionId, counts, 2, displayDegradedPlatforms, false);
                    if (shouldDegrade(state)) {
                        state.degraded = true;
                        batch2DegradedPlatforms.add(state.platformCode);
                        displayDegradedPlatforms.add(state.platformCode);
                    }
                    if (batch2DegradedPlatforms.size() >= 4) {
                        updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), displayDegradedPlatforms, true);
                        markTooManyDegradedFailed(versionId, displayDegradedPlatforms);
                        return Batch2ExecutionResult.stop(displayDegradedPlatforms);
                    }
                }
            }
        }
        updateBatchProgress(versionId, counts.batch1CompletedCalls(), counts.batch2CompletedCalls(), displayDegradedPlatforms, true);
        return Batch2ExecutionResult.continuePipeline(displayDegradedPlatforms);
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
        versionMapper.updateById(update);
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
        versionMapper.updateById(update);
    }

    private void enterStage(Long versionId, String stage, String note) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStage(stage);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
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
        versionMapper.updateById(update);
    }

    private void markFailed(Long versionId, String failureCategory, String reason) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setGenerationStatus(PresaleGenerateStatus.FAILED.name());
        update.setGenerationStage(null);
        update.setFailureCategory(failureCategory);
        update.setFailureReason(truncateReason(reason));
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
        lastProgressUpdateAtByVersion.remove(versionId);
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
        versionMapper.updateById(update);
        lastProgressUpdateAtByVersion.remove(versionId);
    }

    private void writeRawSnapshotJson(Long versionId, String rawJson) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setRawSnapshotJson(rawJson);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    private void writeComputedSnapshotJson(Long versionId, String computedJson) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setComputedSnapshotJson(computedJson);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
    }

    private void writeEditableContentJson(Long versionId, String editableJson) {
        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setEditableContentJson(editableJson);
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
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

    private int countEnabledPlatforms() {
        Long count = aiPlatformConfigMapper.selectCount(
                new LambdaQueryWrapper<AiPlatformConfig>().eq(AiPlatformConfig::getEnabled, true)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countPromptTemplates(int hasCompetitorVar) {
        Long count = promptTemplateMapper.selectCount(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getHasCompetitorVar, hasCompetitorVar)
        );
        return count == null ? 0 : count.intValue();
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
        if (!forceFlush) {
            long now = System.currentTimeMillis();
            long lastAt = lastProgressUpdateAtByVersion.getOrDefault(versionId, 0L);
            if (now - lastAt < PROGRESS_UPDATE_THROTTLE_MS) {
                return;
            }
            lastProgressUpdateAtByVersion.put(versionId, now);
        } else {
            lastProgressUpdateAtByVersion.put(versionId, System.currentTimeMillis());
        }

        PresaleReportVersion update = new PresaleReportVersion();
        update.setId(versionId);
        update.setBatch1CompletedCalls(batch1CompletedCalls);
        update.setBatch2CompletedCalls(batch2CompletedCalls);
        update.setCompletedLlmCalls(batch1CompletedCalls + batch2CompletedCalls);
        update.setIsDegraded(!degradedPlatforms.isEmpty());
        update.setDegradedPlatforms(toJsonArray(degradedPlatforms));
        update.setUpdatedAt(LocalDateTime.now());
        versionMapper.updateById(update);
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
        versionMapper.updateById(update);
        lastProgressUpdateAtByVersion.remove(versionId);
    }

    private List<String> extractTopCompetitorsFromBatch1(Long versionId, String brandName) {
        return competitorAggregator.extractTopCompetitorsFromBatch1(versionId, brandName);
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
        versionMapper.updateById(update);
    }

    private PresaleAiCall insertCall(Long versionId,
                                     int batchNo,
                                     String platformCode,
                                     Long promptTemplateId,
                                     String competitorName,
                                     String stage,
                                     Long parentCallId,
                                     LlmCallResult result,
                                     String failureReason) {
        PresaleAiCall row = buildCall(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, stage,
                parentCallId, result, failureReason
        );
        aiCallMapper.insert(row);
        return row;
    }

    private PresaleAiCall insertFailedCall(Long versionId,
                                           int batchNo,
                                           String platformCode,
                                           Long promptTemplateId,
                                           String competitorName,
                                           String stage,
                                           Long parentCallId,
                                           String failureReason) {
        PresaleAiCall row = buildFailedCall(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, stage, parentCallId, failureReason
        );
        aiCallMapper.insert(row);
        return row;
    }

    private PresaleAiCall buildCall(Long versionId,
                                    int batchNo,
                                    String platformCode,
                                    Long promptTemplateId,
                                    String competitorName,
                                    String stage,
                                    Long parentCallId,
                                    LlmCallResult result,
                                    String failureReason) {
        PresaleAiCall row = new PresaleAiCall();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setStage(stage);
        row.setParentCallId(parentCallId);
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
                                          String failureReason) {
        PresaleAiCall row = new PresaleAiCall();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setStage(stage);
        row.setParentCallId(parentCallId);
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
        row.setCallStatus(CallStatus.SKIPPED_DEGRADED.name());
        row.setRetryCount(0);
        row.setRawResponse(null);
        row.setFailureReason("SKIPPED_DEGRADED");
        row.setPromptTokens(null);
        row.setCompletionTokens(null);
        row.setDurationMs(null);
        aiCallMapper.insert(row);
    }

    private void insertPromptResultAnalyzeFailed(Long versionId,
                                                 int batchNo,
                                                 String platformCode,
                                                 Long promptTemplateId,
                                                 String competitorName,
                                                 Long queryCallId,
                                                 Long analyzeCallId) {
        PresaleAiPromptResult row = buildPromptResultAnalyzeFailed(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, queryCallId
        );
        row.setAnalyzeCallId(analyzeCallId);
        aiPromptResultMapper.insert(row);
    }

    private void insertPromptResultSuccess(Long versionId,
                                           int batchNo,
                                           String platformCode,
                                           Long promptTemplateId,
                                           String competitorName,
                                           Long queryCallId,
                                           Long analyzeCallId,
                                           String analyzeJson) throws AnalyzeParseException {
        PresaleAiPromptResult row = buildPromptResultSuccess(
                versionId, batchNo, platformCode, promptTemplateId, competitorName, queryCallId, analyzeJson
        );
        row.setAnalyzeCallId(analyzeCallId);
        aiPromptResultMapper.insert(row);
    }

    private PresaleAiPromptResult buildPromptResultAnalyzeFailed(Long versionId,
                                                                 int batchNo,
                                                                 String platformCode,
                                                                 Long promptTemplateId,
                                                                 String competitorName,
                                                                 Long queryCallId) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setQueryCallId(queryCallId);
        row.setAnalyzeCallId(null);
        row.setIsMentioned(null);
        row.setRanking(null);
        row.setSentiment(null);
        row.setMentionedCompetitors(null);
        row.setSceneAdvantages(null);
        return row;
    }

    private PresaleAiPromptResult buildPromptResultSuccess(Long versionId,
                                                           int batchNo,
                                                           String platformCode,
                                                           Long promptTemplateId,
                                                           String competitorName,
                                                           Long queryCallId,
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
            row.setIsMentioned(node.get("is_mentioned").asBoolean() ? 1 : 0);
            row.setRanking(node.get("ranking") == null || node.get("ranking").isNull() ? null : node.get("ranking").asInt());
            row.setSentiment(node.get("sentiment").asText());
            row.setMentionedCompetitors(objectMapper.writeValueAsString(node.get("mentioned_competitors")));
            row.setSceneAdvantages(objectMapper.writeValueAsString(node.get("scene_advantages")));
            return row;
        } catch (Exception ex) {
            throw new AnalyzeParseException("failed to persist analyze success payload", ex);
        }
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
        if (state.processedPrompts * 2 < state.totalPrompts) {
            return false;
        }
        return state.failedPrompts * 2 >= state.processedPrompts;
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

    private static final class PreflightResult {
        private final boolean success;
        private final String failureReason;
        private final int platformCount;
        private final int competitorPromptCount;
        private final int batch1TotalCalls;
        private final int totalUpperBoundCalls;

        private PreflightResult(boolean success,
                                String failureReason,
                                int platformCount,
                                int competitorPromptCount,
                                int batch1TotalCalls,
                                int totalUpperBoundCalls) {
            this.success = success;
            this.failureReason = failureReason;
            this.platformCount = platformCount;
            this.competitorPromptCount = competitorPromptCount;
            this.batch1TotalCalls = batch1TotalCalls;
            this.totalUpperBoundCalls = totalUpperBoundCalls;
        }

        static PreflightResult fail(String reason) {
            return new PreflightResult(false, reason, 0, 0, 0, 0);
        }

        static PreflightResult success(int platformCount,
                                       int competitorPromptCount,
                                       int batch1TotalCalls,
                                       int totalUpperBoundCalls) {
            return new PreflightResult(true, null, platformCount, competitorPromptCount,
                    batch1TotalCalls, totalUpperBoundCalls);
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
    }

    private static final class Batch1ExecutionResult {
        private final boolean stopPipeline;
        private final Set<String> degradedPlatforms;

        private Batch1ExecutionResult(boolean stopPipeline, Set<String> degradedPlatforms) {
            this.stopPipeline = stopPipeline;
            this.degradedPlatforms = degradedPlatforms == null ? Set.of() : Set.copyOf(degradedPlatforms);
        }

        static Batch1ExecutionResult stop(Set<String> degradedPlatforms) {
            return new Batch1ExecutionResult(true, degradedPlatforms);
        }

        static Batch1ExecutionResult continuePipeline(Set<String> degradedPlatforms) {
            return new Batch1ExecutionResult(false, degradedPlatforms);
        }

        boolean stopPipeline() {
            return stopPipeline;
        }

        Set<String> degradedPlatforms() {
            return degradedPlatforms;
        }
    }

    private static final class Batch2ExecutionResult {
        private final boolean stopPipeline;
        private final Set<String> degradedPlatforms;

        private Batch2ExecutionResult(boolean stopPipeline, Set<String> degradedPlatforms) {
            this.stopPipeline = stopPipeline;
            this.degradedPlatforms = degradedPlatforms == null ? Set.of() : Set.copyOf(degradedPlatforms);
        }

        static Batch2ExecutionResult stop(Set<String> degradedPlatforms) {
            return new Batch2ExecutionResult(true, degradedPlatforms);
        }

        static Batch2ExecutionResult continuePipeline(Set<String> degradedPlatforms) {
            return new Batch2ExecutionResult(false, degradedPlatforms);
        }

        boolean stopPipeline() {
            return stopPipeline;
        }

        Set<String> degradedPlatforms() {
            return degradedPlatforms;
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
}
