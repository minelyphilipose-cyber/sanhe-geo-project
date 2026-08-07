package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptJudgeResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresaleRawSnapshotAssemblerTest {

    @BeforeEach
    void initializeMybatisMetadata() {
        if (TableInfoHelper.getTableInfo(PresaleAiPromptResult.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    PresaleAiPromptResult.class);
        }
    }

    @Mock
    private PresaleAiCallMapper aiCallMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private PresaleAiPromptJudgeResultMapper judgeResultMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @Mock
    private PresaleBenchmarkResolver benchmarkResolver;
    @Mock
    private PresaleCompetitorAggregator competitorAggregator;

    @Test
    void happyPath_returnsRawJsonWithAllSevenSections() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();
        Set<String> degraded = Set.of();
        List<String> extracted = List.of("Claude");

        mockCommonCounts(2L, 1L, 10L, 7L);
        mockEnabledPlatforms(
                platform("bing_copilot", "Bing Copilot"),
                platform("kimi", "Kimi")
        );
        when(competitorAggregator.aggregateBatch1MentionStats(1001L, List.of("Acme")))
                .thenReturn(new PresaleCompetitorAggregator.Batch1MentionStats(
                        Map.of("claude", 2), Map.of("claude", "Claude"), 4
                ));
        when(competitorAggregator.normalizeName("Claude")).thenReturn("claude");
        when(competitorAggregator.matchCompetitorDisplayName(any(), any())).thenAnswer(inv -> {
            String rawName = inv.getArgument(0, String.class);
            List<String> candidates = inv.getArgument(1);
            return candidates.stream().filter(rawName::equals).findFirst();
        });
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());

        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(
                        promptResult(1L, 1, 1, "POSITIVE", null, null),
                        promptResult(2L, 0, null, "NEUTRAL", null, null)
                ),
                List.of(
                        promptResult(3L, 1, 2, "NEGATIVE", null, null)
                ),
                List.of(
                        promptResult(4L, 1, null, null, null, "[\"优势A\", \"优势B\"]", "Claude"),
                        promptResult(5L, 1, null, null, null, "[\"优势A\"]", "Claude")
                ),
                List.of(
                        promptResult(6L, null, null, "POSITIVE", null, null),
                        promptResult(7L, null, null, "NEUTRAL", null, null),
                        promptResult(8L, null, null, "NEGATIVE", null, null)
                )
        );

        String json = assembler.assemble(1001L, report, version, degraded, extracted);
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertNotNull(raw.getMeta());
        assertNotNull(raw.getClientInfo());
        assertNotNull(raw.getTestSummary());
        assertNotNull(raw.getPlatformBreakdown());
        assertNotNull(raw.getCompetitors());
        assertNotNull(raw.getSentimentDetail());
        assertNotNull(raw.getBenchmarksFrozen());

        assertEquals(3, raw.getTestSummary().getTotalPrompts());
        assertEquals(2, raw.getTestSummary().getTotalPlatforms());
        assertEquals(17, raw.getTestSummary().getTotalCalls());
        assertEquals(14, raw.getTestSummary().getSuccessfulCalls());
        assertEquals(3, raw.getTestSummary().getFailedCalls());
        assertEquals(raw.getTestSummary().getBatch1PromptTestCount() + raw.getTestSummary().getBatch2PromptTestCount(),
                raw.getTestSummary().getPromptTestCount());
        assertEquals(raw.getTestSummary().getQueryCallCount()
                        + raw.getTestSummary().getAnalyzeCallCount()
                        + raw.getTestSummary().getJudgeCallCount(),
                raw.getTestSummary().getTotalCalls());
        assertEquals(raw.getTestSummary().getSuccessCallCount() + raw.getTestSummary().getFailedCallCount(),
                raw.getTestSummary().getTotalCalls());
        assertFalse(raw.getTestSummary().getIsDegraded());

        assertEquals(2, raw.getPlatformBreakdown().size());

        assertEquals(1, raw.getCompetitors().size());
        assertEquals("Claude", raw.getCompetitors().get(0).getName());
        assertEquals(2, raw.getCompetitors().get(0).getMentionCount());
        assertEquals(50.0, raw.getCompetitors().get(0).getMentionRate());
        assertEquals(List.of("优势A", "优势B"), raw.getCompetitors().get(0).getSceneAdvantagesRaw());
    }

    @Test
    void zeroCompetitors_returnsEmptyCompetitorList() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(2L, 1L, 4L, 3L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(1L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(2L, null, null, "POSITIVE", null, null))
        );

        String json = assembler.assemble(1001L, report, version, Set.of(), List.of());
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertNotNull(raw.getCompetitors());
        assertEquals(0, raw.getCompetitors().size());
    }

    @Test
    void fixedExecutionPlatformsKeepCompanionOnlyLogicalPlatformInSnapshot() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(1L, 0L, 2L, 2L);
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(1L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(2L, 1, 1, "POSITIVE", null, null))
        );

        String json = assembler.assembleWithCompetitorStats(
                1001L, report, version, Set.of(), List.of(),
                List.of(platform("ernie", "文心一言"))
        );
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertEquals(1, raw.getTestSummary().getTotalPlatforms());
        assertEquals(1, raw.getPlatformBreakdown().size());
        assertEquals("ernie", raw.getPlatformBreakdown().get(0).getPlatformCode());
        assertEquals("文心一言", raw.getPlatformBreakdown().get(0).getPlatformName());
        verify(aiPlatformConfigMapper, never()).selectList(any());
    }

    @Test
    void testSummary_exposesRawAndWeightedMentionDenominators() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(3L, 0L, 6L, 6L);
        mockEnabledPlatforms(platform("doubao", "豆包"), platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());

        PresaleReportVersionPromptTemplate recommendation = template(101L, "推荐型");
        PresaleReportVersionPromptTemplate cognitive = template(102L, "认知型");
        List<PresaleReportVersionPromptTemplate> templates = List.of(recommendation, cognitive);

        PresaleAiPromptResult doubaoMentioned = promptResult(1L, 1, 1, "POSITIVE", null, null);
        doubaoMentioned.setPromptTemplateId(101L);
        PresaleAiPromptResult doubaoNotMentioned = promptResult(2L, 0, null, "NEUTRAL", null, null);
        doubaoNotMentioned.setPromptTemplateId(101L);
        PresaleAiPromptResult doubaoCognitive = promptResult(3L, 1, null, "POSITIVE", null, null);
        doubaoCognitive.setPromptTemplateId(102L);
        PresaleAiPromptResult kimiMentioned = promptResult(4L, 1, 1, "POSITIVE", null, null);
        kimiMentioned.setPromptTemplateId(101L);

        when(versionPromptTemplateMapper.selectList(any())).thenReturn(templates, templates);
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(doubaoMentioned, doubaoNotMentioned, doubaoCognitive),
                List.of(kimiMentioned),
                List.of()
        );

        String json = assembler.assemble(1001L, report, version, Set.of(), List.of());
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertEquals(3, raw.getTestSummary().getSampleQueryCountRaw());
        assertEquals(5, raw.getTestSummary().getMentionRateWeightedDenominator());
    }

    @Test
    void specifiedCompetitors_areFrozenAndReturnedEvenWithoutMentions() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        report.setSpecifiedCompetitors("[\"竞品A\",\"竞品B\",\"竞品C\"]");
        PresaleReportVersion version = version();

        mockCommonCounts(2L, 1L, 10L, 10L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(competitorAggregator.aggregateBatch1MentionStats(1001L, List.of("Acme")))
                .thenReturn(new PresaleCompetitorAggregator.Batch1MentionStats(Map.of(), Map.of(), 4));
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());
        when(aiPromptResultMapper.selectList(any())).thenReturn(List.of());

        String json = assembler.assembleWithCompetitorStats(
                1001L,
                report,
                version,
                Set.of(),
                List.of(
                        new PresaleCompetitorAggregator.ExtractedCompetitor("竞品A", 0, List.of("竞品A")),
                        new PresaleCompetitorAggregator.ExtractedCompetitor("竞品B", 0, List.of("竞品B")),
                        new PresaleCompetitorAggregator.ExtractedCompetitor("竞品C", 0, List.of("竞品C"))
                )
        );
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertEquals("specified", raw.getCompetitorSource());
        assertEquals(List.of("竞品A", "竞品B", "竞品C"), raw.getSpecifiedCompetitors());
        assertEquals(3, raw.getCompetitors().size());
        assertEquals(List.of("竞品A", "竞品B", "竞品C"),
                raw.getCompetitors().stream().map(c -> c.getName()).toList());
        assertEquals(0, raw.getCompetitors().get(0).getMentionCount());
    }

    @Test
    void singlePlatformDegraded_excludesPlatformAndSummaryStillFalse() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(2L, 1L, 6L, 6L);
        mockEnabledPlatforms(platform("bing_copilot", "Bing Copilot"), platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(1L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(2L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(3L, null, null, "POSITIVE", null, null))
        );

        String json = assembler.assemble(
                1001L, report, version, new LinkedHashSet<>(List.of("bing_copilot")), List.of()
        );
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertFalse(raw.getTestSummary().getIsDegraded());
        assertEquals(1, raw.getTestSummary().getTotalPlatforms());
        assertEquals(1, raw.getPlatformBreakdown().size());
        assertFalse(raw.getPlatformBreakdown().stream()
                .anyMatch(p -> "bing_copilot".equals(p.getPlatformCode())));
    }

    @Test
    void fourPlatformsDegraded_marksSummaryDegradedTrue() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(1L, 1L, 8L, 8L);
        mockEnabledPlatforms(
                platform("p1", "P1"),
                platform("p2", "P2"),
                platform("p3", "P3"),
                platform("p4", "P4")
        );
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(1L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(2L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(3L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(4L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(5L, null, null, "POSITIVE", null, null))
        );

        String json = assembler.assemble(
                1001L, report, version, new LinkedHashSet<>(List.of("p1", "p2", "p3", "p4")), List.of()
        );
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertTrue(raw.getTestSummary().getIsDegraded());
    }

    @Test
    void benchmarkMissing_exceptionBubblesUp() {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(1L, 0L, 2L, 2L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(1L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(2L, null, null, "POSITIVE", null, null))
        );
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class)))
                .thenThrow(new IllegalStateException("BENCHMARK_MISSING"));

        assertThrows(IllegalStateException.class, () ->
                assembler.assemble(1001L, report, version, Set.of(), List.of()));
    }

    @Test
    void sentimentDetail_aggregatesKeywordsAndNegativeEvidence_withSortingAndLimits() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(1L, 0L, 4L, 4L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());

        LocalDateTime t1 = LocalDateTime.of(2026, 4, 23, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 23, 10, 5);
        LocalDateTime t3 = LocalDateTime.of(2026, 4, 23, 10, 10);
        LocalDateTime t4 = LocalDateTime.of(2026, 4, 23, 10, 20);

        PresaleAiPromptResult r1 = promptResultWithSentimentPayload(1L, "NEUTRAL",
                "[{\"keyword\":\"性价比高\",\"sentiment\":\"POSITIVE\"},{\"keyword\":\"等位时间长\",\"sentiment\":\"NEGATIVE\"}]",
                "{\"has_negative\":true,\"snippet\":\"中性关注点不应进入负面池\"}", 11L, 101L, "kimi", "问句1", t1);
        PresaleAiPromptResult r2 = promptResultWithSentimentPayload(2L, "NEGATIVE",
                "[{\"keyword\":\"性价比高\",\"sentiment\":\"POSITIVE\"}]",
                "{\"has_negative\":true,\"snippet\":\"证据B\"}", 12L, 102L, "kimi", "问句2", t2);
        PresaleAiPromptResult r3 = promptResultWithSentimentPayload(3L, "NEUTRAL",
                "[{\"keyword\":\"服务稳定\",\"sentiment\":\"POSITIVE\"}]",
                "{\"has_negative\":false,\"snippet\":null}", 13L, 103L, "kimi", "问句3", t3);
        PresaleAiPromptResult r4 = promptResultWithSentimentPayload(4L, "NEGATIVE",
                "[]",
                "{\"has_negative\":true,\"snippet\":\"证据C\"}", 14L, 104L, "kimi", "问句4", t4);
        PresaleAiPromptResult unmentioned = promptResultWithSentimentPayload(5L, "POSITIVE",
                "[{\"keyword\":\"行业通用好评\",\"sentiment\":\"POSITIVE\"}]",
                "{\"has_negative\":true,\"snippet\":\"不应进入品牌情感\"}", 15L, 105L, "kimi", "问句5", t4.plusMinutes(5));
        unmentioned.setIsMentioned(0);

        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(10L, 1, 1, "POSITIVE", null, null)),
                List.of(r1, r2, r3, r4, unmentioned)
        );

        when(aiCallMapper.selectBatchIds(any())).thenReturn(List.of(
                aiCall(11L, t1),
                aiCall(12L, t2),
                aiCall(13L, t3),
                aiCall(14L, t4)
        ));

        String json = assembler.assemble(1001L, report, version, Set.of(), List.of());
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertNotNull(raw.getSentimentDetail());
        assertEquals(0, raw.getSentimentDetail().getPositiveCount());
        assertEquals(2, raw.getSentimentDetail().getNeutralCount());
        assertEquals(2, raw.getSentimentDetail().getNegativeCount());
        assertEquals(3, raw.getSentimentDetail().getTopKeywords().size());
        assertTrue(raw.getSentimentDetail().getTopKeywords().stream()
                .noneMatch(item -> "行业通用好评".equals(item.getKeyword())));
        assertEquals("性价比高", raw.getSentimentDetail().getTopKeywords().get(0).getKeyword());
        assertEquals(2, raw.getSentimentDetail().getTopKeywords().get(0).getFrequency());
        assertEquals(SentimentDetail.Sentiment.POSITIVE, raw.getSentimentDetail().getTopKeywords().get(0).getSentiment());

        assertEquals(2, raw.getSentimentDetail().getNegativeEvidence().size());
        assertEquals("证据C", raw.getSentimentDetail().getNegativeEvidence().get(0).getSnippet());
        assertEquals(SentimentDetail.Sentiment.NEGATIVE, raw.getSentimentDetail().getNegativeEvidence().get(0).getSentiment());
        assertEquals("问句4", raw.getSentimentDetail().getNegativeEvidence().get(0).getQuery());
        assertEquals("证据B", raw.getSentimentDetail().getNegativeEvidence().get(1).getSnippet());
        assertEquals(SentimentDetail.Sentiment.NEGATIVE, raw.getSentimentDetail().getNegativeEvidence().get(1).getSentiment());
        assertTrue(raw.getSentimentDetail().getNegativeEvidence().stream()
                .noneMatch(item -> "不应进入品牌情感".equals(item.getSnippet())));
    }

    @Test
    void sentimentDetail_emptyPayloads_returnsEmptyArraysNotNull() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(1L, 0L, 2L, 2L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve(eq("科技"), eq("CTO"), any(java.time.LocalDate.class))).thenReturn(benchmark());

        PresaleAiPromptResult sentimentOnly = promptResultWithSentimentPayload(1L, "NEUTRAL",
                "[]", "{}", null, 101L, "kimi", "问句1", LocalDateTime.now());
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(10L, 1, 1, "POSITIVE", null, null)),
                List.of(sentimentOnly)
        );

        String json = assembler.assemble(1001L, report, version, Set.of(), List.of());
        RawSnapshotDTO raw = new ObjectMapper().readValue(json, RawSnapshotDTO.class);

        assertNotNull(raw.getSentimentDetail().getTopKeywords());
        assertTrue(raw.getSentimentDetail().getTopKeywords().isEmpty());
        assertNotNull(raw.getSentimentDetail().getNegativeEvidence());
        assertTrue(raw.getSentimentDetail().getNegativeEvidence().isEmpty());
    }

    private PresaleRawSnapshotAssembler createAssembler() {
        return new PresaleRawSnapshotAssembler(
                aiCallMapper,
                aiPromptResultMapper,
                judgeResultMapper,
                aiPlatformConfigMapper,
                versionPromptTemplateMapper,
                benchmarkResolver,
                competitorAggregator,
                new ObjectMapper()
        );
    }

    private void mockCommonCounts(Long batch1TemplateCount,
                                  Long batch2TemplateCount,
                                  Long totalCalls,
                                  Long successfulCalls) {
        when(versionPromptTemplateMapper.selectCount(any())).thenReturn(batch1TemplateCount, batch2TemplateCount);
        when(aiCallMapper.selectCount(any())).thenReturn(totalCalls, successfulCalls);
    }

    private void mockEnabledPlatforms(AiPlatformConfig... platforms) {
        when(aiPlatformConfigMapper.selectList(any())).thenReturn(List.of(platforms));
    }

    private PresaleReport report() {
        PresaleReport report = new PresaleReport();
        report.setId(2001L);
        report.setBrandName("Acme");
        report.setIndustry("科技");
        report.setIndustryRole("CTO");
        report.setRegion("CN");
        report.setUserDemand("Need GEO visibility");
        return report;
    }

    private PresaleReportVersion version() {
        PresaleReportVersion version = new PresaleReportVersion();
        version.setId(1001L);
        version.setVersionNo(1);
        version.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        return version;
    }

    private PresaleReportVersionPromptTemplate template(Long id, String category) {
        PresaleReportVersionPromptTemplate template = new PresaleReportVersionPromptTemplate();
        template.setId(id);
        template.setReportVersionId(1001L);
        template.setCategory(category);
        template.setHasCompetitorVar(0);
        template.setPromptContent(category + "问题");
        return template;
    }

    private BenchmarksFrozen benchmark() {
        return BenchmarksFrozen.builder()
                .industry("科技")
                .industryRole("_ALL_")
                .matchLevel(MatchLevel.FALLBACK_INDUSTRY)
                .industryAvg(ScoreSet.builder().overall(60.0).build())
                .top1(ScoreSet.builder().overall(90.0).build())
                .top10Score(80.0)
                .confidenceLevel(BenchmarksFrozen.ConfidenceLevel.HIGH)
                .source(BenchmarksFrozen.Source.AUTO_P50)
                .sampleSize(100)
                .build();
    }

    private PresaleAiPromptResult promptResult(Long id,
                                               Integer isMentioned,
                                               Integer ranking,
                                               String sentiment,
                                               String mentionedCompetitors,
                                               String sceneAdvantages) {
        return promptResult(id, isMentioned, ranking, sentiment, mentionedCompetitors, sceneAdvantages, null);
    }

    private PresaleAiPromptResult promptResult(Long id,
                                               Integer isMentioned,
                                               Integer ranking,
                                               String sentiment,
                                               String mentionedCompetitors,
                                               String sceneAdvantages,
                                               String competitorName) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setId(id);
        row.setIsMentioned(isMentioned);
        row.setRanking(ranking);
        row.setSentiment(sentiment);
        row.setMentionedCompetitors(mentionedCompetitors);
        row.setSceneAdvantages(sceneAdvantages);
        row.setCompetitorName(competitorName);
        row.setTopKeywordsJson("[]");
        row.setNegativeEvidenceJson("{}");
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private PresaleAiPromptResult promptResultWithSentimentPayload(Long id,
                                                                   String sentiment,
                                                                   String topKeywordsJson,
                                                                   String negativeEvidenceJson,
                                                                   Long analyzeCallId,
                                                                   Long promptTemplateId,
                                                                   String platformCode,
                                                                   String requestPromptContent,
                                                                   LocalDateTime createdAt) {
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setId(id);
        row.setSentiment(sentiment);
        row.setIsMentioned(1);
        row.setTopKeywordsJson(topKeywordsJson);
        row.setNegativeEvidenceJson(negativeEvidenceJson);
        row.setAnalyzeCallId(analyzeCallId);
        row.setPromptTemplateId(promptTemplateId);
        row.setPlatformCode(platformCode);
        row.setRequestPromptContent(requestPromptContent);
        row.setCreatedAt(createdAt);
        return row;
    }

    private AiPlatformConfig platform(String code, String name) {
        AiPlatformConfig p = new AiPlatformConfig();
        p.setPlatformCode(code);
        p.setPlatformName(name);
        return p;
    }

    private PresaleAiCall aiCall(Long id, LocalDateTime createdAt) {
        PresaleAiCall call = new PresaleAiCall();
        call.setId(id);
        call.setCreatedAt(createdAt);
        return call;
    }
}
