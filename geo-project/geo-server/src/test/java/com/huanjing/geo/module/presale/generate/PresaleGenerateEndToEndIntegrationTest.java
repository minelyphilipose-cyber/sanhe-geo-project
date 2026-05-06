package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.content.credential.crypto.LocalMasterKeyProvider;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.flyway.validate-on-migrate=false")
@Transactional
class PresaleGenerateEndToEndIntegrationTest {

    @Autowired
    private PresaleGenerateOrchestrator orchestrator;
    @Autowired
    private PresaleReportMapper reportMapper;
    @Autowired
    private PresaleReportVersionMapper versionMapper;

    @MockBean
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @MockBean
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @MockBean
    private PresaleAiCallMapper aiCallMapper;
    @MockBean
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @MockBean
    private ReuseDecisionService reuseDecisionService;
    @MockBean
    private PresaleReusePersistenceService reusePersistenceService;
    @MockBean
    private PresaleLlmInvoker llmInvoker;
    @MockBean
    private PromptTemplateRenderer promptTemplateRenderer;
    @MockBean
    private PresaleRawSnapshotAssembler rawSnapshotAssembler;
    @MockBean
    private PresaleComputedSnapshotEnricher computedSnapshotEnricher;
    @MockBean
    private PresaleL3InitService l3InitService;
    @MockBean
    private PresaleCompetitorAggregator competitorAggregator;
    @MockBean
    private LocalMasterKeyProvider localMasterKeyProvider;

    @Test
    void realFlow_benchmarkMissing_marksConfigMissing() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReport report = insertReport();
        PresaleReportVersion version = insertVersion(report.getId());

        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(1L);
        when(versionPromptTemplateMapper.selectCount(any())).thenReturn(1L, 1L);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(promptTemplate(101L, "B1_TEMPLATE", "batch1 prompt", 0)));
        when(competitorAggregator.extractTopCompetitorsFromBatch1(anyLong(), anyString())).thenReturn(List.of());
        when(reuseDecisionService.preloadByVersionAndBatch(anyLong(), anyInt())).thenReturn(Map.of());
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("BENCHMARK_MISSING fallback (_ALL_,_ALL_) not found"));

        Object target = AopTestUtils.getTargetObject(orchestrator);
        ReflectionTestUtils.invokeMethod(target, "doTriggerGenerate", version.getId(), 1L, false);

        PresaleReportVersion latest = versionMapper.selectById(version.getId());
        assertThat(latest).isNotNull();
        assertThat(latest.getGenerationStatus()).isEqualTo(PresaleGenerateStatus.FAILED.name());
        assertThat(latest.getFailureCategory()).isEqualTo("CONFIG_MISSING");
        assertThat(latest.getFailureReason()).contains("L1 aggregate failed");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void realFlow_multiPlatform_parallelExecution_snapshotComplete() throws Exception {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReport report = insertReport();
        PresaleReportVersion version = insertVersion(report.getId());

        List<AiPlatformConfig> platforms = List.of(
                platform("kimi"),
                platform("qwen"),
                platform("doubao"),
                platform("deepseek"),
                platform("glm")
        );
        Set<String> seenPlatforms = ConcurrentHashMap.newKeySet();

        PresaleReportVersionPromptTemplate batch1Template = promptTemplate(101L, "B1_TEMPLATE", "batch1 prompt", 0);
        PresaleReportVersionPromptTemplate batch2Template = promptTemplate(201L, "B2_TEMPLATE", "batch2 prompt {competitor}", 1);

        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(5L);
        when(versionPromptTemplateMapper.selectCount(any())).thenReturn(1L, 1L);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms);
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(
                List.of(batch1Template),
                List.of(batch2Template)
        );
        when(reuseDecisionService.preloadByVersionAndBatch(anyLong(), anyInt())).thenReturn(Map.of());
        when(reuseDecisionService.decide(any(), any())).thenReturn(ReuseDecision.RUN_FULL);
        when(promptTemplateRenderer.variables(any(PlatformCallContext.class), any(PresaleReport.class)))
                .thenCallRealMethod();
        when(promptTemplateRenderer.render(anyString(), any(PromptTemplateRenderer.RenderVariables.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class));
        when(llmInvoker.query(any(PlatformCallContext.class), anyString()))
                .thenAnswer(invocation -> {
                    PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
                    seenPlatforms.add(ctx.platformCode());
                    return new LlmCallResult(
                            "query-ok-" + ctx.platformCode(),
                            100,
                            30,
                            20L,
                            0,
                            CallStatus.SUCCESS
                    );
                });
        when(llmInvoker.analyze(any(PlatformCallContext.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    PlatformCallContext ctx = invocation.getArgument(0, PlatformCallContext.class);
                    return new LlmCallResult(
                            "{\"is_mentioned\":true,\"ranking\":1,\"sentiment\":\"POSITIVE\",\"mentioned_competitors\":[],\"scene_advantages\":[],\"top_keywords\":[],\"negative_evidence\":{}}",
                            120,
                            40,
                            25L,
                            0,
                            CallStatus.SUCCESS
                    );
                });
        when(competitorAggregator.extractTopCompetitorsFromBatch1(anyLong(), anyString()))
                .thenReturn(List.of("c1", "c2"));
        when(rawSnapshotAssembler.assemble(anyLong(), any(), any(), any(), any()))
                .thenReturn("{\"platform_breakdown\":[{\"platform_code\":\"kimi\"},{\"platform_code\":\"qwen\"},{\"platform_code\":\"doubao\"},{\"platform_code\":\"deepseek\"},{\"platform_code\":\"glm\"}]}");
        when(computedSnapshotEnricher.enrichAndValidate(anyLong(), anyString(), any(), any(Boolean.class)))
                .thenReturn("{\"summary\":\"ok\"}");
        when(l3InitService.derive(anyString(), anyString())).thenReturn("{\"l3\":\"ok\"}");

        Object target = AopTestUtils.getTargetObject(orchestrator);
        ReflectionTestUtils.invokeMethod(target, "doTriggerGenerate", version.getId(), 1L, false);

        PresaleReportVersion latest = versionMapper.selectById(version.getId());
        assertThat(latest).isNotNull();
        assertThat(latest.getGenerationStatus()).isEqualTo(PresaleGenerateStatus.DONE.name());
        assertThat(latest.getRawSnapshotJson()).contains("platform_breakdown");
        assertThat(seenPlatforms).containsExactlyInAnyOrder("kimi", "qwen", "doubao", "deepseek", "glm");

        verify(llmInvoker, atLeastOnce()).query(any(PlatformCallContext.class), anyString());
        verify(llmInvoker, atLeastOnce()).analyze(any(PlatformCallContext.class), anyString(), anyString());
    }

    private PresaleReport insertReport() {
        LocalDateTime now = LocalDateTime.now();
        PresaleReport report = new PresaleReport();
        report.setBrandName("C5-Brand");
        report.setIndustry("c5_industry");
        report.setIndustryRole("c5_role");
        report.setRegion("CN");
        report.setUserDemand("C5 integration benchmark missing");
        report.setLatestVersionId(null);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        report.setCreatedBy(1L);
        reportMapper.insert(report);
        return report;
    }

    private PresaleReportVersion insertVersion(Long reportId) {
        LocalDateTime now = LocalDateTime.now();
        PresaleReportVersion version = new PresaleReportVersion();
        version.setReportId(reportId);
        version.setVersionNo(1);
        version.setGenerationStatus(PresaleGenerateStatus.QUEUED.name());
        version.setGenerationStage(null);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        version.setCreatedBy(1L);
        versionMapper.insert(version);
        return version;
    }

    private AiPlatformConfig platform(String code) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode(code);
        config.setEnabled(true);
        config.setEnabledForPresale(true);
        config.setLowModelId("low-model");
        return config;
    }

    private PresaleReportVersionPromptTemplate promptTemplate(Long id, String code, String content, Integer hasCompetitorVar) {
        PresaleReportVersionPromptTemplate template = new PresaleReportVersionPromptTemplate();
        template.setId(id);
        template.setReportId(1L);
        template.setReportVersionId(1L);
        template.setSourceTemplateId(id);
        template.setSourcePromptCode(code);
        template.setSourceTemplateVersion("v3");
        template.setCategory(Integer.valueOf(1).equals(hasCompetitorVar) ? "对比型" : "推荐型");
        template.setBusinessValue("中");
        template.setPromptContent(content);
        template.setHasCompetitorVar(hasCompetitorVar);
        template.setSortOrderInVersion(id.intValue());
        template.setIsUserAdded(0);
        return template;
    }
}


