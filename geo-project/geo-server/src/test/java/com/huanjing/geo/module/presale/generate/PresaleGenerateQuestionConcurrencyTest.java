package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.service.PresaleBenchmarkIndustryClassifier;
import com.huanjing.geo.module.partner.service.PartnerPresaleReportQuotaService;
import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.generate.l3.PresalePage03DoubaoService;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.web.PresaleWebQueryInvoker;
import com.huanjing.geo.module.presale.generate.web.PresaleWebReadinessChecker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
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
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresaleGenerateQuestionConcurrencyTest {

    private static final String ANALYZE_JSON = """
            {
              "is_mentioned": true,
              "ranking": 1,
              "sentiment": "POSITIVE",
              "mentioned_competitors": [],
              "scene_advantages": [],
              "top_keywords": [],
              "negative_evidence": {}
            }
            """;

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
    private PresaleJudgeService presaleJudgeService;
    @Mock
    private PresaleEvaluationModelRouter evaluationModelRouter;
    @Mock
    private PresaleGenerateCancellationRegistry cancellationRegistry;
    @Mock
    private PartnerPresaleReportQuotaService partnerPresaleReportQuotaService;
    @Mock
    private PresaleSampleStatisticsService sampleStatisticsService;
    @Mock
    private PresaleWebReadinessChecker webReadinessChecker;
    @Mock
    private PresaleWebQueryInvoker webQueryInvoker;
    @Mock
    private PresaleBenchmarkIndustryClassifier benchmarkIndustryClassifier;

    private PresaleGenerateOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        orchestrator = new PresaleGenerateOrchestrator(
                versionMapper,
                reportMapper,
                aiPlatformConfigMapper,
                sysDictItemMapper,
                versionPromptTemplateMapper,
                aiCallMapper,
                aiPromptResultMapper,
                reuseDecisionService,
                reusePersistenceService,
                llmInvoker,
                promptTemplateRenderer,
                rawSnapshotAssembler,
                computedSnapshotEnricher,
                l3InitService,
                page03DoubaoService,
                competitorAggregator,
                competitorNormalizationService,
                presaleJudgeService,
                evaluationModelRouter,
                cancellationRegistry,
                partnerPresaleReportQuotaService,
                sampleStatisticsService,
                webReadinessChecker,
                webQueryInvoker,
                benchmarkIndustryClassifier,
                new ObjectMapper(),
                directExecutor
        );

        lenient().when(cancellationRegistry.isCanceled(any())).thenReturn(false);
        lenient().when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.RUN_FULL);
        lenient().when(promptTemplateRenderer.variables(any(), any())).thenCallRealMethod();
        lenient().when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        lenient().when(evaluationModelRouter.routeContexts(any()))
                .thenAnswer(invocation -> List.of(invocation.getArgument(0, PlatformCallContext.class)));

        AtomicLong callIds = new AtomicLong(0);
        lenient().doAnswer(invocation -> {
            PresaleAiCall row = invocation.getArgument(0, PresaleAiCall.class);
            row.setId(callIds.incrementAndGet());
            return 1;
        }).when(aiCallMapper).insert(any(PresaleAiCall.class));
    }

    @Test
    void executeBatch1RunsPromptsConcurrentlyUpToPlatformLimit() throws Exception {
        Long versionId = 9001L;
        when(versionMapper.selectById(versionId)).thenReturn(runningVersion(versionId));

        AtomicInteger activeQueries = new AtomicInteger(0);
        AtomicInteger maxActiveQueries = new AtomicInteger(0);
        AtomicInteger enteredQueries = new AtomicInteger(0);
        when(llmInvoker.query(any(), anyString())).thenAnswer(invocation -> {
            int active = activeQueries.incrementAndGet();
            maxActiveQueries.accumulateAndGet(active, Math::max);
            enteredQueries.incrementAndGet();
            try {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400);
                while (enteredQueries.get() < 3 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                Thread.sleep(50);
                return success("answer");
            } finally {
                activeQueries.decrementAndGet();
            }
        });
        when(llmInvoker.analyze(any(), anyString(), anyString())).thenReturn(success(ANALYZE_JSON));

        AtomicInteger completedCalls = new AtomicInteger(0);
        ReflectionTestUtils.invokeMethod(
                orchestrator,
                "executePlatformBatch1",
                platform("deepseek", 3),
                versionId,
                templates(6),
                Map.of(),
                ConcurrentHashMap.newKeySet(),
                new AtomicInteger(0),
                report(),
                report(),
                1L,
                true,
                completedCalls,
                new AtomicInteger(0),
                new AtomicInteger(0),
                new Semaphore(24)
        );

        assertEquals(12, completedCalls.get());
        assertTrue(maxActiveQueries.get() >= 3,
                "same platform should process prompts concurrently up to concurrency_limit");
    }

    @Test
    void executeBatch1RespectsReportPromptConcurrencyLimit() throws Exception {
        Long versionId = 9003L;
        when(versionMapper.selectById(versionId)).thenReturn(runningVersion(versionId));

        AtomicInteger activeQueries = new AtomicInteger(0);
        AtomicInteger maxActiveQueries = new AtomicInteger(0);
        when(llmInvoker.query(any(), anyString())).thenAnswer(invocation -> {
            int active = activeQueries.incrementAndGet();
            maxActiveQueries.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(80);
                return success("answer");
            } finally {
                activeQueries.decrementAndGet();
            }
        });
        when(llmInvoker.analyze(any(), anyString(), anyString())).thenReturn(success(ANALYZE_JSON));

        AtomicInteger completedCalls = new AtomicInteger(0);
        ReflectionTestUtils.invokeMethod(
                orchestrator,
                "executePlatformBatch1",
                platform("deepseek", 5),
                versionId,
                templates(6),
                Map.of(),
                ConcurrentHashMap.newKeySet(),
                new AtomicInteger(0),
                report(),
                report(),
                1L,
                true,
                completedCalls,
                new AtomicInteger(0),
                new AtomicInteger(0),
                new Semaphore(2)
        );

        assertEquals(12, completedCalls.get());
        assertTrue(maxActiveQueries.get() <= 2,
                "report-level prompt concurrency should cap active model requests");
        assertTrue(maxActiveQueries.get() >= 2,
                "test should observe concurrent execution before asserting the cap");
    }

    @Test
    void executeBatch1StopsBeforeCallingModelWhenVersionIsNoLongerRunning() throws Exception {
        Long versionId = 9002L;
        when(versionMapper.selectById(versionId)).thenReturn(failedVersion(versionId));

        assertThrows(BatchInterruptedException.class, () -> ReflectionTestUtils.invokeMethod(
                orchestrator,
                "executePlatformBatch1",
                platform("deepseek", 3),
                versionId,
                templates(3),
                Map.of(),
                Set.of(),
                new AtomicInteger(0),
                report(),
                report(),
                1L,
                true,
                new AtomicInteger(0),
                new AtomicInteger(0),
                new AtomicInteger(0),
                new Semaphore(24)
        ));
        verify(llmInvoker, never()).query(any(), anyString());
    }

    @Test
    void executeBatch1StopsBeforeCallingModelWhenCancellationRequested() throws Exception {
        Long versionId = 9004L;
        when(versionMapper.selectById(versionId)).thenReturn(runningVersion(versionId));
        when(cancellationRegistry.isCanceled(versionId)).thenReturn(true);

        assertThrows(BatchInterruptedException.class, () -> ReflectionTestUtils.invokeMethod(
                orchestrator,
                "executePlatformBatch1",
                platform("deepseek", 3),
                versionId,
                templates(3),
                Map.of(),
                Set.of(),
                new AtomicInteger(0),
                report(),
                report(),
                1L,
                true,
                new AtomicInteger(0),
                new AtomicInteger(0),
                new AtomicInteger(0),
                new Semaphore(24)
        ));
        verify(llmInvoker, never()).query(any(), anyString());
    }

    private static AiPlatformConfig platform(String platformCode, int concurrencyLimit) {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode(platformCode);
        platform.setPlatformName(platformCode);
        platform.setConcurrencyLimit(concurrencyLimit);
        return platform;
    }

    private static PresaleReportVersion runningVersion(Long versionId) {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setGenerationStatus(PresaleGenerateStatus.RUNNING.name());
        return version;
    }

    private static PresaleReportVersion failedVersion(Long versionId) {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(versionId);
        version.setGenerationStatus(PresaleGenerateStatus.FAILED.name());
        return version;
    }

    private static PresaleReport report() {
        PresaleReport report = new PresaleReport();
        report.setId(1L);
        report.setBrandName("诗帝尼");
        return report;
    }

    private static List<PresaleReportVersionPromptTemplate> templates(int count) {
        List<PresaleReportVersionPromptTemplate> templates = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            PresaleReportVersionPromptTemplate template = new PresaleReportVersionPromptTemplate();
            template.setId(i);
            template.setPromptContent("prompt " + i);
            templates.add(template);
        }
        return templates;
    }

    private static LlmCallResult success(String rawResponse) {
        return new LlmCallResult(
                rawResponse,
                10,
                10,
                50L,
                0,
                CallStatus.SUCCESS,
                "deepseek",
                "Deepseek",
                "test-model",
                "Test Model"
        );
    }
}
