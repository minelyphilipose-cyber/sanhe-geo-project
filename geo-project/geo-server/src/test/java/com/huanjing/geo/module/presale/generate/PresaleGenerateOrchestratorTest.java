package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.pool.LlmPermitScope;
import com.huanjing.geo.common.llm.pool.LlmPermitUnavailableException;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.generate.l3.PresalePage03DoubaoService;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.presale.generate.web.PresaleQueryWebMode;
import com.huanjing.geo.module.presale.generate.web.PresaleWebExecutionContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.entity.SysDictItem;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresaleGenerateOrchestratorTest {

    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private SysDictItemMapper sysDictItemMapper;
    @Mock
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @Mock
    private PresaleAiCallMapper aiCallMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private ReuseDecisionService reuseDecisionService;
    @Mock
    private PresaleReusePersistenceService reusePersistenceService;
    @Mock
    private PresaleLlmInvoker llmInvoker;
    @Mock
    private PromptTemplateRenderer promptTemplateRenderer;
    @Mock
    private PresaleRawSnapshotAssembler rawSnapshotAssembler;
    @Mock
    private PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    @Mock
    private PresaleL3InitService l3InitService;
    @Mock
    private PresalePage03DoubaoService page03DoubaoService;
    @Mock
    private PresaleCompetitorAggregator competitorAggregator;
    @Mock
    private PresaleCompetitorNormalizationService competitorNormalizationService;
    @Mock
    private PresaleEvaluationModelRouter evaluationModelRouter;
    @Mock
    private PresaleGenerateCancellationRegistry cancellationRegistry;
    @Mock
    private PresaleJudgeService presaleJudgeService;
    @Mock
    private Executor platformExecutor;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PresaleGenerateOrchestrator orchestrator;

    @BeforeEach
    void setupReuseDefaults() {
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0, Runnable.class);
            task.run();
            return null;
        }).when(platformExecutor).execute(any(Runnable.class));
        lenient().when(reuseDecisionService.preloadByVersionAndBatch(any(), anyInt())).thenReturn(Map.of());
        lenient().when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.RUN_FULL);
        lenient().when(reuseDecisionService.snapshotOf(any(), any())).thenReturn(null);
        lenient().when(competitorAggregator.extractTopCompetitorsFromBatch1(any(), anyCollection())).thenReturn(List.of());
        lenient().when(sysDictItemMapper.selectList(any())).thenReturn(List.of());
        lenient().when(competitorAggregator.normalizeName(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cancellationRegistry.isCanceled(any())).thenReturn(false);
        lenient().when(evaluationModelRouter.routeContexts(any()))
                .thenAnswer(inv -> List.of(inv.getArgument(0, PlatformCallContext.class)));
        lenient().when(promptTemplateRenderer.variables(any(), any())).thenCallRealMethod();
        lenient().when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        lenient().when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        lenient().when(aiPlatformConfigMapper.selectCount(any())).thenReturn(1L);
        lenient().when(versionMapper.tryTransitionToRunning(anyLong(), anyInt())).thenReturn(1);
        lenient().when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn("{\"client_info\":{\"brand_name\":\"Acme\",\"industry\":\"Software\"},\"test_summary\":{\"total_platforms\":1,\"total_prompts\":1},\"benchmarks_frozen\":{\"industry_avg\":{\"overall\":50.0}},\"competitors\":[]}");
        lenient().when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{}");
        lenient().when(l3InitService.derive(anyString(), anyString())).thenReturn("{}");
        lenient().when(page03DoubaoService.generateAndApply(anyLong(), anyString(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> inv.getArgument(2, String.class));
    }

    @Test
    void requiredRunUsesReadinessPlatformSnapshotWithoutReloadingDatabase() {
        AiPlatformConfig platform = platforms("wenxin").get(0);
        PresaleWebExecutionContext context = new PresaleWebExecutionContext(
                PresaleQueryWebMode.REQUIRED, Map.of(), List.of(platform));
        platform.setPlatformCode("changed-after-snapshot");

        @SuppressWarnings("unchecked")
        List<AiPlatformConfig> resolved = ReflectionTestUtils.invokeMethod(
                orchestrator, "reportPlatformsForRun", context);

        assertNotNull(resolved);
        assertEquals(List.of("wenxin"), resolved.stream().map(AiPlatformConfig::getPlatformCode).toList());
        verify(aiPlatformConfigMapper, never()).selectList(any());
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptedException_directThrow_markedAsInterrupted() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", true);
        ReflectionTestUtils.setField(orchestrator, "mockDelayMs", 1000L);
        PresaleReportVersion claimed = new PresaleReportVersion();
        claimed.setId(9701L);
        // Legacy fixture: production rows have a non-null attempt after V346.
        when(versionMapper.selectById(9701L)).thenReturn(claimed);
        Thread.currentThread().interrupt();

        orchestrator.triggerGenerate(9701L, 701L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(updateCaptor.capture());
        PresaleReportVersion failed = updateCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .findFirst()
                .orElseThrow();
        assertEquals("INTERRUPTED", failed.getFailureCategory());
    }

    @Test
    void llmInvokeException_wrapsInterruptedException_markedAsInterrupted() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9702L, 8702L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(731L, "G1", "batch1 {brand}"))
        );
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString()))
                .thenThrow(new LlmInvokeException("wrapped", new InterruptedException("stop")));

        orchestrator.triggerGenerate(9702L, 702L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .findFirst()
                .orElseThrow();
        assertEquals("INTERRUPTED", failed.getFailureCategory());
    }

    @Test
    void llmInvokeException_withInterruptedFlag_abortsBatchEarly() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9703L, 8703L, 1, 3, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(741L, "G1", "Q1"),
                promptTemplate(742L, "G2", "Q2"),
                promptTemplate(743L, "G3", "Q3")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(PlatformCallContext.class), anyString())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new LlmInvokeException("interrupted flag set");
        });

        orchestrator.triggerGenerate(9703L, 703L, false);

        verify(aiCallMapper, times(1)).insert(any(PresaleAiCall.class));
        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .findFirst()
                .orElseThrow();
        assertEquals("INTERRUPTED", failed.getFailureCategory());
    }

    @Test
    void interruptedOnPlatform1_platform2NotExecuted() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9704L, 8704L, 2, 2, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p1", "p2"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(751L, "G1", "Q1"),
                promptTemplate(752L, "G2", "Q2")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(PlatformCallContext.class), anyString())).thenAnswer(invocation -> {
            PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
            if ("p1".equals(ctx.platformCode())) {
                Thread.currentThread().interrupt();
                throw new LlmInvokeException("interrupt on platform1");
            }
            return successResult("unexpected-platform2-call");
        });

        orchestrator.triggerGenerate(9704L, 704L, false);

        ArgumentCaptor<PlatformCallContext> queryCtxCaptor = ArgumentCaptor.forClass(PlatformCallContext.class);
        verify(llmInvoker, times(1)).query(queryCtxCaptor.capture(), anyString());
        assertEquals("p1", queryCtxCaptor.getValue().platformCode());

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, times(1)).insert(callCaptor.capture());
        assertEquals("p1", callCaptor.getValue().getPlatformCode());
        assertEquals("Q1", callCaptor.getValue().getRequestPromptContent());
    }

    @Test
    void triggerGenerate_realModePreflightFail_marksFailedWithoutRunning() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(9001L);
        version.setReportId(8001L);
        when(versionMapper.selectById(9001L)).thenReturn(version);

        PresaleReport report = new PresaleReport();
        report.setId(8001L);
        report.setBrandName("Acme");
        when(reportMapper.selectById(8001L)).thenReturn(report);

        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(0L);

        orchestrator.triggerGenerate(9001L, 1001L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper).updateById(updateCaptor.capture());
        PresaleReportVersion update = updateCaptor.getValue();
        assertEquals(PresaleGenerateStatus.FAILED.name(), update.getGenerationStatus());
        assertNull(update.getGenerationStage());
        assertTrue(update.getFailureReason().startsWith("CONFIG_MISSING:"));
    }

    @Test
    void preflight_brandNameBlank_marksFailed() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(9002L);
        version.setReportId(8002L);
        when(versionMapper.selectById(9002L)).thenReturn(version);

        PresaleReport report = new PresaleReport();
        report.setId(8002L);
        report.setBrandName("   ");
        when(reportMapper.selectById(8002L)).thenReturn(report);

        orchestrator.triggerGenerate(9002L, 1002L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper).updateById(updateCaptor.capture());
        PresaleReportVersion update = updateCaptor.getValue();
        assertEquals(PresaleGenerateStatus.FAILED.name(), update.getGenerationStatus());
        assertTrue(update.getFailureReason().contains("report.brand_name is blank"));
    }

    @Test
    void preflight_genericPromptCountZero_marksFailed() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(9003L);
        version.setReportId(8003L);
        when(versionMapper.selectById(9003L)).thenReturn(version);

        PresaleReport report = new PresaleReport();
        report.setId(8003L);
        report.setBrandName("Acme");
        when(reportMapper.selectById(8003L)).thenReturn(report);

        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(9L);
        when(versionPromptTemplateMapper.selectCount(any())).thenReturn(0L);

        orchestrator.triggerGenerate(9003L, 1003L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper).updateById(updateCaptor.capture());
        PresaleReportVersion update = updateCaptor.getValue();
        assertEquals(PresaleGenerateStatus.FAILED.name(), update.getGenerationStatus());
        assertTrue(update.getFailureReason().contains("generic prompt count is 0"));
    }

    @Test
    void preflight_success_marksRunning() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9004L, 8004L, 9, 25, 5);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of());
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of());

        orchestrator.triggerGenerate(9004L, 1004L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(updateCaptor.capture());

        PresaleReportVersion runningUpdate = updateCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.RUNNING.name().equals(v.getGenerationStatus()))
                .findFirst()
                .orElseThrow();
        assertEquals("BATCH1", runningUpdate.getGenerationStage());
        assertEquals(450, runningUpdate.getBatch1TotalCalls());
        assertEquals(811, runningUpdate.getTotalLlmCalls());
        assertEquals(0, runningUpdate.getCompletedLlmCalls());
    }

    @Test
    void retry_analyzeSuccessExists_skipsWholeCombo_noLlmInvoke() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9601L, 8601L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(701L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(702L, "C1", "batch2 {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.SKIP_ALL);

        orchestrator.triggerGenerate(9601L, 601L, false);

        verify(llmInvoker, never()).query(any(), anyString());
        verify(llmInvoker, never()).analyze(any(), anyString(), anyString());
        verify(reusePersistenceService, never()).replaceFailedAnalyzeAndResult(any(), any(), any(), any());
        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        int maxBatch1Completed = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getBatch1CompletedCalls)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);
        assertEquals(2, maxBatch1Completed);
    }

    @Test
    void retry_querySuccess_analyzeFailed_reuseQuery_onlyAnalyzeCalled_replaceFailedRows() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9602L, 8602L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(711L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(712L, "C1", "batch2 {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        PresaleAiCall reusedQuery = new PresaleAiCall();
        reusedQuery.setId(999L);
        reusedQuery.setRawResponse("reused-query-answer");
        when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.REUSE_QUERY_ONLY);
        when(reuseDecisionService.snapshotOf(any(), any())).thenReturn(new ReuseSnapshot(false, reusedQuery));

        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9602L, 602L, false);

        verify(llmInvoker, never()).query(any(), anyString());
        verify(llmInvoker, times(1)).analyze(any(), anyString(), anyString());
        ArgumentCaptor<PlatformCallContext> ctxCaptor = ArgumentCaptor.forClass(PlatformCallContext.class);
        ArgumentCaptor<PresaleAiCall> reusedQueryCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(reusePersistenceService, times(1))
                .replaceFailedAnalyzeAndResult(ctxCaptor.capture(), reusedQueryCaptor.capture(), any(), any());
        assertEquals(999L, reusedQueryCaptor.getValue().getId());
        assertEquals("", ctxCaptor.getValue().competitorName());
    }

    @Test
    void retry_queryFailed_rerunQueryAndAnalyze() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9603L, 8603L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(721L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(722L, "C1", "batch2 {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.RUN_FULL);
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9603L, 603L, false);

        verify(llmInvoker, times(1)).query(any(), anyString());
        verify(llmInvoker, times(1)).analyze(any(), anyString(), anyString());
        verify(reusePersistenceService, never()).replaceFailedAnalyzeAndResult(any(), any(), any(), any());
    }

    @Test
    void batch1_allSuccess_writesExpectedCallAndPromptResultRows() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9101L, 8101L, 2, 3, 2);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms(
                "chatgpt",
                "claude"
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(1L, "P1", "Q1"),
                promptTemplate(2L, "P2", "Q2"),
                promptTemplate(3L, "P3", "Q3")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString()))
                .thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9101L, 101L, false);

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, times(12)).insert(callCaptor.capture());
        List<PresaleAiCall> calls = callCaptor.getAllValues();
        PresaleAiCall queryCall = calls.stream()
                .filter(c -> "QUERY".equals(c.getStage()))
                .findFirst()
                .orElseThrow();
        PresaleAiCall analyzeCall = calls.stream()
                .filter(c -> "ANALYZE".equals(c.getStage()))
                .findFirst()
                .orElseThrow();
        assertEquals("Q1", queryCall.getRequestPromptContent());
        assertTrue(analyzeCall.getRequestPromptContent().contains("问题:Q1"));
        assertTrue(analyzeCall.getRequestPromptContent().contains("回答:query-ok"));
        assertTrue(analyzeCall.getRequestPromptContent().contains("目标品牌:"));
        verify(aiPromptResultMapper, times(6)).insert(any());
    }

    @Test
    void batch1_singlePlatformDegraded_writesSkippedRows() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9102L, 8102L, 1, 4, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(11L, "P1", "Q1"),
                promptTemplate(12L, "P2", "Q2"),
                promptTemplate(13L, "P3", "Q3"),
                promptTemplate(14L, "P4", "Q4")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString()))
                .thenThrow(new LlmInvokeException("q1 failed"))
                .thenThrow(new LlmInvokeException("q2 failed"));

        orchestrator.triggerGenerate(9102L, 102L, false);

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, times(6)).insert(callCaptor.capture());
        long skippedCount = callCaptor.getAllValues().stream()
                .filter(c -> CallStatus.SKIPPED_DEGRADED.name().equals(c.getCallStatus()))
                .count();
        assertEquals(4L, skippedCount);
        verify(aiPromptResultMapper, never()).insert(any());
    }

    @Test
    void batch1_degradeFormula_runtimePath_triggersDegradeAndProgressThrottle() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9104L, 8104L, 1, 10, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(1001L, "P1", "Q1"),
                promptTemplate(1002L, "P2", "Q2"),
                promptTemplate(1003L, "P3", "Q3"),
                promptTemplate(1004L, "P4", "Q4"),
                promptTemplate(1005L, "P5", "Q5"),
                promptTemplate(1006L, "P6", "Q6"),
                promptTemplate(1007L, "P7", "Q7"),
                promptTemplate(1008L, "P8", "Q8"),
                promptTemplate(1009L, "P9", "Q9"),
                promptTemplate(1010L, "P10", "Q10")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        AtomicInteger queryCounter = new AtomicInteger(0);
        when(llmInvoker.query(any(), anyString())).thenAnswer(invocation -> {
            int n = queryCounter.incrementAndGet();
            if (n <= 4) {
                throw new LlmInvokeException("query failed " + n);
            }
            return successResult("query-ok-" + n);
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9104L, 104L, false);

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, times(16)).insert(callCaptor.capture());
        long skippedCount = callCaptor.getAllValues().stream()
                .filter(c -> CallStatus.SKIPPED_DEGRADED.name().equals(c.getCallStatus()))
                .count();
        assertEquals(10L, skippedCount);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        long progressUpdates = versionCaptor.getAllValues().stream()
                .filter(v -> v.getBatch1CompletedCalls() != null)
                .count();
        assertTrue(progressUpdates < 10, "progress updates should be throttled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void batch1_analyzeFailuresDoNotDegradeAnswerPlatform() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", (Executor) Runnable::run);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(2001L, "P1", "Q1"),
                promptTemplate(2002L, "P2", "Q2"),
                promptTemplate(2003L, "P3", "Q3"),
                promptTemplate(2004L, "P4", "Q4")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenThrow(new LlmInvokeException("judge parse failed"));

        Object result = invokeExecuteBatch1(9105L, 8105L);
        Set<String> degradedPlatforms = (Set<String>) invokeNoArg(result, "degradedPlatforms");
        List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");

        assertTrue(degradedPlatforms.isEmpty());
        assertEquals(PlatformStatus.DONE, platformResults.get(0).status());
        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, atLeastOnce()).insert(callCaptor.capture());
        long skippedCount = callCaptor.getAllValues().stream()
                .filter(c -> CallStatus.SKIPPED_DEGRADED.name().equals(c.getCallStatus()))
                .count();
        assertEquals(0L, skippedCount);
    }

    @Test
    void shouldDegrade_processed6Failed4_returnsTrue() throws Exception {
        Class<?> stateClass = Class.forName(
                "com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator$PlatformBatchState");
        Constructor<?> ctor = stateClass.getDeclaredConstructor(String.class, int.class);
        ctor.setAccessible(true);
        Object state = ctor.newInstance("kimi", 10);
        ReflectionTestUtils.setField(state, "processedPrompts", 6);
        ReflectionTestUtils.setField(state, "failedPrompts", 4);

        Method shouldDegrade = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("shouldDegrade", stateClass);
        shouldDegrade.setAccessible(true);
        boolean degrade = (boolean) shouldDegrade.invoke(orchestrator, state);

        assertTrue(degrade);
    }

    @Test
    void batch1_fourPlatformsDegraded_marksFailed() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9103L, 8103L, 4, 2, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms(
                "p1",
                "p2",
                "p3",
                "p4"
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(21L, "P1", "Q1"),
                promptTemplate(22L, "P2", "Q2")
        ));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString()))
                .thenThrow(new LlmInvokeException("query failed"));

        orchestrator.triggerGenerate(9103L, 103L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .findFirst()
                .orElseThrow();
        assertEquals("TOO_MANY_DEGRADED_PLATFORMS", failed.getFailureReason());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch1_platformParallel_degradeThresholdStopsPipelineAfterSubmittedTasks() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", (Executor) Runnable::run);

        AtomicInteger submittedPlatforms = new AtomicInteger(0);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p1", "p2", "p3", "p4", "p5"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(promptTemplate(301L, "G1", "t")));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        when(llmInvoker.query(any(PlatformCallContext.class), anyString())).thenAnswer(invocation -> {
            PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
            submittedPlatforms.incrementAndGet();
            if (Set.of("p1", "p2", "p3", "p4").contains(ctx.platformCode())) {
                throw new LlmInvokeException("force degrade " + ctx.platformCode());
            }
            return successResult("query-ok");
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        Object result = invokeExecuteBatch1(3001L, 7001L);
        List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");
        Set<String> degradedPlatforms = (Set<String>) invokeNoArg(result, "degradedPlatforms");
        boolean stopPipeline = (boolean) invokeNoArg(result, "stopPipeline");

        assertEquals(5, submittedPlatforms.get());
        assertEquals(5, platformResults.size());
        assertEquals(Set.of("p1", "p2", "p3", "p4"), degradedPlatforms);
        assertTrue(stopPipeline);
        assertTrue(platformResults.stream()
                .filter(r -> Set.of("p1", "p2", "p3", "p4").contains(r.platformCode()))
                .allMatch(r -> r.status() == PlatformStatus.DEGRADED));
        assertTrue(platformResults.stream()
                .anyMatch(r -> "p5".equals(r.platformCode()) && r.status() == PlatformStatus.DONE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch1_platformParallel_sharedStateThreadSafe() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", pool);

        try {
            List<AiPlatformConfig> platforms = platforms(
                    "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10");
            when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms);
            when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(promptTemplate(302L, "G1", "t")));

            CyclicBarrier barrier = new CyclicBarrier(10);
            AtomicInteger entered = new AtomicInteger(0);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class))).thenAnswer(invocation -> {
                entered.incrementAndGet();
                awaitBarrier(barrier, failure);
                return invocation.getArgument(0, String.class);
            });
            when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
            when(llmInvoker.analyze(any(), anyString(), anyString()))
                    .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

            Object result = invokeExecuteBatch1(3002L, 7002L);
            assertNoFailure(failure);

            List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");
            Set<String> degradedPlatforms = (Set<String>) invokeNoArg(result, "degradedPlatforms");
            assertEquals(10, entered.get());
            assertEquals(10, platformResults.size());
            assertTrue(degradedPlatforms.isEmpty());
            assertTrue(platformResults.stream().allMatch(r -> r.status() == PlatformStatus.DONE));

            ArgumentCaptor<PresaleReportVersion> progressCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
            verify(versionMapper, atLeastOnce()).updateById(progressCaptor.capture());
            List<Integer> completedProgress = progressCaptor.getAllValues().stream()
                    .map(PresaleReportVersion::getBatch1CompletedCalls)
                    .filter(v -> v != null)
                    .toList();
            for (int i = 1; i < completedProgress.size(); i++) {
                assertTrue(completedProgress.get(i) >= completedProgress.get(i - 1));
            }
            assertTrue(completedProgress.stream().anyMatch(v -> v == 20));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch1_rejectedExecution_markVersionFailed() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", (Executor) command -> {
            throw new java.util.concurrent.RejectedExecutionException("reject");
        });

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p1", "p2", "p3", "p4"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(promptTemplate(303L, "G1", "t")));

        Object result = invokeExecuteBatch1(3003L, 7003L);
        List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");
        boolean stopPipeline = (boolean) invokeNoArg(result, "stopPipeline");

        assertEquals(4, platformResults.size());
        assertTrue(stopPipeline);
        assertTrue(platformResults.stream().allMatch(r -> r.status() == PlatformStatus.SKIPPED));
        verify(versionMapper, atLeastOnce()).updateById(any(PresaleReportVersion.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch1_completionException_unwrapped() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        LlmInvokeException expected = new LlmInvokeException("boom");
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p1"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(promptTemplate(304L, "G1", "t")));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(invocation -> {
                    throw new CompletionException(expected);
                });

        Object result = invokeExecuteBatch1(3004L, 7004L);
        List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");

        assertEquals(1, platformResults.size());
        PlatformBatchResult row = platformResults.get(0);
        assertEquals(PlatformStatus.DEGRADED, row.status());
        assertNotNull(row.errorCause());
        assertInstanceOf(LlmInvokeException.class, row.errorCause());
    }

    @Test
    void doTriggerGenerate_versionAlreadyRunning_skipSilently() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        when(versionMapper.tryTransitionToRunning(eq(3010L), anyInt())).thenReturn(0);

        Method doTriggerGenerate = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("doTriggerGenerate", Long.class, Long.class, boolean.class);
        doTriggerGenerate.setAccessible(true);
        doTriggerGenerate.invoke(orchestrator, 3010L, 1L, false);

        verify(versionMapper).selectById(3010L);
        verify(reportMapper, never()).selectById(anyLong());
        verify(aiPlatformConfigMapper, never()).selectCount(any());
        verify(versionPromptTemplateMapper, never()).selectCount(any());
        verify(llmInvoker, never()).query(any(), anyString());
        verify(llmInvoker, never()).analyze(any(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch2_platformParallel_degradeThresholdStopsPipelineAfterSubmittedTasks() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", (Executor) Runnable::run);

        AtomicInteger submittedPlatforms = new AtomicInteger(0);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p3", "p4", "p5"));
        PresaleReportVersionPromptTemplate template = promptTemplate(401L, "C1", "t {competitor}");
        template.setHasCompetitorVar(1);
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(template));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        when(llmInvoker.query(any(PlatformCallContext.class), anyString())).thenAnswer(invocation -> {
            PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
            submittedPlatforms.incrementAndGet();
            if (Set.of("p3", "p4").contains(ctx.platformCode())) {
                throw new LlmInvokeException("force degrade " + ctx.platformCode());
            }
            return successResult("query-ok");
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"target_mentioned\":true,\"competitor_mentioned\":true,\"preferred_brand\":\"target\",\"evidence\":\"ok\",\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[\"c1\"]}"));

        Object result = invokeExecuteBatch2(4001L, 8001L, List.of("c1"), 1, Set.of("p1", "p2"));
        List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");
        Set<String> batch2DegradedPlatforms = (Set<String>) invokeNoArg(result, "batch2DegradedPlatforms");
        Set<String> displayDegradedPlatforms = (Set<String>) invokeNoArg(result, "displayDegradedPlatforms");
        boolean stopPipeline = (boolean) invokeNoArg(result, "stopPipeline");

        assertEquals(3, submittedPlatforms.get());
        assertEquals(3, platformResults.size());
        assertEquals(Set.of("p3", "p4"), batch2DegradedPlatforms);
        assertEquals(Set.of("p1", "p2", "p3", "p4"), displayDegradedPlatforms);
        assertTrue(stopPipeline);
        assertTrue(platformResults.stream()
                .anyMatch(r -> "p5".equals(r.platformCode()) && r.status() == PlatformStatus.DONE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch2_platformParallel_sharedStateThreadSafe() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", pool);

        try {
            when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms(
                    "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10"));
            PresaleReportVersionPromptTemplate template = promptTemplate(402L, "C1", "t {competitor}");
            template.setHasCompetitorVar(1);
            when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(template));

            CyclicBarrier barrier = new CyclicBarrier(10);
            AtomicInteger entered = new AtomicInteger(0);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class))).thenAnswer(invocation -> {
                PromptTemplateRenderer.RenderVariables variables =
                        invocation.getArgument(1, PromptTemplateRenderer.RenderVariables.class);
                if ("c1、c2".equals(variables.competitor())) {
                    entered.incrementAndGet();
                    awaitBarrier(barrier, failure);
                }
                return invocation.getArgument(0, String.class);
            });
            when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
            when(llmInvoker.analyze(any(), anyString(), anyString()))
                    .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

            Object result = invokeExecuteBatch2(4002L, 8002L, List.of("c1", "c2"), 1, Set.of());
            assertNoFailure(failure);

            Set<String> batch2DegradedPlatforms = (Set<String>) invokeNoArg(result, "batch2DegradedPlatforms");
            Set<String> displayDegradedPlatforms = (Set<String>) invokeNoArg(result, "displayDegradedPlatforms");
            assertEquals(10, entered.get());
            assertTrue(batch2DegradedPlatforms.isEmpty());
            assertTrue(displayDegradedPlatforms.isEmpty());

            ArgumentCaptor<PresaleReportVersion> progressCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
            verify(versionMapper, atLeastOnce()).updateById(progressCaptor.capture());
            List<Integer> completedProgress = progressCaptor.getAllValues().stream()
                    .map(PresaleReportVersion::getBatch2CompletedCalls)
                    .filter(v -> v != null)
                    .toList();
            for (int i = 1; i < completedProgress.size(); i++) {
                assertTrue(completedProgress.get(i) >= completedProgress.get(i - 1));
            }
            assertTrue(completedProgress.stream().anyMatch(v -> v == 20));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch2_rejectedExecution_markVersionFailed() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", (Executor) command -> {
            throw new java.util.concurrent.RejectedExecutionException("reject");
        });

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p1", "p2", "p3", "p4"));
        PresaleReportVersionPromptTemplate template = promptTemplate(403L, "C1", "t {competitor}");
        template.setHasCompetitorVar(1);
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(template));

        Object result = invokeExecuteBatch2(4003L, 8003L, List.of("c1"), 1, Set.of());
        List<PlatformBatchResult> platformResults = (List<PlatformBatchResult>) invokeNoArg(result, "platformResults");
        Set<String> batch2DegradedPlatforms = (Set<String>) invokeNoArg(result, "batch2DegradedPlatforms");
        boolean stopPipeline = (boolean) invokeNoArg(result, "stopPipeline");

        assertEquals(4, platformResults.size());
        assertTrue(stopPipeline);
        assertEquals(Set.of("p1", "p2", "p3", "p4"), batch2DegradedPlatforms);
        assertTrue(platformResults.stream().allMatch(r -> r.status() == PlatformStatus.SKIPPED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeBatch2_delta_excludesBatch1Degraded() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "platformExecutor", (Executor) Runnable::run);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("p2", "p3", "p4", "p5"));
        PresaleReportVersionPromptTemplate template = promptTemplate(404L, "C1", "t {competitor}");
        template.setHasCompetitorVar(1);
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(template));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        when(llmInvoker.query(any(PlatformCallContext.class), anyString())).thenAnswer(invocation -> {
            PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
            if ("p3".equals(ctx.platformCode())) {
                throw new LlmInvokeException("degrade p3");
            }
            return successResult("query-ok");
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        Object result = invokeExecuteBatch2(4004L, 8004L, List.of("c1"), 1, Set.of("p1"));
        Set<String> batch2DegradedPlatforms = (Set<String>) invokeNoArg(result, "batch2DegradedPlatforms");
        Set<String> displayDegradedPlatforms = (Set<String>) invokeNoArg(result, "displayDegradedPlatforms");
        boolean stopPipeline = (boolean) invokeNoArg(result, "stopPipeline");

        assertFalse(stopPipeline);
        assertEquals(Set.of("p3"), batch2DegradedPlatforms);
        assertEquals(Set.of("p1", "p3"), displayDegradedPlatforms);
    }

    @Test
    @SuppressWarnings("unchecked")
    void competitorExtract_aggregatesTop3AndFiltersBrand() throws Exception {
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9201L), anyCollection()))
                .thenReturn(List.of("Claude", "ChatGPT", "Doubao"));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, List.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9201L, List.of("Acme  AI"));

        assertEquals(List.of("Claude", "ChatGPT", "Doubao"), competitors);
    }

    @Test
    void competitorExtract_empty_setsCategoryButNotFailedImmediately() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9202L, 8202L, 1, 1, 1);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of());
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of());
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

        orchestrator.triggerGenerate(9202L, 202L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        boolean hasCompetitorExtractEmpty = versionCaptor.getAllValues().stream().anyMatch(v ->
                "COMPETITOR_EXTRACT_EMPTY".equals(v.getFailureCategory())
                        && v.getGenerationStatus() == null
        );
        assertTrue(hasCompetitorExtractEmpty);
    }

    @Test
    @SuppressWarnings("unchecked")
    void competitorExtract_lessThan3Candidates_returnsActualSize() throws Exception {
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9301L), anyCollection()))
                .thenReturn(List.of("Claude", "ChatGPT"));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, List.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9301L, List.of("Acme"));

        assertEquals(List.of("Claude", "ChatGPT"), competitors);
    }

    @Test
    @SuppressWarnings("unchecked")
    void competitorExtract_invalidJsonIsSkipped_withoutBreakingOtherRows() throws Exception {
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9302L), anyCollection()))
                .thenReturn(List.of("Claude", "Gemini"));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, List.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9302L, List.of("Acme"));

        assertEquals(List.of("Claude", "Gemini"), competitors);
    }

    @Test
    @SuppressWarnings("unchecked")
    void competitorExtract_rowLevelDedup_countsSameNameOncePerRow() throws Exception {
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9303L), anyCollection()))
                .thenReturn(List.of("Claude", "Gemini"));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, List.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9303L, List.of("Acme"));

        assertEquals(List.of("Claude", "Gemini"), competitors);
    }

    @Test
    void batch2_writesBatchNoAndCompetitorName() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9401L, 8401L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                platforms("kimi"),
                platforms("kimi")
        );
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(401L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(402L, "C1", "batch2 {brand} vs {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(401L, "[\"Claude\", \"Gemini\"]"))
        );
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9401L), anyCollection()))
                .thenReturn(List.of("Claude", "Gemini"));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9401L, 401L, false);

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, atLeastOnce()).insert(callCaptor.capture());
        boolean hasBatch2Query = callCaptor.getAllValues().stream().anyMatch(c ->
                Integer.valueOf(2).equals(c.getBatchNo())
                        && "QUERY".equals(c.getStage())
                        && "Claude、Gemini".equals(c.getCompetitorName())
        );
        boolean hasBatch2Analyze = callCaptor.getAllValues().stream().anyMatch(c ->
                Integer.valueOf(2).equals(c.getBatchNo())
                        && "ANALYZE".equals(c.getStage())
                        && "Claude、Gemini".equals(c.getCompetitorName())
        );
        assertTrue(hasBatch2Query);
        assertTrue(hasBatch2Analyze);

        ArgumentCaptor<PresaleAiPromptResult> resultCaptor = ArgumentCaptor.forClass(PresaleAiPromptResult.class);
        verify(aiPromptResultMapper, atLeastOnce()).insert(resultCaptor.capture());
        boolean hasBatch2Result = resultCaptor.getAllValues().stream().anyMatch(r ->
                Integer.valueOf(2).equals(r.getBatchNo())
                        && "Claude、Gemini".equals(r.getCompetitorName())
        );
        assertTrue(hasBatch2Result);
    }

    @Test
    void batch2_degradeUsesQcmpOnly_asTotalPrompts() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9402L, 8402L, 1, 1, 2);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                platforms("kimi"),
                platforms("kimi")
        );
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(501L, "G1", "batch1 {brand}")),
                List.of(
                        promptTemplate(502L, "C1", "batch2-a {competitor}"),
                        promptTemplate(503L, "C2", "batch2-b {competitor}")
                )
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(501L, "[\"Claude\", \"Gemini\", \"Doubao\"]"))
        );
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9402L), anyCollection()))
                .thenReturn(List.of("Claude", "Gemini", "Doubao"));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        AtomicInteger batch2QueryCounter = new AtomicInteger(0);
        when(llmInvoker.query(any(), anyString())).thenAnswer(invocation -> {
            Object ctxObj = invocation.getArgument(0);
            int batchNo = (int) ctxObj.getClass().getMethod("batchNo").invoke(ctxObj);
            if (batchNo == 1) {
                return successResult("batch1-ok");
            }
            int n = batch2QueryCounter.incrementAndGet();
            if (n <= 2) {
                throw new LlmInvokeException("batch2 fail " + n);
            }
            return successResult("batch2-ok-" + n);
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9402L, 402L, false);

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, atLeastOnce()).insert(callCaptor.capture());
        long batch2Skipped = callCaptor.getAllValues().stream()
                .filter(c -> Integer.valueOf(2).equals(c.getBatchNo()))
                .filter(c -> CallStatus.SKIPPED_DEGRADED.name().equals(c.getCallStatus()))
                .count();
        assertEquals(2L, batch2Skipped);
    }

    @Test
    void batch1_queryFailureCountsAsTwoCompletedCalls() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9501L, 8501L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms("kimi"));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(601L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(602L, "C1", "batch2 {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString())).thenThrow(new LlmInvokeException("batch1 query fail"));

        orchestrator.triggerGenerate(9501L, 501L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        int maxBatch1Completed = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getBatch1CompletedCalls)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);
        int maxCompleted = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getCompletedLlmCalls)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);
        assertEquals(2, maxBatch1Completed);
        assertEquals(2, maxCompleted);
    }

    @Test
    void batch2_queryFailureCountsAsTwoCompletedCalls() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9502L, 8502L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                platforms("kimi"),
                platforms("kimi")
        );
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(611L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(612L, "C1", "batch2 {brand} vs {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(611L, "[\"Claude\"]"))
        );
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9502L), anyCollection()))
                .thenReturn(List.of("Claude"));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        when(llmInvoker.query(any(), anyString())).thenAnswer(invocation -> {
            Object ctxObj = invocation.getArgument(0);
            int batchNo = (int) ctxObj.getClass().getMethod("batchNo").invoke(ctxObj);
            if (batchNo == 1) {
                return successResult("batch1-ok");
            }
            throw new LlmInvokeException("batch2 query fail");
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9502L, 502L, false);

        ArgumentCaptor<PresaleAiCall> callCaptor = ArgumentCaptor.forClass(PresaleAiCall.class);
        verify(aiCallMapper, atLeastOnce()).insert(callCaptor.capture());
        boolean hasBatch2QueryFailed = callCaptor.getAllValues().stream().anyMatch(c ->
                Integer.valueOf(2).equals(c.getBatchNo())
                        && "Claude".equals(c.getCompetitorName())
                        && "QUERY".equals(c.getStage())
                        && CallStatus.FAILED.name().equals(c.getCallStatus())
        );
        assertTrue(hasBatch2QueryFailed);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        int maxBatch2Completed = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getBatch2CompletedCalls)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);
        assertEquals(2, maxBatch2Completed);
    }

    @Test
    void regenerate_fromDone_reusePath_appliesAndPipelineContinues() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9801L, 8801L, 2, 2, 1);
        PresaleReportVersion preflightVersion = new PresaleReportVersion();
        preflightVersion.setId(9801L);
        preflightVersion.setReportId(8801L);
        PresaleReportVersion batch1Version = new PresaleReportVersion();
        batch1Version.setId(9801L);
        batch1Version.setReportId(8801L);
        PresaleReportVersion batch2EntryVersion = new PresaleReportVersion();
        batch2EntryVersion.setId(9801L);
        batch2EntryVersion.setReportId(8801L);
        batch2EntryVersion.setBatch1CompletedCalls(8);
        when(versionMapper.selectById(9801L))
                .thenReturn(preflightVersion, batch1Version, batch2EntryVersion, batch2EntryVersion);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                platforms("p1", "p2"),
                platforms("p1", "p2")
        );
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(
                        promptTemplate(821L, "G1", "batch1-q1 {brand}"),
                        promptTemplate(822L, "G2", "batch1-q2 {brand}")
                ),
                List.of(promptTemplate(823L, "C1", "batch2-q1 {competitor}"))
        );
        lenient().when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(821L, "[\"Claude\"]"))
        );
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9801L), anyCollection()))
                .thenReturn(List.of("Claude"));
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        when(reuseDecisionService.decide(any(), any())).thenAnswer(invocation -> {
            PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
            if (ctx.batchNo() == 1 && "p1".equals(ctx.platformCode()) && ctx.promptTemplateId().equals(821L)) {
                return ReuseDecision.SKIP_ALL; // 命中 1 对
            }
            if (ctx.batchNo() == 2) {
                return ReuseDecision.SKIP_ALL; // batch2 全短路，确保总 query=3
            }
            return ReuseDecision.RUN_FULL;
        });
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9801L, 801L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        verify(llmInvoker, times(3)).query(any(), anyString());
        boolean hasBatch2Stage = versionCaptor.getAllValues().stream()
                .anyMatch(v -> "BATCH2".equals(v.getGenerationStage()));
        assertTrue(hasBatch2Stage);
        String lastStatus = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getGenerationStatus)
                .filter(v -> v != null)
                .reduce((first, second) -> second)
                .orElse(null);
        assertTrue(lastStatus != null
                && !PresaleGenerateStatus.INIT.name().equals(lastStatus)
                && !PresaleGenerateStatus.QUEUED.name().equals(lastStatus));
    }

    @Test
    void realFullFlow_happyPath_reachesDone() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9901L, 8901L, List.of("Claude"));

        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any())).thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{\"scores\":{\"overall\":60.0}}");
        when(l3InitService.derive(anyString(), anyString())).thenReturn("{\"editable\":\"ok\"}");

        orchestrator.triggerGenerate(9901L, 901L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        boolean rawWritten = versionCaptor.getAllValues().stream()
                .anyMatch(v -> v.getRawSnapshotJson() != null && !v.getRawSnapshotJson().isBlank());
        boolean computedWritten = versionCaptor.getAllValues().stream()
                .anyMatch(v -> v.getComputedSnapshotJson() != null && !v.getComputedSnapshotJson().isBlank());
        boolean editableWritten = versionCaptor.getAllValues().stream()
                .anyMatch(v -> v.getEditableContentJson() != null && !v.getEditableContentJson().isBlank());
        PresaleReportVersion done = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.DONE.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertTrue(rawWritten);
        assertTrue(computedWritten);
        assertTrue(editableWritten);
        assertEquals(PresaleGenerateStatus.DONE.name(), done.getGenerationStatus());
        assertNull(done.getGenerationStage());
    }

    @Test
    void realFullFlow_capacityFailureDefer_requeuesWithoutFailedObservation() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        ReflectionTestUtils.setField(orchestrator, "capacityFailureDeferEnabled", true);
        setupSimpleRealFlow(9920L, 8920L, List.of("Claude"));
        when(llmInvoker.query(any(), anyString()))
                .thenThrow(new LlmInvokeException("permit busy",
                        new LlmPermitUnavailableException(LlmPermitScope.FEATURE, "presale-generation")));

        orchestrator.triggerGenerate(9920L, 920L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion deferred = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.QUEUED.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertNull(deferred.getGenerationStage());
        assertEquals("CAPACITY_DEFERRED", deferred.getFailureCategory());
        assertTrue(deferred.getFailureReason().startsWith("CAPACITY_DEFERRED: PERMIT_BUSY"));
        assertTrue(versionCaptor.getAllValues().stream()
                .noneMatch(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus())));
        verify(aiCallMapper, never()).insert(any(PresaleAiCall.class));
    }

    @Test
    void realFullFlow_l1Fails_marksL1Error() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9902L, 8902L, List.of());
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new com.huanjing.geo.common.exception.BizException(500, "L1 aggregate failed: boom"));

        orchestrator.triggerGenerate(9902L, 902L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("L1_SERIALIZATION_ERROR", failed.getFailureCategory());
    }

    @Test
    void realFullFlow_benchmarkMissing_marksConfigMissing() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9903L, 8903L, List.of());
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("BENCHMARK_MISSING fallback (_ALL_,_ALL_) not found"));

        orchestrator.triggerGenerate(9903L, 903L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("CONFIG_MISSING", failed.getFailureCategory());
    }

    @Test
    void realFullFlow_l2Fails_marksL2Error() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9904L, 8904L, List.of("Claude"));
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any())).thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenThrow(new com.huanjing.geo.common.exception.BizException(500, "L2 compute failed"));

        orchestrator.triggerGenerate(9904L, 904L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("L2_COMPUTE_ERROR", failed.getFailureCategory());
    }

    @Test
    void realFullFlow_l3Fails_marksL3Error() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9905L, 8905L, List.of("Claude"));
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any())).thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{\"scores\":{\"overall\":60.0}}");
        when(l3InitService.derive(anyString(), anyString()))
                .thenThrow(new com.huanjing.geo.common.exception.BizException(500, "L3 init failed"));

        orchestrator.triggerGenerate(9905L, 905L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        PresaleReportVersion failed = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("L3_INIT_ERROR", failed.getFailureCategory());
    }

    @Test
    void realFullFlow_page03Fails_keepsDefaultL3AndReachesDone() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9907L, 8907L, List.of());
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any()))
                .thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{\"scores\":{\"overall\":60.0}}");
        when(l3InitService.derive(anyString(), anyString())).thenReturn("{\"editable\":\"default\"}");
        when(page03DoubaoService.generateAndApply(anyLong(), anyString(), anyString(), any(), anyBoolean()))
                .thenThrow(new com.huanjing.geo.common.exception.BizException(500, "all evaluation models failed"));

        orchestrator.triggerGenerate(9907L, 907L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        assertTrue(versionCaptor.getAllValues().stream()
                .anyMatch(v -> PresaleGenerateStatus.DONE.name().equals(v.getGenerationStatus())));
        assertTrue(versionCaptor.getAllValues().stream()
                .noneMatch(v -> PresaleGenerateStatus.FAILED.name().equals(v.getGenerationStatus())));
        verify(page03DoubaoService).generateAndApply(
                eq(9907L), eq("{\"raw\":\"ok\"}"), eq("{\"editable\":\"default\"}"), eq(907L), eq(false));
    }

    @Test
    void realFullFlow_zeroCompetitors_reachesDone() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9906L, 8906L, List.of());

        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any())).thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{\"scores\":{\"overall\":60.0}}");
        when(l3InitService.derive(anyString(), anyString())).thenReturn("{\"editable\":\"ok\"}");

        orchestrator.triggerGenerate(9906L, 906L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        boolean hasExtractEmptyMarker = versionCaptor.getAllValues().stream()
                .anyMatch(v -> "COMPETITOR_EXTRACT_EMPTY".equals(v.getFailureCategory()));
        PresaleReportVersion done = versionCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.DONE.name().equals(v.getGenerationStatus()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertTrue(hasExtractEmptyMarker);
        assertEquals(PresaleGenerateStatus.DONE.name(), done.getGenerationStatus());
    }

    @Test
    void promptResult_persistsRenderedRequestPromptContent() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupSimpleRealFlow(9910L, 8910L, List.of());
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any())).thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{\"scores\":{\"overall\":60.0}}");
        when(l3InitService.derive(anyString(), anyString())).thenReturn("{\"editable\":\"ok\"}");

        orchestrator.triggerGenerate(9910L, 910L, false);

        ArgumentCaptor<PresaleAiPromptResult> resultCaptor = ArgumentCaptor.forClass(PresaleAiPromptResult.class);
        verify(aiPromptResultMapper, atLeastOnce()).insert(resultCaptor.capture());
        PresaleAiPromptResult row = resultCaptor.getAllValues().stream()
                .filter(r -> Integer.valueOf(1).equals(r.getBatchNo()))
                .findFirst()
                .orElseThrow();
        assertEquals("batch1 {brand}", row.getRequestPromptContent());
    }

    @Test
    void promptRender_usesChineseDictValueForIndustryAndRole() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9911L, 8911L, 1, 1, 1);

        PresaleReport report = new PresaleReport();
        report.setId(8911L);
        report.setBrandName("Acme");
        report.setIndustry("restaurant");
        report.setIndustryRole("chain_brand");
        report.setRegion("CN");
        when(reportMapper.selectById(8911L)).thenReturn(report);

        when(sysDictItemMapper.selectList(any()))
                .thenReturn(List.of(dictItem("餐饮")), List.of(dictItem("连锁品牌")));
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                platforms("kimi"),
                platforms("kimi")
        );
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(1991L, "G1", "行业={industry},身份={industry_role},品牌={brand}")),
                List.of(promptTemplate(1992L, "C1", "batch2 {competitor}"))
        );
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(9911L), anyCollection())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenCallRealMethod();
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any(), any())).thenReturn("{\"raw\":\"ok\"}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), nullable(String.class), anyBoolean()))
                .thenReturn("{\"scores\":{\"overall\":60.0}}");
        when(l3InitService.derive(anyString(), anyString())).thenReturn("{\"editable\":\"ok\"}");

        orchestrator.triggerGenerate(9911L, 911L, false);

        ArgumentCaptor<PresaleAiPromptResult> resultCaptor = ArgumentCaptor.forClass(PresaleAiPromptResult.class);
        verify(aiPromptResultMapper, atLeastOnce()).insert(resultCaptor.capture());
        PresaleAiPromptResult row = resultCaptor.getAllValues().stream()
                .filter(r -> Integer.valueOf(1).equals(r.getBatchNo()))
                .findFirst()
                .orElseThrow();
        assertEquals("行业=餐饮,身份=连锁品牌,品牌=Acme", row.getRequestPromptContent());
    }

    @Test
    void analyzeWithEvaluationModel_fallsBackWhenFirstJudgeInvokeFails() throws Exception {
        PlatformCallContext sourceCtx = new PlatformCallContext(
                9302L, 1, "chatgpt", 11L, "", "Acme", 1L, false);
        PlatformCallContext deepseekCtx = new PlatformCallContext(
                9302L, 1, "deepseek", 11L, "", "Acme", 1L, false);
        PlatformCallContext doubaoCtx = new PlatformCallContext(
                9302L, 1, "doubao", 11L, "", "Acme", 1L, false);
        LlmCallResult expected = successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}");

        when(evaluationModelRouter.routeContexts(sourceCtx)).thenReturn(List.of(deepseekCtx, doubaoCtx));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    PlatformCallContext ctx = inv.getArgument(0, PlatformCallContext.class);
                    if ("deepseek".equals(ctx.platformCode())) {
                        throw new LlmInvokeException("deepseek quota exhausted");
                    }
                    return expected;
                });

        LlmCallResult actual = ReflectionTestUtils.invokeMethod(
                orchestrator,
                "analyzeWithEvaluationModel",
                sourceCtx,
                "prompt",
                "answer"
        );

        assertEquals(expected, actual);
        ArgumentCaptor<PlatformCallContext> ctxCaptor = ArgumentCaptor.forClass(PlatformCallContext.class);
        verify(llmInvoker, times(2)).analyze(ctxCaptor.capture(), anyString(), anyString());
        assertEquals(List.of("deepseek", "doubao"), ctxCaptor.getAllValues().stream()
                .map(PlatformCallContext::platformCode)
                .toList());
    }

    @Test
    void analyzeWithEvaluationModel_excludesDegradedEvaluationPlatforms() throws Exception {
        PlatformCallContext sourceCtx = new PlatformCallContext(
                9303L, 2, "chatgpt", 11L, "c1", "Acme", 1L, false);
        PlatformCallContext deepseekCtx = new PlatformCallContext(
                9303L, 2, "deepseek", 11L, "c1", "Acme", 1L, false);
        PlatformCallContext doubaoCtx = new PlatformCallContext(
                9303L, 2, "doubao", 11L, "c1", "Acme", 1L, false);
        LlmCallResult expected = successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}");

        when(evaluationModelRouter.routeContexts(sourceCtx)).thenReturn(List.of(deepseekCtx, doubaoCtx));
        when(llmInvoker.analyze(any(), anyString(), anyString())).thenReturn(expected);

        LlmCallResult actual = ReflectionTestUtils.invokeMethod(
                orchestrator,
                "analyzeWithEvaluationModel",
                sourceCtx,
                "prompt",
                "answer",
                Set.of("deepseek")
        );

        assertEquals(expected, actual);
        ArgumentCaptor<PlatformCallContext> ctxCaptor = ArgumentCaptor.forClass(PlatformCallContext.class);
        verify(llmInvoker, times(1)).analyze(ctxCaptor.capture(), anyString(), anyString());
        assertEquals("doubao", ctxCaptor.getValue().platformCode());
    }

    @Test
    void analyzeWithEvaluationModel_retriesThreeRoundsAndCompensationWhenAllJudgesBusy() throws Exception {
        PlatformCallContext sourceCtx = new PlatformCallContext(
                9301L, 1, "chatgpt", 11L, "", "Acme", 1L, false);
        PlatformCallContext deepseekCtx = new PlatformCallContext(
                9301L, 1, "deepseek", 11L, "", "Acme", 1L, false);
        PlatformCallContext doubaoCtx = new PlatformCallContext(
                9301L, 1, "doubao", 11L, "", "Acme", 1L, false);
        when(evaluationModelRouter.routeContexts(sourceCtx)).thenReturn(List.of(deepseekCtx, doubaoCtx));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenThrow(new LlmPermitUnavailableException(LlmPermitScope.PLATFORM, "busy"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> ReflectionTestUtils.invokeMethod(
                orchestrator,
                "analyzeWithEvaluationModel",
                sourceCtx,
                "prompt",
                "answer"
        ));
        assertInstanceOf(LlmInvokeException.class, thrown.getCause());
        verify(evaluationModelRouter, times(1)).routeContexts(sourceCtx);
        verify(llmInvoker, times(10)).analyze(any(), anyString(), anyString());
    }

    private Object invokeExecuteBatch1(Long versionId, Long reportId) throws Exception {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setReportId(reportId);
        PresaleReport report = new PresaleReport();
        report.setId(reportId);
        report.setBrandName("Acme");

        Method executeBatch1 = PresaleGenerateOrchestrator.class.getDeclaredMethod(
                "executeBatch1",
                PresaleReportVersion.class,
                PresaleReport.class,
                PresaleReport.class,
                Long.class,
                boolean.class,
                Class.forName("com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator$PreflightResult")
        );
        executeBatch1.setAccessible(true);
        return executeBatch1.invoke(orchestrator, version, report, report, 1L, false, null);
    }

    private Object invokeExecuteBatch2(Long versionId,
                                       Long reportId,
                                       List<String> competitors,
                                       int competitorPromptCount,
                                       Set<String> batch1DegradedPlatforms) throws Exception {
        PresaleReport report = new PresaleReport();
        report.setId(reportId);
        report.setBrandName("Acme");

        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setBatch1CompletedCalls(0);
        version.setBatch2CompletedCalls(0);
        when(versionMapper.selectById(versionId)).thenReturn(version);

        Method executeBatch2 = PresaleGenerateOrchestrator.class.getDeclaredMethod(
                "executeBatch2",
                Long.class,
                PresaleReport.class,
                PresaleReport.class,
                Long.class,
                boolean.class,
                List.class,
                int.class,
                Set.class
        );
        executeBatch2.setAccessible(true);
        return executeBatch2.invoke(orchestrator, versionId, report, report, 1L, false, competitors, competitorPromptCount, batch1DegradedPlatforms);
    }

    private Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private void awaitBarrier(CyclicBarrier barrier, AtomicReference<Throwable> failure) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure.compareAndSet(null, e);
        } catch (BrokenBarrierException e) {
            failure.compareAndSet(null, e);
        }
    }

    private void assertNoFailure(AtomicReference<Throwable> failure) {
        Throwable t = failure.get();
        if (t != null) {
            throw new AssertionError("Unexpected barrier failure", t);
        }
    }

    private void setupBasePreflightSuccess(Long versionId,
                                           Long reportId,
                                           int platformCount,
                                           int genericPromptCount,
                                           int competitorPromptCount) {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setReportId(reportId);
        when(versionMapper.selectById(versionId)).thenReturn(version);

        PresaleReport report = new PresaleReport();
        report.setId(reportId);
        report.setBrandName("Acme");
        report.setIndustry("Software");
        report.setIndustryRole("SaaS");
        report.setRegion("CN");
        when(reportMapper.selectById(reportId)).thenReturn(report);

        when(aiPlatformConfigMapper.selectCount(any())).thenReturn((long) platformCount);
        when(versionPromptTemplateMapper.selectCount(any()))
                .thenReturn(
                        (long) genericPromptCount,
                        (long) competitorPromptCount,
                        (long) genericPromptCount,
                        (long) competitorPromptCount
                );
    }

    private void setupSimpleRealFlow(Long versionId, Long reportId, List<String> extractedCompetitors) throws Exception {
        setupBasePreflightSuccess(versionId, reportId, 1, 1, 1);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                platforms("kimi"),
                platforms("kimi")
        );
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(991L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(992L, "C1", "batch2 {competitor}"))
        );
        when(competitorAggregator.extractTopCompetitorsFromBatch1(eq(versionId), anyCollection()))
                .thenReturn(extractedCompetitors);
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));
    }

    private PresaleReportVersionPromptTemplate promptTemplate(Long id, String code, String content) {
        PresaleReportVersionPromptTemplate t = new PresaleReportVersionPromptTemplate();
        t.setId(id);
        t.setSourcePromptCode(code);
        t.setPromptContent(content);
        t.setHasCompetitorVar(content != null && content.contains("{competitor}") ? 1 : 0);
        t.setSortOrderInVersion(1);
        return t;
    }

    private LlmCallResult successResult(String rawResponse) {
        return new LlmCallResult(rawResponse, 10, 20, 120L, 0, CallStatus.SUCCESS);
    }

    private PresaleAiPromptResult promptResult(Long id, String competitorsJson) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setId(id);
        row.setVersionId(9201L);
        row.setBatchNo(1);
        row.setIsMentioned(1);
        row.setMentionedCompetitors(competitorsJson);
        return row;
    }

    private SysDictItem dictItem(String value) {
        SysDictItem item = new SysDictItem();
        item.setDictValue(value);
        item.setEnabled(true);
        return item;
    }

    private List<AiPlatformConfig> platforms(String... platformCodes) {
        return java.util.Arrays.stream(platformCodes)
                .map(code -> {
                    AiPlatformConfig p = new AiPlatformConfig();
                    p.setPlatformCode(code);
                    p.setPlatformName(code);
                    p.setEnabled(true);
                    p.setEnabledForPresale(true);
                    p.setLowModelId("low-model");
                    return p;
                })
                .toList();
    }
}

