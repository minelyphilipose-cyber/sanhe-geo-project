package com.huanjing.geo.module.presale.generate.calc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
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
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresaleCompetitorAggregator competitorAggregator;

    @Test
    void happyPath_sceneCoverageAndIntentBreakdownAreConsistent() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
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
    void sceneCoverageGroupsUseConfiguredBusinessValueOrder() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(61L, "P61", "对比型", "comparison", 1),
                template(62L, "P62", "推荐型", "recommendation"),
                template(63L, "P63", "问题型", "inquiry"),
                template(64L, "P64", "场景型", "scenario"),
                template(65L, "P65", "认知型", "cognitive")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(62L, "p1", 1, 1, null),
                row(64L, "p1", 1, 1, null)
        ));

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 1,
                "INQUIRY", 1,
                "COGNITIVE", 1,
                "SCENARIO", 1
        );
        List<PlatformIntentCell> cells = List.of(
                judgeCell("p1", "COMPARISON", 10, "target"),
                judgeCell("p1", "COGNITIVE", 10)
        );

        SceneAndIntentResult result = calculator.compute(6001L, raw(List.of(), List.of()), totals, cells);

        assertEquals(List.of("推荐型", "对比型"), result.sceneCoverage().getHighValue().getCoveredQueries()
                .stream().map(item -> item.getCategory()).toList());
        assertEquals(List.of("认知型", "场景型"), result.sceneCoverage().getMidValue().getCoveredQueries()
                .stream().map(item -> item.getCategory()).toList());
        assertEquals(List.of("问题型"), result.sceneCoverage().getLowValue().getMissingQueries()
                .stream().map(item -> item.getCategory()).toList());
    }

    @Test
    void threePlatformsDegraded_thresholdUsesEffectivePlatforms() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4"), platform("p5"),
                platform("p6"), platform("p7"), platform("p8"), platform("p9")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
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
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
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

    @Test
    void comparisonCoverageRequiresScoreThresholdAndNonCompetitorStanceOnOneThirdPlatforms() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(41L, "P41", "对比型", "comparison 1", 1),
                template(42L, "P42", "对比型", "comparison 2", 1),
                template(43L, "P43", "对比型", "comparison 3", 1),
                template(44L, "P44", "对比型", "comparison 4", 1)
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

        RawSnapshotDTO raw = raw(List.of(), List.of());
        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 0,
                "COMPARISON", 4,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );
        List<PlatformIntentCell> cells = List.of(
                judgeCell("p1", "COMPARISON", 10, "target"),
                judgeCell("p2", "COMPARISON", 60, "tie"),
                judgeCell("p3", "COMPARISON", 90, "competitor"),
                judgeCell("p4", "COMPARISON", 9, "target")
        );

        SceneAndIntentResult result = calculator.compute(4001L, raw, totals, cells);
        assertEquals(2, result.intentBreakdown().stream()
                .filter(i -> "对比型".equals(i.getCategory()))
                .findFirst().orElseThrow().getCoveredPrompts());
        assertEquals(4, result.sceneCoverage().getHighValue().getCovered());
    }

    @Test
    void comparisonCoverageFallsBackToMissingWhenLessThanOneThirdPlatformsMeetThreshold() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(45L, "P45", "对比型", "comparison missing", 1)
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 0,
                "COMPARISON", 1,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );
        List<PlatformIntentCell> cells = List.of(
                judgeCell("p1", "COMPARISON", 80, "competitor"),
                judgeCell("p2", "COMPARISON", 9, "target"),
                judgeCell("p3", "COMPARISON", null, null),
                judgeCell("p4", "COMPARISON", 70, "target")
        );

        SceneAndIntentResult result = calculator.compute(4501L, raw(List.of(), List.of()), totals, cells);

        assertEquals(0, result.sceneCoverage().getHighValue().getCovered());
        assertEquals(1, result.sceneCoverage().getHighValue().getMissingQueries().size());
    }

    @Test
    void cognitiveCoverageRequiresScoreThresholdOnOneThirdPlatforms() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(46L, "P46", "认知型", "cognitive")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 0,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 1,
                "SCENARIO", 0
        );
        List<PlatformIntentCell> cells = List.of(
                judgeCell("p1", "COGNITIVE", 10),
                judgeCell("p2", "COGNITIVE", 40),
                judgeCell("p3", "COGNITIVE", 9),
                judgeCell("p4", "COGNITIVE", null)
        );

        SceneAndIntentResult result = calculator.compute(4601L, raw(List.of(), List.of()), totals, cells);

        assertEquals(1, result.sceneCoverage().getMidValue().getCovered());
        assertEquals(1, result.sceneCoverage().getMidValue().getCoveredQueries().size());
    }

    @Test
    void cognitiveCoverageFallsBackToMissingWhenLessThanOneThirdPlatformsMeetThreshold() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(47L, "P47", "认知型", "cognitive missing")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 0,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 1,
                "SCENARIO", 0
        );
        List<PlatformIntentCell> cells = List.of(
                judgeCell("p1", "COGNITIVE", 10),
                judgeCell("p2", "COGNITIVE", 9),
                judgeCell("p3", "COGNITIVE", null),
                judgeCell("p4", "COGNITIVE", 0)
        );

        SceneAndIntentResult result = calculator.compute(4701L, raw(List.of(), List.of()), totals, cells);

        assertEquals(0, result.sceneCoverage().getMidValue().getCovered());
        assertEquals(1, result.sceneCoverage().getMidValue().getMissingQueries().size());
    }

    @Test
    void comparisonSceneQueriesUseRenderedBatch2PromptContent() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(51L, "P51", "对比型", "{brand}和{competitor}相比有什么优势?", 1)
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(51L, "p1", 2, 1, null, null, "吾悦广场和万达广场相比有什么优势?")
        ));

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 0,
                "COMPARISON", 1,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );
        List<PlatformIntentCell> cells = List.of(
                judgeCell("p1", "COMPARISON", 10, "target"),
                judgeCell("p2", "COMPARISON", 11, "tie")
        );

        SceneAndIntentResult result = calculator.compute(5001L, raw(List.of(), List.of()), totals, cells);

        assertEquals("吾悦广场和万达广场相比有什么优势?",
                result.sceneCoverage().getHighValue().getCoveredQueries().get(0).getPromptContent());
    }

    private RawSnapshotDTO raw(List<String> degradedPlatforms, List<Competitor> competitors) {
        RawSnapshotDTO raw = new RawSnapshotDTO();
        raw.setTestSummary(TestSummary.builder().degradedPlatforms(degradedPlatforms).build());
        raw.setCompetitors(competitors);
        return raw;
    }

    private PresaleReportVersionPromptTemplate template(Long id, String promptCode, String category, String promptContent) {
        return template(id, promptCode, category, promptContent, 0);
    }

    private PresaleReportVersionPromptTemplate template(Long id, String promptCode, String category, String promptContent, Integer hasCompetitorVar) {
        PresaleReportVersionPromptTemplate t = new PresaleReportVersionPromptTemplate();
        t.setId(id);
        t.setSourcePromptCode(promptCode);
        t.setCategory(category);
        t.setPromptContent(promptContent);
        t.setHasCompetitorVar(hasCompetitorVar);
        t.setSortOrderInVersion(1);
        return t;
    }

    private PlatformIntentCell judgeCell(String platformCode, String intentCode, Integer score) {
        return judgeCell(platformCode, intentCode, score, null);
    }

    private PlatformIntentCell judgeCell(String platformCode, String intentCode, Integer score, String stance) {
        return PlatformIntentCell.builder()
                .platformCode(platformCode)
                .intentCode(intentCode)
                .intentLabel("COMPARISON".equals(intentCode) ? "对比型" : "认知型")
                .mentionRate(score)
                .totalPrompts(4)
                .platformPromptCount(4)
                .stance(stance)
                .build();
    }

    private PresaleAiPromptResult row(Long templateId,
                                      String platformCode,
                                      Integer isMentioned,
                                      Integer ranking,
                                      String mentionedCompetitors) {
        return row(templateId, platformCode, 1, isMentioned, ranking, mentionedCompetitors, null);
    }

    private PresaleAiPromptResult row(Long templateId,
                                      String platformCode,
                                      Integer batchNo,
                                      Integer isMentioned,
                                      Integer ranking,
                                      String mentionedCompetitors,
                                      String requestPromptContent) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setVersionId(1L);
        row.setBatchNo(batchNo);
        row.setPromptTemplateId(templateId);
        row.setPlatformCode(platformCode);
        row.setIsMentioned(isMentioned);
        row.setRanking(ranking);
        row.setMentionedCompetitors(mentionedCompetitors);
        row.setRequestPromptContent(requestPromptContent);
        return row;
    }

    private AiPlatformConfig platform(String platformCode) {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode(platformCode);
        return platform;
    }

}
