package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReuseDecisionServiceTest {

    @Mock
    private PresaleAiCallMapper aiCallMapper;

    @InjectMocks
    private ReuseDecisionService service;

    @Test
    void analyzeSuccessPresent_returnsSkipAll_evenIfOtherRowsExist() {
        PresaleAiCall analyzeSuccess = call(100L, 1, "kimi", 10L, "", "ANALYZE", "SUCCESS", null);
        PresaleAiCall analyzeFailed = call(100L, 1, "kimi", 10L, "", "ANALYZE", "FAILED", null);
        PresaleAiCall querySuccess = call(100L, 1, "kimi", 10L, "", "QUERY", "SUCCESS", "answer");
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
