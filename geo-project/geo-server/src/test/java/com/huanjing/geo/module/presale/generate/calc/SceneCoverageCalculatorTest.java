package com.huanjing.geo.module.presale.generate.calc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.generate.PresaleCompetitorAggregator;
import com.huanjing.geo.module.presale.generate.PromptJudgeSignalRow;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void dealerCoverageUsesWeightedHalfThresholdAndExcludesBrandOnlyRows() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        List<AiPlatformConfig> platforms = IntStream.range(0, 100)
                .mapToObj(i -> platform("p" + i))
                .toList();
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(platforms);
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(901L, "P901", "推荐型", "49 percent uncovered"),
                template(902L, "P902", "推荐型", "50 percent covered")
        ));
        List<PresaleAiPromptResult> rows = new java.util.ArrayList<>();
        IntStream.range(0, 49).forEach(i -> rows.add(row(901L, "p" + i, 1, 1, null, null, null)));
        IntStream.range(0, 50).forEach(i -> rows.add(row(902L, "p" + i, 1, 1, null, null, null)));
        // 代理品牌单独曝光在持久化口径中 is_mentioned=0，不得进入门店覆盖分子。
        rows.add(row(901L, "p49", 1, 0, null, null, null));
        when(aiPromptResultMapper.selectList(any())).thenReturn(rows);

        RawSnapshotDTO raw = raw(List.of(), List.of());
        raw.setClientInfo(ClientInfo.builder().attributionMode("DEALER").build());
        SceneAndIntentResult result = calculator.compute(9001L, raw, Map.of(
                "RECOMMENDATION", 2, "COMPARISON", 0, "INQUIRY", 0, "COGNITIVE", 0, "SCENARIO", 0
        ));

        assertEquals(1, result.sceneCoverage().getHighValue().getCovered());
        assertEquals(List.of("50 percent covered"), result.sceneCoverage().getHighValue().getCoveredQueries()
                .stream().map(item -> item.getPromptContent()).toList());
        assertEquals(List.of("49 percent uncovered"), result.sceneCoverage().getHighValue().getMissingQueries()
                .stream().map(item -> item.getPromptContent()).toList());
    }

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
        when(aiPromptResultMapper.selectPromptJudgeSignalsByVersionId(any())).thenReturn(List.of(
                signal(61L, "p1", "COMPARISON", "SUCCESS", null, "target"),
                signal(65L, "p1", "COGNITIVE", "SUCCESS", "0.2", null)
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
    void platformThresholdUsesFixedRawSnapshotInsteadOfCurrentPresaleSwitches() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper,
                competitorAggregator, new ObjectMapper());
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(71L, "P71", "推荐型", "recommendation")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(71L, "ernie", 1, 1, null)
        ));
        RawSnapshotDTO raw = raw(List.of(), List.of());
        raw.setPlatformBreakdown(List.of(
                PlatformBreakdown.builder().platformCode("ernie").platformName("文心一言").build(),
                PlatformBreakdown.builder().platformCode("kimi").platformName("Kimi").build()
        ));

        SceneAndIntentResult result = calculator.compute(7001L, raw, Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        ));

        assertEquals(1, result.sceneCoverage().getHighValue().getCovered());
        verify(aiPlatformConfigMapper, never()).selectList(any());
    }

    @Test
    void sampleIntentCoverage_prefersDoubaoMentionBeforeMajorityThreshold() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("doubao"), platform("kimi"), platform("deepseek"), platform("qianwen")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(81L, "REC_081", "推荐型", "doubao covered")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(81L, "doubao", 1, 1, null)
        ));

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );

        SceneAndIntentResult result = calculator.compute(8101L, raw(List.of(), List.of()), totals);

        assertEquals(1, result.sceneCoverage().getHighValue().getCovered());
        assertEquals(1, result.intentBreakdown().stream()
                .filter(i -> "推荐型".equals(i.getCategory()))
                .findFirst().orElseThrow().getCoveredPrompts());
    }

    @Test
    void sampleIntentCoverage_fallsBackToPreviousThresholdWhenDoubaoMissing() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("doubao"), platform("kimi"), platform("deepseek"), platform("qianwen")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(82L, "REC_082", "推荐型", "fallback covered")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(82L, "kimi", 1, 1, null),
                row(82L, "deepseek", 1, 2, null)
        ));

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );

        SceneAndIntentResult result = calculator.compute(8201L, raw(List.of(), List.of()), totals);

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
        when(competitorAggregator.matchCompetitorDisplayName(any(), any())).thenAnswer(inv -> {
            String s = inv.getArgument(0, String.class);
            List<String> candidates = inv.getArgument(1);
            return candidates.stream().filter(s::equals).findFirst();
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
    void missingQueriesWithTopCompetitors_matchesAliasBySharedNormalizer() {
        PresaleCompetitorAggregator realAggregator = new PresaleCompetitorAggregator(
                null, new ObjectMapper(), new com.huanjing.geo.module.presale.generate.CompetitorNameNormalizer());
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, realAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(131L, "P131", "推荐型", "missing rec prompt")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(131L, "p1", 0, null, "[\"阜阳美奥口腔医院\"]")
        ));

        RawSnapshotDTO raw = raw(List.of(), List.of(
                Competitor.builder().name("阜阳市人民医院口腔科").build(),
                Competitor.builder().name("美奥口腔").build(),
                Competitor.builder().name("阜阳市第二人民医院口腔科").build()
        ));
        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 1,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );

        SceneAndIntentResult result = calculator.compute(3002L, raw, totals);

        List<String> coverage = result.sceneCoverage().getHighValue().getMissingQueries().get(0).getTopCompetitorCoverage();
        assertEquals(List.of("美奥口腔"), coverage);
    }

    @Test
    void duplicatePromptCodeUsesOnePromptCoveragePartition() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(71L, "REC_001", "推荐型", "covered prompt"),
                template(72L, "REC_002", "推荐型", "duplicate prompt missing copy"),
                template(73L, "REC_002", "推荐型", "duplicate prompt covered copy")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(71L, "p1", 1, 1, null),
                row(73L, "p1", 1, 1, null)
        ));

        Map<String, Integer> totals = Map.of(
                "RECOMMENDATION", 2,
                "COMPARISON", 0,
                "INQUIRY", 0,
                "COGNITIVE", 0,
                "SCENARIO", 0
        );

        SceneAndIntentResult result = calculator.compute(7001L, raw(List.of(), List.of()), totals);

        assertEquals(2, result.sceneCoverage().getHighValue().getTotal());
        assertEquals(2, result.sceneCoverage().getHighValue().getCovered());
        assertEquals(0, result.sceneCoverage().getHighValue().getMissingQueries().size());
        assertEquals(2, result.sceneCoverage().getHighValue().getCovered()
                + result.sceneCoverage().getHighValue().getMissingQueries().size());
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
        when(aiPromptResultMapper.selectPromptJudgeSignalsByVersionId(any())).thenReturn(List.of(
                signal(41L, "p1", "COMPARISON", "SUCCESS", null, "target"),
                signal(42L, "p1", "COMPARISON", "SUCCESS", null, "target"),
                signal(43L, "p1", "COMPARISON", "SUCCESS", null, "target"),
                signal(44L, "p1", "COMPARISON", "SUCCESS", null, "target")
        ));

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
        when(aiPromptResultMapper.selectPromptJudgeSignalsByVersionId(any())).thenReturn(List.of(
                signal(46L, "p1", "COGNITIVE", "SUCCESS", "0.1", null)
        ));

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
    void cognitiveSceneCoverageRequiresPromptLevelMentionOrJudgeHit() {
        SceneCoverageCalculator calculator = new SceneCoverageCalculator(
                aiPromptResultMapper, versionPromptTemplateMapper, aiPlatformConfigMapper, competitorAggregator, new ObjectMapper());
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(
                platform("p1"), platform("p2"), platform("p3"), platform("p4")
        ));
        when(versionPromptTemplateMapper.selectList(any())).thenReturn(List.of(
                template(48L, "P48", "认知型", "market landscape")
        ));
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of(
                row(48L, "p1", 1, 0, null, null, "market landscape"),
                row(48L, "p2", 1, 0, null, null, "market landscape")
        ));
        when(aiPromptResultMapper.selectPromptJudgeSignalsByVersionId(any())).thenReturn(List.of(
                signal(48L, "p1", "COGNITIVE", "SUCCESS", "0.0", null),
                signal(48L, "p2", "COGNITIVE", "SUCCESS", "0.0", null)
        ));

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

        SceneAndIntentResult result = calculator.compute(4801L, raw(List.of(), List.of()), totals, cells);

        assertEquals(1, result.intentBreakdown().stream()
                .filter(i -> "认知型".equals(i.getCategory()))
                .findFirst().orElseThrow().getCoveredPrompts());
        assertEquals(0, result.sceneCoverage().getMidValue().getCovered());
        assertEquals(1, result.sceneCoverage().getMidValue().getMissingQueries().size());
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
        when(aiPromptResultMapper.selectPromptJudgeSignalsByVersionId(any())).thenReturn(List.of(
                signal(51L, "p1", "COMPARISON", "SUCCESS", null, "target")
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
                .judgeScore(score)
                .totalPrompts(4)
                .platformPromptCount(4)
                .judgeSampleCount(4)
                .stance(stance)
                .judgeStance(stance)
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

    private PromptJudgeSignalRow signal(Long templateId,
                                        String platformCode,
                                        String category,
                                        String judgeStatus,
                                        String attributeHitRate,
                                        String preferredBrand) {
        PromptJudgeSignalRow row = new PromptJudgeSignalRow();
        row.setPromptTemplateId(templateId);
        row.setPlatformCode(platformCode);
        row.setCategory(category);
        row.setJudgeStatus(judgeStatus);
        row.setAttributeHitRate(attributeHitRate == null ? null : new BigDecimal(attributeHitRate));
        row.setPreferredBrand(preferredBrand);
        return row;
    }

    private AiPlatformConfig platform(String platformCode) {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode(platformCode);
        return platform;
    }

}
