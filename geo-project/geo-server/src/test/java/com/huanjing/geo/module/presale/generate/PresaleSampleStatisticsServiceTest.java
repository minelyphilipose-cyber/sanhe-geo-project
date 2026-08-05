package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.presale.generate.web.PresaleQueryWebMode;
import com.huanjing.geo.module.presale.generate.web.PresaleSearchEvidence;
import com.huanjing.geo.module.presale.generate.web.PresaleWebExecutionContext;
import com.huanjing.geo.module.presale.generate.web.ResolvedCompanionExecutionConfig;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleSampleStatisticsServiceTest {
    @Mock private PresaleAiCallMapper callMapper;
    @Mock private PresaleAiPromptResultMapper resultMapper;
    @Mock private PresaleReportVersionMapper versionMapper;
    @Mock private PresaleReportVersionPromptTemplateMapper templateMapper;

    private PresaleSampleStatisticsService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                PresaleAiPromptResult.class);
        objectMapper = new ObjectMapper();
        ReuseDecisionService reuse = new ReuseDecisionService(callMapper, objectMapper);
        service = new PresaleSampleStatisticsService(callMapper, resultMapper, versionMapper,
                templateMapper, reuse, objectMapper);
    }

    @Test
    void plannedSamplesAreMutuallyClassifiedAndConserved() {
        Long versionId = 88L;
        when(templateMapper.selectList(any())).thenReturn(List.of(template(1L), template(2L), template(3L), template(4L)));
        List<PresaleAiCall> calls = new ArrayList<>();
        calls.add(webQuery(versionId, 1L, "SUCCESS", null));
        calls.add(call(versionId, 1L, "ANALYZE", "SUCCESS"));
        calls.add(webQuery(versionId, 2L, "FAILED", "EMPTY_ANSWER"));
        calls.add(call(versionId, 3L, "QUERY", "SKIPPED_DEGRADED"));
        calls.add(webQuery(versionId, 4L, "SUCCESS", null));
        calls.add(call(versionId, 4L, "ANALYZE", "FAILED"));
        when(callMapper.selectList(any())).thenReturn(calls);
        PresaleAiPromptResult effectiveRow = new PresaleAiPromptResult();
        effectiveRow.setId(1001L);
        effectiveRow.setVersionId(versionId);
        effectiveRow.setBatchNo(1);
        effectiveRow.setPlatformCode("qwen");
        effectiveRow.setPromptTemplateId(1L);
        effectiveRow.setCompetitorName("");
        when(resultMapper.selectList(any())).thenReturn(List.of(effectiveRow));

        PresaleSampleStatisticsService.StatisticsResult result = service.classifyAndPersist(
                versionId, context(), Set.of(), null);

        assertEquals(4, result.planned());
        assertEquals(2, result.webValid());
        assertEquals(1, result.effective());
        assertEquals(1, result.queryFailed());
        assertEquals(1, result.analyzeFailed());
        assertEquals(1, result.skipped());
        assertEquals(result.planned(), result.queryFailed() + result.analyzeFailed()
                + result.skipped() + result.degradedExcluded() + result.effective());

        ArgumentCaptor<PresaleReportVersion> update = ArgumentCaptor.forClass(PresaleReportVersion.class);
        verify(versionMapper).updateById(update.capture());
        assertEquals(4, update.getValue().getPlannedQueryCount());
        assertEquals("EMPTY_ANSWER", update.getValue().getMainWebFailureCode());
    }

    @Test
    void analyzeFromOlderQueryDoesNotMakeCurrentWebQueryEffective() {
        Long versionId = 89L;
        when(templateMapper.selectList(any())).thenReturn(List.of(template(1L)));
        PresaleAiCall currentQuery = webQuery(versionId, 1L, "SUCCESS", null);
        PresaleAiCall oldAnalyze = call(versionId, 1L, "ANALYZE", "SUCCESS");
        oldAnalyze.setParentCallId(999L);
        when(callMapper.selectList(any())).thenReturn(List.of(oldAnalyze, currentQuery));
        when(resultMapper.selectList(any())).thenReturn(List.of());

        PresaleSampleStatisticsService.StatisticsResult result = service.classifyAndPersist(
                versionId, context(), Set.of(), null);

        assertEquals(1, result.webValid());
        assertEquals(0, result.effective());
        assertEquals(1, result.analyzeFailed());
    }

    @Test
    void mixedRunCountsNativeAsEffectiveWithoutInflatingWebCoverage() {
        Long versionId = 90L;
        when(templateMapper.selectList(any())).thenReturn(List.of(template(1L)));
        PresaleAiCall web = webQuery(versionId, 1L, "SUCCESS", null);
        PresaleAiCall webAnalyze = call(versionId, 1L, "ANALYZE", "SUCCESS");
        PresaleAiCall nativeQuery = call(versionId, 1L, "QUERY", "SUCCESS");
        nativeQuery.setId(901L);
        nativeQuery.setPlatformCode("kimi");
        PresaleAiCall nativeAnalyze = call(versionId, 1L, "ANALYZE", "SUCCESS");
        nativeAnalyze.setId(902L);
        nativeAnalyze.setPlatformCode("kimi");
        nativeAnalyze.setParentCallId(901L);
        when(callMapper.selectList(any())).thenReturn(List.of(webAnalyze, web, nativeAnalyze, nativeQuery));
        when(resultMapper.selectList(any())).thenReturn(List.of());

        PresaleSampleStatisticsService.StatisticsResult result = service.classifyAndPersist(
                versionId, mixedContext(), Set.of(), null);

        assertEquals(2, result.planned());
        assertEquals(1, result.plannedWeb());
        assertEquals(1, result.webValid());
        assertEquals(2, result.effective());
    }

    @Test
    void successfulAnswerWithoutUsableSearchEvidenceRemainsEffectiveButNotWebValid() {
        Long versionId = 91L;
        when(templateMapper.selectList(any())).thenReturn(List.of(template(1L)));
        PresaleAiCall query = webQuery(versionId, 1L, "SUCCESS", null);
        query.setSearchEvidenceJson("""
                {"searchTriggered":false,"searchStatus":"SUCCEEDED","evidenceLevel":"NONE",
                 "webConfigId":9,"webConfigVersion":3,"integrationType":"DASHSCOPE_NATIVE_WEB",
                 "modelId":"qwen-plus","failureCode":null}
                """);
        PresaleAiCall analyze = call(versionId, 1L, "ANALYZE", "SUCCESS");
        when(callMapper.selectList(any())).thenReturn(List.of(analyze, query));
        when(resultMapper.selectList(any())).thenReturn(List.of());

        PresaleSampleStatisticsService.StatisticsResult result = service.classifyAndPersist(
                versionId, context(), Set.of(), null);

        assertEquals(1, result.plannedWeb());
        assertEquals(0, result.webValid());
        assertEquals(1, result.effective());
        assertEquals(0, result.queryFailed());
    }

    private PresaleWebExecutionContext context() {
        ResolvedCompanionExecutionConfig config = new ResolvedCompanionExecutionConfig(
                "qwen", "千问", 9L, "qwen_web", "千问联网", 3L, "qwen", "aliyun",
                IntegrationType.DASHSCOPE_NATIVE_WEB, "https://dashscope.aliyuncs.com/x",
                "qwen-plus", "千问联网", "env://KEY", "{}", 1000, 1000, 1, 60, 60000);
        return new PresaleWebExecutionContext(PresaleQueryWebMode.REQUIRED, Map.of("qwen", config));
    }

    private PresaleWebExecutionContext mixedContext() {
        ResolvedCompanionExecutionConfig config = new ResolvedCompanionExecutionConfig(
                "qwen", "千问", 9L, "qwen_web", "千问联网", 3L, "qwen", "aliyun",
                IntegrationType.DASHSCOPE_NATIVE_WEB, "https://dashscope.aliyuncs.com/x",
                "qwen-plus", "千问联网", "env://KEY", "{}", 1000, 1000, 1, 60, 60000);
        com.huanjing.geo.module.system.entity.AiPlatformConfig qwen = new com.huanjing.geo.module.system.entity.AiPlatformConfig();
        qwen.setPlatformCode("qwen");
        qwen.setPlatformName("千问");
        qwen.setChannelCode("qwen");
        com.huanjing.geo.module.system.entity.AiPlatformConfig kimi = new com.huanjing.geo.module.system.entity.AiPlatformConfig();
        kimi.setPlatformCode("kimi");
        kimi.setPlatformName("Kimi");
        kimi.setChannelCode("kimi");
        return new PresaleWebExecutionContext(PresaleQueryWebMode.REQUIRED,
                Map.of("qwen", config), List.of(qwen, kimi));
    }

    private PresaleReportVersionPromptTemplate template(Long id) {
        PresaleReportVersionPromptTemplate row = new PresaleReportVersionPromptTemplate();
        row.setId(id);
        row.setHasCompetitorVar(0);
        return row;
    }

    private PresaleAiCall webQuery(Long versionId, Long templateId, String status, String failureCode) {
        PresaleAiCall row = call(versionId, templateId, "QUERY", status);
        row.setQueryContractVersion(PresaleSearchEvidence.CONTRACT_VERSION);
        String searchStatus = "SUCCESS".equals(status) ? "SUCCEEDED" : "FAILED";
        row.setSearchEvidenceJson("{\"searchTriggered\":" + ("SUCCESS".equals(status))
                + ",\"searchStatus\":\"" + searchStatus + "\",\"evidenceLevel\":\"SOURCES\","
                + "\"webConfigId\":9,\"webConfigVersion\":3,\"integrationType\":\"DASHSCOPE_NATIVE_WEB\","
                + "\"modelId\":\"qwen-plus\",\"failureCode\":"
                + (failureCode == null ? "null" : "\"" + failureCode + "\"") + "}");
        return row;
    }

    private PresaleAiCall call(Long versionId, Long templateId, String stage, String status) {
        PresaleAiCall row = new PresaleAiCall();
        row.setId(templateId * 10 + ("QUERY".equals(stage) ? 1 : 2));
        row.setVersionId(versionId);
        row.setBatchNo(1);
        row.setPlatformCode("qwen");
        row.setPromptTemplateId(templateId);
        row.setCompetitorName("");
        row.setStage(stage);
        row.setCallStatus(status);
        if ("ANALYZE".equals(stage)) {
            row.setParentCallId(templateId * 10 + 1);
        }
        return row;
    }
}
