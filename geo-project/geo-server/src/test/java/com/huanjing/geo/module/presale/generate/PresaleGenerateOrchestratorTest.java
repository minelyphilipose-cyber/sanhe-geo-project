package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleGenerateOrchestratorTest {

    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresalePromptTemplateMapper promptTemplateMapper;
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
    private PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    @Mock
    private PresaleL3InitService l3InitService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PresaleGenerateOrchestrator orchestrator;

    @BeforeEach
    void setupReuseDefaults() {
        lenient().when(reuseDecisionService.preloadByVersionAndBatch(any(), anyInt())).thenReturn(Map.of());
        lenient().when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.RUN_FULL);
        lenient().when(reuseDecisionService.snapshotOf(any(), any())).thenReturn(null);
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
        when(promptTemplateMapper.selectCount(any())).thenReturn(0L);

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
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of());

        orchestrator.triggerGenerate(9004L, 1004L, false);

        ArgumentCaptor<PresaleReportVersion> updateCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(updateCaptor.capture());

        PresaleReportVersion runningUpdate = updateCaptor.getAllValues().stream()
                .filter(v -> PresaleGenerateStatus.RUNNING.name().equals(v.getGenerationStatus()))
                .findFirst()
                .orElseThrow();
        assertEquals("BATCH1", runningUpdate.getGenerationStage());
        assertEquals(450, runningUpdate.getBatch1TotalCalls());
        assertEquals(720, runningUpdate.getTotalLlmCalls());
        assertEquals(0, runningUpdate.getCompletedLlmCalls());
    }

    @Test
    void retry_analyzeSuccessExists_skipsWholeCombo_noLlmInvoke() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9601L, 8601L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(701L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(702L, "C1", "batch2 {competitor}"))
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.SKIP_ALL);

        orchestrator.triggerGenerate(9601L, 601L, false);

        verify(llmInvoker, never()).query(any(), anyString());
        verify(llmInvoker, never()).analyze(any(), anyString(), anyString());
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

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(711L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(712L, "C1", "batch2 {competitor}"))
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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
        verify(reusePersistenceService, times(1))
                .replaceFailedAnalyzeAndResult(any(), any(), any(), any());
    }

    @Test
    void retry_queryFailed_rerunQueryAndAnalyze() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9603L, 8603L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(721L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(722L, "C1", "batch2 {competitor}"))
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.RUN_FULL);
        when(llmInvoker.query(any(), anyString())).thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9603L, 603L, false);

        verify(llmInvoker, times(1)).query(any(), anyString());
        verify(llmInvoker, times(1)).analyze(any(), anyString(), anyString());
    }

    @Test
    void batch1_allSuccess_writesExpectedCallAndPromptResultRows() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9101L, 8101L, 2, 3, 2);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("chatgpt"),
                platform("claude")
        ));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(1L, "P1", "Q1"),
                promptTemplate(2L, "P2", "Q2"),
                promptTemplate(3L, "P3", "Q3")
        ));
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        when(llmInvoker.query(any(), anyString()))
                .thenReturn(successResult("query-ok"));
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9101L, 101L, false);

        verify(aiCallMapper, times(12)).insert(any(PresaleAiCall.class));
        verify(aiPromptResultMapper, times(6)).insert(any());
    }

    @Test
    void batch1_singlePlatformDegraded_writesSkippedRows() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9102L, 8102L, 1, 4, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(11L, "P1", "Q1"),
                promptTemplate(12L, "P2", "Q2"),
                promptTemplate(13L, "P3", "Q3"),
                promptTemplate(14L, "P4", "Q4")
        ));
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
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
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"),
                platform("p2"),
                platform("p3"),
                platform("p4")
        ));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
                promptTemplate(21L, "P1", "Q1"),
                promptTemplate(22L, "P2", "Q2")
        ));
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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
    void competitorExtract_aggregatesTop3AndFiltersBrand() throws Exception {
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(1L, "[\"ChatGPT\", \"Claude\", \"Acme AI\"]"),
                promptResult(2L, "[\"claude\", \"Gemini\"]"),
                promptResult(3L, "[\"  CHAT GPT  \", \"Gemini\", \"Doubao\"]"),
                promptResult(4L, "[\"Doubao\", \"Claude\"]"),
                promptResult(5L, "[\"AcmeAI\", \"Acme AI\"]")
        ));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, String.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9201L, "Acme  AI");

        assertEquals(List.of("Claude", "ChatGPT", "Doubao"), competitors);
    }

    @Test
    void competitorExtract_empty_setsCategoryButNotFailedImmediately() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9202L, 8202L, 1, 1, 1);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of());
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

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
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(11L, "[\"Claude\"]"),
                promptResult(12L, "[\"ChatGPT\"]"),
                promptResult(13L, "[\"Claude\"]")
        ));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, String.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9301L, "Acme");

        assertEquals(List.of("Claude", "ChatGPT"), competitors);
    }

    @Test
    @SuppressWarnings("unchecked")
    void competitorExtract_invalidJsonIsSkipped_withoutBreakingOtherRows() throws Exception {
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(21L, "{broken json"),
                promptResult(22L, "[\"Claude\", \"Gemini\"]"),
                promptResult(23L, "[1,2,3]")
        ));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, String.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9302L, "Acme");

        assertEquals(List.of("Claude", "Gemini"), competitors);
    }

    @Test
    @SuppressWarnings("unchecked")
    void competitorExtract_rowLevelDedup_countsSameNameOncePerRow() throws Exception {
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                promptResult(31L, "[\"Claude\", \"claude\", \"CLAUDE\"]"),
                promptResult(32L, "[\"Claude\"]"),
                promptResult(33L, "[\"Gemini\"]")
        ));

        Method m = PresaleGenerateOrchestrator.class
                .getDeclaredMethod("extractTopCompetitorsFromBatch1", Long.class, String.class);
        m.setAccessible(true);
        List<String> competitors = (List<String>) m.invoke(orchestrator, 9303L, "Acme");

        assertEquals(List.of("Claude", "Gemini"), competitors);
    }

    @Test
    void batch2_writesBatchNoAndCompetitorName() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9401L, 8401L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                List.of(platform("kimi")),
                List.of(platform("kimi"))
        );
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(401L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(402L, "C1", "batch2 {brand} vs {competitor}"))
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(401L, "[\"Claude\"]"))
        );
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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
                        && "Claude".equals(c.getCompetitorName())
        );
        boolean hasBatch2Analyze = callCaptor.getAllValues().stream().anyMatch(c ->
                Integer.valueOf(2).equals(c.getBatchNo())
                        && "ANALYZE".equals(c.getStage())
                        && "Claude".equals(c.getCompetitorName())
        );
        assertTrue(hasBatch2Query);
        assertTrue(hasBatch2Analyze);
    }

    @Test
    void batch2_degradeUsesQcmpTimesC_asTotalPrompts() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9402L, 8402L, 1, 1, 2);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                List.of(platform("kimi")),
                List.of(platform("kimi"))
        );
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(501L, "G1", "batch1 {brand}")),
                List.of(
                        promptTemplate(502L, "C1", "batch2-a {competitor}"),
                        promptTemplate(503L, "C2", "batch2-b {competitor}")
                )
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(501L, "[\"Claude\", \"Gemini\", \"Doubao\"]"))
        );
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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
        assertEquals(6L, batch2Skipped);
    }

    @Test
    void batch1_queryFailureCountsAsTwoCompletedCalls() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9501L, 8501L, 1, 1, 1);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(601L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(602L, "C1", "batch2 {competitor}"))
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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
                List.of(platform("kimi")),
                List.of(platform("kimi"))
        );
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(611L, "G1", "batch1 {brand}")),
                List.of(promptTemplate(612L, "C1", "batch2 {brand} vs {competitor}"))
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(611L, "[\"Claude\"]"))
        );
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
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
    void completedEqualsTotal_inMixedCase_withoutEarlyStop() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);
        setupBasePreflightSuccess(9503L, 8503L, 2, 1, 2);
        PresaleReportVersion preflightVersion = new PresaleReportVersion();
        preflightVersion.setId(9503L);
        preflightVersion.setReportId(8503L);
        PresaleReportVersion batch1Version = new PresaleReportVersion();
        batch1Version.setId(9503L);
        batch1Version.setReportId(8503L);
        PresaleReportVersion batch2EntryVersion = new PresaleReportVersion();
        batch2EntryVersion.setId(9503L);
        batch2EntryVersion.setReportId(8503L);
        batch2EntryVersion.setBatch1CompletedCalls(4);
        when(versionMapper.selectById(9503L))
                .thenReturn(preflightVersion, batch1Version, batch2EntryVersion, batch2EntryVersion);

        when(aiPlatformConfigMapper.selectList(any())).thenReturn(
                List.of(platform("p1"), platform("p2")),
                List.of(platform("p1"), platform("p2"))
        );
        when(promptTemplateMapper.selectList(any())).thenReturn(
                List.of(promptTemplate(621L, "G1", "batch1 {brand}")),
                List.of(
                        promptTemplate(622L, "C1", "batch2-a {competitor}"),
                        promptTemplate(623L, "C2", "batch2-b {competitor}")
                )
        );
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(621L, "[\"Claude\", \"Gemini\", \"Doubao\"]"))
        );
        when(promptTemplateRenderer.render(anyString(), anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        AtomicInteger p2Batch2QueryCounter = new AtomicInteger(0);
        when(llmInvoker.query(any(), anyString())).thenAnswer(invocation -> {
            Object ctxObj = invocation.getArgument(0);
            int batchNo = (int) ctxObj.getClass().getMethod("batchNo").invoke(ctxObj);
            String platformCode = (String) ctxObj.getClass().getMethod("platformCode").invoke(ctxObj);
            if (batchNo == 1) {
                return successResult("batch1-ok");
            }
            if ("p2".equals(platformCode)) {
                int n = p2Batch2QueryCounter.incrementAndGet();
                if (n <= 3) {
                    throw new LlmInvokeException("p2 batch2 query fail " + n);
                }
            }
            return successResult("batch2-ok");
        });
        when(llmInvoker.analyze(any(), anyString(), anyString()))
                .thenReturn(successResult("{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[]}"));

        orchestrator.triggerGenerate(9503L, 503L, false);

        ArgumentCaptor<PresaleReportVersion> versionCaptor = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper, atLeastOnce()).updateById(versionCaptor.capture());
        int maxTotal = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getTotalLlmCalls)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);
        int maxCompleted = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getCompletedLlmCalls)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);
        long batch2DegradedPlatformCount = versionCaptor.getAllValues().stream()
                .map(PresaleReportVersion::getDegradedPlatforms)
                .filter(v -> v != null && !v.isBlank() && !"[]".equals(v))
                .count();
        assertEquals(maxTotal, maxCompleted);
        assertTrue(batch2DegradedPlatformCount >= 1);
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
        when(promptTemplateMapper.selectCount(any()))
                .thenReturn((long) genericPromptCount, (long) competitorPromptCount);
    }

    private AiPlatformConfig platform(String code) {
        AiPlatformConfig p = new AiPlatformConfig();
        p.setPlatformCode(code);
        p.setEnabled(true);
        return p;
    }

    private PresalePromptTemplate promptTemplate(Long id, String code, String content) {
        PresalePromptTemplate t = new PresalePromptTemplate();
        t.setId(id);
        t.setPromptCode(code);
        t.setPromptContent(content);
        t.setEnabled(1);
        t.setHasCompetitorVar(0);
        t.setSortOrder(1);
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
}
