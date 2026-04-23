package com.huanjing.geo.module.presale.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleRawSnapshotAssemblerTest {

    @Mock
    private PresaleAiCallMapper aiCallMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresalePromptTemplateMapper promptTemplateMapper;
    @Mock
    private PresaleBenchmarkResolver benchmarkResolver;
    @Mock
    private PresaleCompetitorAggregator competitorAggregator;

    @Test
    void happyPath_returnsRawJsonWithAllSevenSections() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();
        Set<String> degraded = new LinkedHashSet<>(List.of("bing_copilot"));
        List<String> extracted = List.of("Claude");

        mockCommonCounts(2L, 2L, 1L, 10L, 7L);
        mockEnabledPlatforms(
                platform("bing_copilot", "Bing Copilot"),
                platform("kimi", "Kimi")
        );
        when(competitorAggregator.aggregateBatch1MentionStats(1001L, "Acme"))
                .thenReturn(new PresaleCompetitorAggregator.Batch1MentionStats(
                        Map.of("claude", 2), Map.of("claude", "Claude"), 4
                ));
        when(competitorAggregator.normalizeName("Claude")).thenReturn("claude");
        when(benchmarkResolver.resolve("科技", "CTO")).thenReturn(benchmark());

        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(
                        promptResult(1L, 1, 1, "POSITIVE", null, null),
                        promptResult(2L, 0, null, "NEUTRAL", null, null)
                ),
                List.of(
                        promptResult(3L, 1, 2, "NEGATIVE", null, null)
                ),
                List.of(
                        promptResult(4L, 1, null, null, null, "[\"优势A\", \"优势B\"]"),
                        promptResult(5L, 1, null, null, null, "[\"优势A\"]")
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
        assertEquals(10, raw.getTestSummary().getTotalCalls());
        assertEquals(7, raw.getTestSummary().getSuccessfulCalls());
        assertEquals(3, raw.getTestSummary().getFailedCalls());
        assertFalse(raw.getTestSummary().getIsDegraded());

        assertEquals(2, raw.getPlatformBreakdown().size());
        assertTrue(raw.getPlatformBreakdown().stream()
                .anyMatch(p -> "bing_copilot".equals(p.getPlatformCode()) && Boolean.TRUE.equals(p.getIsDegraded())));

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

        mockCommonCounts(1L, 2L, 1L, 4L, 3L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve("科技", "CTO")).thenReturn(benchmark());
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
    void singlePlatformDegraded_marksPlatformTrueButSummaryFalse() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(2L, 2L, 1L, 6L, 6L);
        mockEnabledPlatforms(platform("bing_copilot", "Bing Copilot"), platform("kimi", "Kimi"));
        when(benchmarkResolver.resolve("科技", "CTO")).thenReturn(benchmark());
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
        assertTrue(raw.getPlatformBreakdown().stream()
                .anyMatch(p -> "bing_copilot".equals(p.getPlatformCode()) && Boolean.TRUE.equals(p.getIsDegraded())));
    }

    @Test
    void fourPlatformsDegraded_marksSummaryDegradedTrue() throws Exception {
        PresaleRawSnapshotAssembler assembler = createAssembler();
        PresaleReport report = report();
        PresaleReportVersion version = version();

        mockCommonCounts(4L, 1L, 1L, 8L, 8L);
        mockEnabledPlatforms(
                platform("p1", "P1"),
                platform("p2", "P2"),
                platform("p3", "P3"),
                platform("p4", "P4")
        );
        when(benchmarkResolver.resolve("科技", "CTO")).thenReturn(benchmark());
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

        mockCommonCounts(1L, 1L, 0L, 2L, 2L);
        mockEnabledPlatforms(platform("kimi", "Kimi"));
        when(aiPromptResultMapper.selectList(any())).thenReturn(
                List.of(promptResult(1L, 1, 1, "POSITIVE", null, null)),
                List.of(promptResult(2L, null, null, "POSITIVE", null, null))
        );
        when(benchmarkResolver.resolve("科技", "CTO"))
                .thenThrow(new IllegalStateException("BENCHMARK_MISSING"));

        assertThrows(IllegalStateException.class, () ->
                assembler.assemble(1001L, report, version, Set.of(), List.of()));
    }

    private PresaleRawSnapshotAssembler createAssembler() {
        return new PresaleRawSnapshotAssembler(
                aiCallMapper,
                aiPromptResultMapper,
                aiPlatformConfigMapper,
                promptTemplateMapper,
                benchmarkResolver,
                competitorAggregator,
                new ObjectMapper()
        );
    }

    private void mockCommonCounts(Long platformCount,
                                  Long batch1TemplateCount,
                                  Long batch2TemplateCount,
                                  Long totalCalls,
                                  Long successfulCalls) {
        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(platformCount);
        when(promptTemplateMapper.selectCount(any())).thenReturn(batch1TemplateCount, batch2TemplateCount);
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
        PresaleAiPromptResult row = new PresaleAiPromptResult();
        row.setId(id);
        row.setIsMentioned(isMentioned);
        row.setRanking(ranking);
        row.setSentiment(sentiment);
        row.setMentionedCompetitors(mentionedCompetitors);
        row.setSceneAdvantages(sceneAdvantages);
        return row;
    }

    private AiPlatformConfig platform(String code, String name) {
        AiPlatformConfig p = new AiPlatformConfig();
        p.setPlatformCode(code);
        p.setPlatformName(name);
        p.setEnabled(true);
        return p;
    }
}
