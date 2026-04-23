package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.ClientInfo;
import com.huanjing.geo.module.presale.dto.snapshot.raw.Competitor;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawMeta;
import com.huanjing.geo.module.presale.dto.snapshot.raw.RawSnapshotDTO;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresalePromptTemplate;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

@Component
public class PresaleRawSnapshotAssembler {

    private static final Logger log = LoggerFactory.getLogger(PresaleRawSnapshotAssembler.class);
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_SKIPPED_DEGRADED = "SKIPPED_DEGRADED";
    private static final int MAX_SCENE_ADVANTAGES = 5;

    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresalePromptTemplateMapper promptTemplateMapper;
    private final PresaleBenchmarkResolver benchmarkResolver;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final ObjectMapper objectMapper;

    public PresaleRawSnapshotAssembler(PresaleAiCallMapper aiCallMapper,
                                       PresaleAiPromptResultMapper aiPromptResultMapper,
                                       AiPlatformConfigMapper aiPlatformConfigMapper,
                                       PresalePromptTemplateMapper promptTemplateMapper,
                                       PresaleBenchmarkResolver benchmarkResolver,
                                       PresaleCompetitorAggregator competitorAggregator,
                                       ObjectMapper objectMapper) {
        this.aiCallMapper = aiCallMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.benchmarkResolver = benchmarkResolver;
        this.competitorAggregator = competitorAggregator;
        this.objectMapper = objectMapper;
    }

    public String assemble(Long versionId,
                           PresaleReport report,
                           PresaleReportVersion version,
                           Set<String> degradedPlatforms,
                           List<String> extractedCompetitorDisplayNames) {
        try {
            RawMeta meta = buildMeta(report, version);
            ClientInfo clientInfo = buildClientInfo(report);
            TestSummary testSummary = buildTestSummary(versionId, degradedPlatforms, extractedCompetitorDisplayNames);
            List<PlatformBreakdown> platformBreakdown = buildPlatformBreakdown(versionId, degradedPlatforms);
            List<Competitor> competitors = buildCompetitors(
                    versionId, report, extractedCompetitorDisplayNames);
            SentimentDetail sentimentDetail = buildSentimentDetail(versionId);
            BenchmarksFrozen benchmarksFrozen = benchmarkResolver.resolve(
                    report.getIndustry(), report.getIndustryRole());

            RawSnapshotDTO raw = RawSnapshotDTO.builder()
                    .meta(meta)
                    .clientInfo(clientInfo)
                    .testSummary(testSummary)
                    .platformBreakdown(platformBreakdown)
                    .competitors(competitors)
                    .sentimentDetail(sentimentDetail)
                    .benchmarksFrozen(benchmarksFrozen)
                    .build();
            return objectMapper.writeValueAsString(raw);
        } catch (BizException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "L1 aggregate failed: JSON serialization error - " + ex.getMessage());
        } catch (Exception ex) {
            throw new BizException(500, "L1 aggregate failed: " + ex.getMessage());
        }
    }

    private RawMeta buildMeta(PresaleReport report, PresaleReportVersion version) {
        LocalDateTime now = LocalDateTime.now();
        return RawMeta.builder()
                .reportId(report.getId())
                .versionNo(version.getVersionNo())
                .generatedAt(now)
                .generationDurationSeconds(computeDurationSeconds(version, now))
                .formulaVersion("v1.0")
                .build();
    }

    private Integer computeDurationSeconds(PresaleReportVersion version, LocalDateTime now) {
        if (version.getCreatedAt() == null) {
            log.warn("computeDurationSeconds fallback to 0, versionId={} createdAt is null", version.getId());
            return 0;
        }
        long seconds = Duration.between(version.getCreatedAt(), now).toSeconds();
        if (seconds < 0) {
            return 0;
        }
        if (seconds > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) seconds;
    }

    private ClientInfo buildClientInfo(PresaleReport report) {
        return ClientInfo.builder()
                .brandName(report.getBrandName())
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .build();
    }

    private TestSummary buildTestSummary(Long versionId,
                                         Set<String> degradedPlatforms,
                                         List<String> extractedCompetitorDisplayNames) {
        int platformCount = countEnabledPlatforms();
        int genericPromptCount = countPromptTemplates(0);
        int competitorPromptCount = countPromptTemplates(1);
        int competitorCount = extractedCompetitorDisplayNames == null ? 0 : extractedCompetitorDisplayNames.size();

        int totalPrompts = genericPromptCount + (competitorPromptCount * competitorCount);
        int totalCalls = countNonSkippedCalls(versionId);
        int successfulCalls = countCallsByStatus(versionId, STATUS_SUCCESS);
        int failedCalls = Math.max(0, totalCalls - successfulCalls);

        Set<String> safeDegradedSet = degradedPlatforms == null ? Set.of() : degradedPlatforms;
        return TestSummary.builder()
                .totalPrompts(totalPrompts)
                .totalPlatforms(platformCount)
                .totalCalls(totalCalls)
                .successfulCalls(successfulCalls)
                .failedCalls(failedCalls)
                .excludedCount(0)
                .rounds(2)
                .isDegraded(safeDegradedSet.size() >= 4)
                .degradedPlatforms(new ArrayList<>(safeDegradedSet))
                .build();
    }

    private int countEnabledPlatforms() {
        Long count = aiPlatformConfigMapper.selectCount(
                new LambdaQueryWrapper<AiPlatformConfig>().eq(AiPlatformConfig::getEnabled, true)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countPromptTemplates(int hasCompetitorVar) {
        Long count = promptTemplateMapper.selectCount(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getHasCompetitorVar, hasCompetitorVar)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countNonSkippedCalls(Long versionId) {
        Long count = aiCallMapper.selectCount(
                new LambdaQueryWrapper<PresaleAiCall>()
                        .eq(PresaleAiCall::getVersionId, versionId)
                        .in(PresaleAiCall::getBatchNo, 1, 2)
                        .in(PresaleAiCall::getStage, "QUERY", "ANALYZE")
                        .ne(PresaleAiCall::getCallStatus, STATUS_SKIPPED_DEGRADED)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countCallsByStatus(Long versionId, String status) {
        Long count = aiCallMapper.selectCount(
                new LambdaQueryWrapper<PresaleAiCall>()
                        .eq(PresaleAiCall::getVersionId, versionId)
                        .in(PresaleAiCall::getBatchNo, 1, 2)
                        .in(PresaleAiCall::getStage, "QUERY", "ANALYZE")
                        .eq(PresaleAiCall::getCallStatus, status)
        );
        return count == null ? 0 : count.intValue();
    }

    private List<PlatformBreakdown> buildPlatformBreakdown(Long versionId, Set<String> degradedPlatforms) {
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .orderByAsc(AiPlatformConfig::getPlatformCode)
        );
        Set<String> safeDegraded = degradedPlatforms == null ? Set.of() : degradedPlatforms;
        List<PlatformBreakdown> out = new ArrayList<>();
        for (AiPlatformConfig platform : platforms) {
            String platformCode = platform.getPlatformCode();
            List<PresaleAiPromptResult> batch1Rows = aiPromptResultMapper.selectList(
                    new LambdaQueryWrapper<PresaleAiPromptResult>()
                            .eq(PresaleAiPromptResult::getVersionId, versionId)
                            .eq(PresaleAiPromptResult::getPlatformCode, platformCode)
                            .eq(PresaleAiPromptResult::getBatchNo, 1)
                            .isNotNull(PresaleAiPromptResult::getIsMentioned)
            );

            int totalTests = batch1Rows.size();
            int mentionCount = (int) batch1Rows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getIsMentioned()))
                    .count();
            double mentionRate = totalTests == 0 ? 0.0 : (mentionCount * 100.0 / totalTests);
            OptionalDouble avgRankingOpt = batch1Rows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getIsMentioned()))
                    .filter(r -> r.getRanking() != null)
                    .mapToInt(PresaleAiPromptResult::getRanking)
                    .average();
            Double avgRanking = avgRankingOpt.isPresent() ? avgRankingOpt.getAsDouble() : null;
            int primaryRec = (int) batch1Rows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getRanking()))
                    .count();

            int positive = (int) batch1Rows.stream()
                    .filter(r -> "POSITIVE".equals(r.getSentiment()))
                    .count();
            int neutral = (int) batch1Rows.stream()
                    .filter(r -> "NEUTRAL".equals(r.getSentiment()))
                    .count();
            int negative = (int) batch1Rows.stream()
                    .filter(r -> "NEGATIVE".equals(r.getSentiment()))
                    .count();

            out.add(PlatformBreakdown.builder()
                    .platformCode(platformCode)
                    .platformName(platform.getPlatformName())
                    .totalTests(totalTests)
                    .mentionCount(mentionCount)
                    .mentionRate(mentionRate)
                    .avgRanking(avgRanking)
                    .primaryRecommendationCount(primaryRec)
                    .sentimentDistribution(PlatformBreakdown.SentimentDistribution.builder()
                            .positive(positive)
                            .neutral(neutral)
                            .negative(negative)
                            .build())
                    .isDegraded(safeDegraded.contains(platformCode))
                    .build());
        }
        return out;
    }

    private List<Competitor> buildCompetitors(Long versionId,
                                              PresaleReport report,
                                              List<String> extractedCompetitorDisplayNames) {
        if (extractedCompetitorDisplayNames == null || extractedCompetitorDisplayNames.isEmpty()) {
            return List.of();
        }
        PresaleCompetitorAggregator.Batch1MentionStats stats =
                competitorAggregator.aggregateBatch1MentionStats(versionId, report.getBrandName());

        int rank = 1;
        List<Competitor> out = new ArrayList<>();
        for (String competitorDisplayName : extractedCompetitorDisplayNames) {
            String normalized = competitorAggregator.normalizeName(competitorDisplayName);
            int mentionCount = stats.countByNormalized().getOrDefault(normalized, 0);
            double mentionRate = stats.denominatorRows() == 0 ? 0.0
                    : (mentionCount * 100.0 / stats.denominatorRows());
            out.add(Competitor.builder()
                    .rank(rank++)
                    .name(competitorDisplayName)
                    .mentionCount(mentionCount)
                    .mentionRate(mentionRate)
                    .avgRanking(null)
                    .sceneAdvantagesRaw(aggregateSceneAdvantages(versionId, competitorDisplayName))
                    .build());
        }
        return out;
    }

    private List<String> aggregateSceneAdvantages(Long versionId, String competitorDisplayName) {
        List<PresaleAiPromptResult> batch2Rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .eq(PresaleAiPromptResult::getBatchNo, 2)
                        .eq(PresaleAiPromptResult::getCompetitorName, competitorDisplayName)
                        .isNotNull(PresaleAiPromptResult::getIsMentioned)
        );
        Map<String, Integer> freq = new HashMap<>();
        for (PresaleAiPromptResult row : batch2Rows) {
            String sceneAdvantages = row.getSceneAdvantages();
            if (sceneAdvantages == null || sceneAdvantages.isBlank()) {
                continue;
            }
            try {
                JsonNode arr = objectMapper.readTree(sceneAdvantages);
                if (!arr.isArray()) {
                    continue;
                }
                for (JsonNode item : arr) {
                    if (!item.isTextual()) {
                        continue;
                    }
                    String scene = item.asText().trim();
                    if (scene.isEmpty()) {
                        continue;
                    }
                    freq.merge(scene, 1, Integer::sum);
                }
            } catch (Exception ex) {
                log.warn("Skip invalid scene_advantages, versionId={}, rowId={}", versionId, row.getId(), ex);
            }
        }
        return freq.entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_SCENE_ADVANTAGES)
                .map(Map.Entry::getKey)
                .toList();
    }

    private SentimentDetail buildSentimentDetail(Long versionId) {
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .isNotNull(PresaleAiPromptResult::getSentiment)
        );
        int positive = (int) rows.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
        int neutral = (int) rows.stream().filter(r -> "NEUTRAL".equals(r.getSentiment())).count();
        int negative = (int) rows.stream().filter(r -> "NEGATIVE".equals(r.getSentiment())).count();
        return SentimentDetail.builder()
                .positiveCount(positive)
                .neutralCount(neutral)
                .negativeCount(negative)
                .topKeywords(null)
                .negativeEvidence(null)
                .build();
    }
}
