package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator.ExtractedCompetitor;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator.RawCompetitorMention;
import com.huanjing.geo.module.presale.generate.llm.CallStatus;
import com.huanjing.geo.module.presale.generate.llm.LlmCallResult;
import com.huanjing.geo.module.presale.generate.llm.PlatformCallContext;
import com.huanjing.geo.module.presale.generate.llm.PresaleLlmInvoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleCompetitorNormalizationServiceTest {

    @Mock
    private PresaleLlmInvoker llmInvoker;
    @Mock
    private PresaleEvaluationModelRouter evaluationModelRouter;

    @Test
    void normalize_mergesAliasesAndRecomputesTop3Counts() throws Exception {
        PresaleCompetitorNormalizationService service = createService();
        mockEvaluationPlatform();
        when(llmInvoker.normalizeCompetitors(any(), anyString())).thenReturn(new LlmCallResult(
                """
                        {
                          "normalized_competitors": [
                            {"canonical_name":"皇派门窗","aliases":["皇派门窗","皇派"]},
                            {"canonical_name":"新豪轩门窗","aliases":["新豪轩门窗","新豪轩"]},
                            {"canonical_name":"派雅门窗","aliases":["派雅门窗","派雅"]}
                          ]
                        }
                        """,
                10, 20, 100L, 0, CallStatus.SUCCESS
        ));

        PresaleCompetitorNormalizationService.NormalizationOutcome outcome = service.normalize(
                214L,
                "广州诗帝尼门窗有限公司",
                List.of(
                        raw("皇派门窗", 80),
                        raw("新豪轩门窗", 68),
                        raw("新豪轩", 41),
                        raw("派雅门窗", 35),
                        raw("皇派", 35),
                        raw("派雅", 21)
                ),
                1L,
                true
        );

        assertTrue(outcome.llmCalled());
        assertEquals(3, outcome.competitors().size());
        assertCompetitor(outcome.competitors().get(0), "皇派门窗", 115, List.of("皇派门窗", "皇派"));
        assertCompetitor(outcome.competitors().get(1), "新豪轩门窗", 109, List.of("新豪轩门窗", "新豪轩"));
        assertCompetitor(outcome.competitors().get(2), "派雅门窗", 56, List.of("派雅门窗", "派雅"));
    }

    @Test
    void normalize_rejectsInventedAliasesAndKeepsMissingNamesAsSingletons() throws Exception {
        PresaleCompetitorNormalizationService service = createService();
        mockEvaluationPlatform();
        when(llmInvoker.normalizeCompetitors(any(), anyString())).thenReturn(new LlmCallResult(
                """
                        {
                          "normalized_competitors": [
                            {"canonical_name":"新豪轩集团","aliases":["新豪轩门窗","新豪轩","不存在品牌"]}
                          ]
                        }
                        """,
                10, 20, 100L, 0, CallStatus.SUCCESS
        ));

        PresaleCompetitorNormalizationService.NormalizationOutcome outcome = service.normalize(
                214L,
                "广州诗帝尼门窗有限公司",
                List.of(
                        raw("皇派门窗", 80),
                        raw("新豪轩门窗", 68),
                        raw("新豪轩", 41)
                ),
                1L,
                true
        );

        assertEquals("新豪轩门窗", outcome.competitors().get(0).name());
        assertEquals(109, outcome.competitors().get(0).mentionCount());
        assertEquals("皇派门窗", outcome.competitors().get(1).name());
        assertEquals(80, outcome.competitors().get(1).mentionCount());
    }

    @Test
    void normalize_fallbacksToRawTopWhenNoPlatform() {
        PresaleCompetitorNormalizationService service = createService();
        when(evaluationModelRouter.routeContexts(any())).thenReturn(List.of());

        PresaleCompetitorNormalizationService.NormalizationOutcome outcome = service.normalize(
                214L,
                "广州诗帝尼门窗有限公司",
                List.of(raw("皇派门窗", 80), raw("新豪轩门窗", 68), raw("新豪轩", 41), raw("派雅门窗", 35)),
                1L,
                true
        );

        assertFalse(outcome.llmCalled());
        assertEquals(List.of("皇派门窗", "新豪轩门窗", "新豪轩"),
                outcome.competitors().stream().map(ExtractedCompetitor::name).toList());
    }

    private PresaleCompetitorNormalizationService createService() {
        return new PresaleCompetitorNormalizationService(llmInvoker, evaluationModelRouter, new ObjectMapper());
    }

    private void mockEvaluationPlatform() {
        when(evaluationModelRouter.routeContexts(any()))
                .thenReturn(List.of(new PlatformCallContext(214L, 1, "deepseek", null, "", "品牌", 1L, true)));
    }

    private RawCompetitorMention raw(String name, int count) {
        return new RawCompetitorMention(name, count, name);
    }

    private void assertCompetitor(ExtractedCompetitor actual, String name, int count, List<String> aliases) {
        assertEquals(name, actual.name());
        assertEquals(count, actual.mentionCount());
        assertEquals(aliases, actual.aliases());
    }
}
