package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.generate.l3.PresaleL3InitService;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import com.huanjing.geo.module.presale.generate.llm.PromptTemplateRenderer;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
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
    private PresalePromptTemplateMapper promptTemplateMapper;
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

    @Test
    void realFlow_benchmarkMissing_marksConfigMissing() {
        ReflectionTestUtils.setField(orchestrator, "mockEnabled", false);

        PresaleReport report = insertReport();
        PresaleReportVersion version = insertVersion(report.getId());

        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(1L);
        when(promptTemplateMapper.selectCount(any())).thenReturn(1L, 1L);
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platform("kimi")));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of());
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
}


