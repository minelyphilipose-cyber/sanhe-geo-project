package com.huanjing.geo.module.presale.generate.calc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneCoverageCalculatorTest {

    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private PresalePromptTemplateMapper promptTemplateMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresaleCompetitorAggregator competitorAggregator;

    @Test
    void happyPath_sceneCoverageAndIntentBreakdownAreConsistent() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, promptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(11L, "P11", "推荐型", "rec question"),
                template(12L, "P12", "问题型", "inq question"),
                template(13L, "P13", "场景型", "scene question")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(11L, "p1", 1, 1, "[\"Claude\"]"),
                row(11L, "p2", 1, 2, "[\"Gemini\"]"),
                row(12L, "p1", 1, 3, null)
        ));
        when(competitorAggregator.normalizeName(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0, String.class);
            return s == null ? "" : s.trim().replaceAll("\\s+", "").toLowerCase();
        });

        RawSnapshotDTO raw = raw(List.of(), List.of(
                Competitor.builder().name("Claude").build(),
                Competitor.builder().name("Gemini").build()
        ));
        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 1,
                "COGNITIVE", 0,
                "SCENARIO", 1
        );

        SceneAndIntentResult result = calculator.compute(1001L, raw, totals);

        assertEquals(1, result.sceneCoverage().getHighValue().getCovered());
        assertEquals(1, result.sceneCoverage().getHighValue().getTotal());
        assertEquals(0, result.sceneCoverage().getMidValue().getCovered());
        assertEquals(1, result.sceneCoverage().getMidValue().getTotal());
        assertEquals(0, result.sceneCoverage().getLowValue().getCovered());
        assertEquals(1, result.sceneCoverage().getLowValue().getTotal());

        // 同源断言: intent_breakdown.coveredPrompts == scene_coverage 各档覆盖之和(按意图映射)
        int recommendationCovered = result.intentBreakdown().stream()
                .filter(i -> "推荐型".equals(i.getCategory()))
                .findFirst().orElseThrow().getCoveredPrompts();
        int inquiryCovered = result.intentBreakdown().stream()
                .filter(i -> "问题型".equals(i.getCategory()))
                .findFirst().orElseThrow().getCoveredPrompts();
        int scenarioCovered = result.intentBreakdown().stream()
                .filter(i -> "场景型".equals(i.getCategory()))
                .findFirst().orElseThrow().getCoveredPrompts();
        assertEquals(1, recommendationCovered);
        assertEquals(0, inquiryCovered);
        assertEquals(0, scenarioCovered);
    }

    @Test
    void threePlatformsDegraded_thresholdUsesEffectivePlatforms() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, promptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4"), platform("p5"),
                platform("p6"), platform("p7"), platform("p8"), platform("p9")
        ));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(21L, "P21", "推荐型", "rec")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(21L, "p1", 1, 1, null),
                row(21L, "p2", 1, 1, null),
                row(21L, "p3", 1, 1, null)
        ));
        RawSnapshotDTO raw = raw(List.of("p7", "p8", "p9"), List.of());
        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );

        SceneAndIntentResult result = calculator.compute(2001L, raw, totals);
        // 9-3=6, threshold=ceil(6/2)=3, 命中3个平台即 covered
        assertEquals(1, result.sceneCoverage().getHighValue().getCovered());
    }

    @Test
    void missingQueriesWithTopCompetitors_returnsMatchedDisplayNames() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, promptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(promptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(31L, "P31", "推荐型", "missing rec prompt")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(31L, "p1", 0, null, "[\"Claude\", \"SomeOther\"]"),
                row(31L, "p2", 0, null, "[\"Unknown\"]")
        ));
        when(competitorAggregator.normalizeName(any())).thenAnswer(inv -> {
            String s = inv.getArgument(0, String.class);
            return s == null ? "" : s.trim().replaceAll("\\s+", "").toLowerCase();
        });

        RawSnapshotDTO raw = raw(List.of(), List.of(
                Competitor.builder().name("Claude").build(),
                Competitor.builder().name("Gemini").build(),
                Competitor.builder().name("Doubao").build()
        ));
        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );

        SceneAndIntentResult result = calculator.compute(3001L, raw, totals);
        List<String> coverage = result.sceneCoverage().getHighValue().getMissingQueries().get(0).getTopCompetitorCoverage();
        assertEquals(List.of("Claude"), coverage);
        assertTrue(result.sceneCoverage().getHighValue().getMissingQueries().get(0).getPromptContent().contains("missing"));
    }

    private RawSnapshotDTO raw(List<String> degradedPlatforms, List<Competitor> competitors) {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setTestSummary(TestSummary.builder().degradedPlatforms(degradedPlatforms).build());
        raw.setCompetitors(competitors);
        return raw;
    }

    private PresalePromptTemplate template(Long id, String promptCode, String category, String promptContent) {
        PresalePromptTemplate t = new PresalePromptTemplate();
        t.setId(id);
        t.setPromptCode(promptCode);
        t.setCategory(category);
        t.setPromptContent(promptContent);
        t.setEnabled(1);
        t.setHasCompetitorVar(0);
        t.setSortOrder(1);
        return t;
    }

    private PresaleAiPromptResult row(Long templateId,
                                      String platformCode,
                                      Integer isMentioned,
                                      Integer ranking,
                                      String mentionedCompetitors) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setVersionId(1L);
        row.setBatchNo(1);
        row.setPromptTemplateId(templateId);
        row.setPlatformCode(platformCode);
        row.setIsMentioned(isMentioned);
        row.setRanking(ranking);
        row.setMentionedCompetitors(mentionedCompetitors);
        return row;
    }

    private AiPlatformConfig platform(String platformCode) {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode(platformCode);
        return platform;
    }

}
