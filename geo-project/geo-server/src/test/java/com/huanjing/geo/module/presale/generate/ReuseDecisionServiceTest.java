package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.web.CompanionIdentity;
import com.huanjing.geo.module.presale.generate.web.PresaleQueryWebMode;
import com.huanjing.geo.module.presale.generate.web.PresaleSearchEvidence;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReuseDecisionServiceTest {

    @Mock
    private PresaleAiCallMapper aiCallMapper;

    private ReuseDecisionService service;

    @BeforeEach
    void setUp() {
        service = new ReuseDecisionService(aiCallMapper, new ObjectMapper());
    }

    @Test
    void analyzeSuccessPresent_returnsSkipAll_evenIfOtherRowsExist() {
        PresaleAiCall analyzeSuccess = call(100L, 1, "kimi", 10L, "", "ANALYZE", "SUCCESS", null);
        PresaleAiCall analyzeFailed = call(100L, 1, "kimi", 10L, "", "ANALYZE", "FAILED", null);
        PresaleAiCall querySuccess = call(100L, 1, "kimi", 10L, "", "QUERY", "SUCCESS", "answer");
        querySuccess.setId(1001L);
        analyzeSuccess.setParentCallId(1001L);
        when(aiCallMapper.selectList(any())).thenReturn(List.of(analyzeSuccess, analyzeFailed, querySuccess));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(100L, 1);
        PlatformCallContext ctx = new PlatformCallContext(100L, 1, "kimi", 10L, "", "Acme", 1L, false);

        assertEquals(ReuseDecision.SKIP_ALL, service.decide(ctx, cache));
    }

    @Test
    void competitorName_nullAndEmpty_normalizedToSameReuseKey() {
        PresaleAiCall querySuccessNull = call(101L, 2, "kimi", 11L, null, "QUERY", "SUCCESS", "answer");
        when(aiCallMapper.selectList(any())).thenReturn(List.of(querySuccessNull));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(101L, 2);
        PlatformCallContext ctxEmpty = new PlatformCallContext(101L, 2, "kimi", 11L, "", "Acme", 1L, false);

        assertEquals(ReuseDecision.REUSE_QUERY_ONLY, service.decide(ctxEmpty, cache));
    }

    @Test
    void queryFailedOnly_runsFull() {
        PresaleAiCall queryFailed = call(102L, 1, "kimi", 12L, "", "QUERY", "FAILED", null);
        when(aiCallMapper.selectList(any())).thenReturn(List.of(queryFailed));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(102L, 1);
        PlatformCallContext ctx = new PlatformCallContext(102L, 1, "kimi", 12L, "", "Acme", 1L, false);

        assertEquals(ReuseDecision.RUN_FULL, service.decide(ctx, cache));
    }

    @Test
    void competitorName_caseSensitive_notNormalizedToSameReuseKey() {
        PresaleAiCall querySuccessCapital = call(103L, 2, "kimi", 13L, "Claude", "QUERY", "SUCCESS", "answer");
        when(aiCallMapper.selectList(any())).thenReturn(List.of(querySuccessCapital));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(103L, 2);
        PlatformCallContext ctxLowercase = new PlatformCallContext(103L, 2, "kimi", 13L, "claude", "Acme", 1L, false);

        assertEquals(ReuseDecision.RUN_FULL, service.decide(ctxLowercase, cache));
    }

    @Test
    void competitorGroup_orderAndWhitespaceVariants_normalizedToSameReuseKey() {
        PresaleAiCall querySuccessGroup = call(104L, 2, "kimi", 14L, "A、B、C", "QUERY", "SUCCESS", "answer");
        when(aiCallMapper.selectList(any())).thenReturn(List.of(querySuccessGroup));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(104L, 2);

        assertEquals(ReuseDecision.REUSE_QUERY_ONLY, service.decide(
                new PlatformCallContext(104L, 2, "kimi", 14L, "B、A、C", "Acme", 1L, false), cache));
        assertEquals(ReuseDecision.REUSE_QUERY_ONLY, service.decide(
                new PlatformCallContext(104L, 2, "kimi", 14L, "A 、B、C", "Acme", 1L, false), cache));
        assertEquals(ReuseDecision.REUSE_QUERY_ONLY, service.decide(
                new PlatformCallContext(104L, 2, "kimi", 14L, "A、 B 、C", "Acme", 1L, false), cache));
    }

    @Test
    void requiredMode_reusesOnlyMatchingSuccessfulWebContract() {
        PresaleAiCall analyzeSuccess = call(105L, 1, "qwen", 15L, "", "ANALYZE", "SUCCESS", "analysis");
        PresaleAiCall querySuccess = call(105L, 1, "qwen", 15L, "", "QUERY", "SUCCESS", "answer");
        querySuccess.setId(1051L);
        analyzeSuccess.setParentCallId(1051L);
        querySuccess.setQueryContractVersion(PresaleSearchEvidence.CONTRACT_VERSION);
        querySuccess.setSearchEvidenceJson("""
                {"searchTriggered":true,"searchStatus":"SUCCEEDED","evidenceLevel":"SOURCES",
                 "webConfigId":9,"webConfigVersion":3,"integrationType":"DASHSCOPE_NATIVE_WEB",
                 "modelId":"qwen-plus"}
                """);
        when(aiCallMapper.selectList(any())).thenReturn(List.of(analyzeSuccess, querySuccess));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(105L, 1);
        PlatformCallContext ctx = new PlatformCallContext(105L, 1, "qwen", 15L, "", "Acme", 1L, false);
        CompanionIdentity identity = new CompanionIdentity(9L, 3L,
                IntegrationType.DASHSCOPE_NATIVE_WEB, "qwen-plus");

        assertEquals(ReuseDecision.SKIP_ALL,
                service.decide(ctx, cache, PresaleQueryWebMode.REQUIRED, identity));
        assertEquals(ReuseDecision.RUN_FULL,
                service.decide(ctx, cache, PresaleQueryWebMode.REQUIRED,
                        new CompanionIdentity(9L, 4L, IntegrationType.DASHSCOPE_NATIVE_WEB, "qwen-plus")));
    }

    @Test
    void requiredModeReusesSuccessfulAutoAnswerWithoutClaimingWebEvidence() {
        PresaleAiCall querySuccess = call(108L, 1, "qwen", 18L, "", "QUERY", "SUCCESS", "answer");
        querySuccess.setId(1081L);
        querySuccess.setQueryContractVersion(PresaleSearchEvidence.CONTRACT_VERSION);
        querySuccess.setSearchEvidenceJson("""
                {"searchTriggered":false,"searchStatus":"SUCCEEDED","evidenceLevel":"NONE",
                 "webConfigId":9,"webConfigVersion":3,"integrationType":"DASHSCOPE_NATIVE_WEB",
                 "modelId":"qwen-plus"}
                """);
        when(aiCallMapper.selectList(any())).thenReturn(List.of(querySuccess));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache =
                service.preloadByVersionAndBatch(108L, 1);
        PlatformCallContext ctx = new PlatformCallContext(
                108L, 1, "qwen", 18L, "", "Acme", 1L, false);
        CompanionIdentity identity = new CompanionIdentity(
                9L, 3L, IntegrationType.DASHSCOPE_NATIVE_WEB, "qwen-plus");

        assertEquals(ReuseDecision.REUSE_QUERY_ONLY,
                service.decide(ctx, cache, PresaleQueryWebMode.REQUIRED, identity));
        assertFalse(service.hasValidWebSearchEvidence(querySuccess, identity));
    }

    @Test
    void requiredMode_doesNotPairNewQueryWithAnalyzeFromOlderQuery() {
        PresaleAiCall oldAnalyze = call(106L, 1, "qwen", 16L, "", "ANALYZE", "SUCCESS", "analysis");
        oldAnalyze.setId(1064L);
        oldAnalyze.setParentCallId(1061L);
        PresaleAiCall newQuery = call(106L, 1, "qwen", 16L, "", "QUERY", "SUCCESS", "new answer");
        newQuery.setId(1063L);
        newQuery.setQueryContractVersion(PresaleSearchEvidence.CONTRACT_VERSION);
        newQuery.setSearchEvidenceJson("""
                {"searchTriggered":true,"searchStatus":"SUCCEEDED","evidenceLevel":"SOURCES",
                 "webConfigId":9,"webConfigVersion":4,"integrationType":"DASHSCOPE_NATIVE_WEB",
                 "modelId":"qwen-plus"}
                """);
        PresaleAiCall oldQuery = call(106L, 1, "qwen", 16L, "", "QUERY", "SUCCESS", "old answer");
        oldQuery.setId(1061L);
        when(aiCallMapper.selectList(any())).thenReturn(List.of(oldAnalyze, newQuery, oldQuery));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(106L, 1);
        PlatformCallContext ctx = new PlatformCallContext(106L, 1, "qwen", 16L, "", "Acme", 1L, false);

        assertEquals(ReuseDecision.REUSE_QUERY_ONLY,
                service.decide(ctx, cache, PresaleQueryWebMode.REQUIRED,
                        new CompanionIdentity(9L, 4L, IntegrationType.DASHSCOPE_NATIVE_WEB, "qwen-plus")));
    }

    @Test
    void mixedRequiredNativeRouteRejectsHistoricalWebQuery() {
        PresaleAiCall webQuery = call(107L, 1, "kimi", 17L, "", "QUERY", "SUCCESS", "web answer");
        webQuery.setId(1071L);
        webQuery.setQueryContractVersion(PresaleSearchEvidence.CONTRACT_VERSION);
        webQuery.setSearchEvidenceJson("{}");
        when(aiCallMapper.selectList(any())).thenReturn(List.of(webQuery));

        Map<ReuseDecisionService.ReuseKey, ReuseSnapshot> cache = service.preloadByVersionAndBatch(107L, 1);
        PlatformCallContext ctx = new PlatformCallContext(107L, 1, "kimi", 17L, "", "Acme", 1L, false);

        assertEquals(ReuseDecision.RUN_FULL, service.decideNative(ctx, cache));
    }

    private PresaleAiCall call(Long versionId,
                               int batchNo,
                               String platformCode,
                               Long promptTemplateId,
                               String competitorName,
                               String stage,
                               String status,
                               String rawResponse) {
        PresaleAiCall row = new PresaleAiCall();
        row.setVersionId(versionId);
        row.setBatchNo(batchNo);
        row.setPlatformCode(platformCode);
        row.setPromptTemplateId(promptTemplateId);
        row.setCompetitorName(competitorName);
        row.setStage(stage);
        row.setCallStatus(status);
        row.setRawResponse(rawResponse);
        return row;
    }
}
