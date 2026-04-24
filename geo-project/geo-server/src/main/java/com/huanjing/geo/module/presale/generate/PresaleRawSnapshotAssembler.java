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
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PresaleRawSnapshotAssembler {

    private static final Logger log = LoggerFactory.getLogger(PresaleRawSnapshotAssembler.class);
    private static final String CATEGORY_COGNITIVE = "认知型";
    private static final String CATEGORY_COMPARISON = "对比型";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_SKIPPED_DEGRADED = "SKIPPED_DEGRADED";
    @Value("${presale.prompt.active-version:v2}")
    private String activePromptTemplateVersion;
    private static final int MAX_SCENE_ADVANTAGES = 5;
    private static final int MAX_TOP_KEYWORDS = 10;
    private static final int MAX_NEGATIVE_EVIDENCE = 3;

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
            log.error("L1 aggregate JSON serialization error, versionId={}", versionId, ex);
            throw new BizException(500, "L1 aggregate failed: JSON serialization error - " + ex.getMessage());
        } catch (Exception ex) {
            log.error("L1 aggregate unexpected error, versionId={}", versionId, ex);
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
        Long count = aiPlatformConfigMapper.selectCount(PresalePlatformConfigQueries.presaleEnabledWrapper());
        return count == null ? 0 : count.intValue();
    }

    private int countPromptTemplates(int hasCompetitorVar) {
        Long count = promptTemplateMapper.selectCount(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getTemplateVersion, activePromptTemplateVersion)
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
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(PresalePlatformConfigQueries.presaleEnabledWrapper());
        if (platforms == null) {
            platforms = List.of();
        }
        Map<Long, String> categoryByTemplateId = loadActiveTemplateCategoryMap();
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
            if (batch1Rows == null) {
                batch1Rows = List.of();
            }

            List<PresaleAiPromptResult> sampleRows = batch1Rows.stream()
                    .filter(row -> {
                        Long templateId = row.getPromptTemplateId();
                        String category = templateId == null ? null : categoryByTemplateId.get(templateId);
                        return isSampleIntentCategory(category);
                    })
                    .toList();

            int totalTests = sampleRows.size();
            int mentionCount = (int) sampleRows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getIsMentioned()))
                    .count();
            double mentionRate = totalTests == 0 ? 0.0 : (mentionCount * 100.0 / totalTests);
            OptionalDouble avgRankingOpt = sampleRows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getIsMentioned()))
                    .filter(r -> r.getRanking() != null)
                    .mapToInt(PresaleAiPromptResult::getRanking)
                    .average();
            Double avgRanking = avgRankingOpt.isPresent() ? avgRankingOpt.getAsDouble() : null;
            int primaryRec = (int) sampleRows.stream()
                    .filter(r -> Integer.valueOf(1).equals(r.getRanking()))
                    .count();

            int positive = (int) sampleRows.stream()
                    .filter(r -> "POSITIVE".equals(r.getSentiment()))
                    .count();
            int neutral = (int) sampleRows.stream()
                    .filter(r -> "NEUTRAL".equals(r.getSentiment()))
                    .count();
            int negative = (int) sampleRows.stream()
                    .filter(r -> "NEGATIVE".equals(r.getSentiment()))
                    .count();

            out.add(PlatformBreakdown.builder()
                    .platformCode(platformCode)
                    .platformName(platform.getPlatformName() == null ? platformCode : platform.getPlatformName())
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

    private Map<Long, String> loadActiveTemplateCategoryMap() {
        List<PresalePromptTemplate> templates = promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresalePromptTemplate>()
                        .eq(PresalePromptTemplate::getEnabled, 1)
                        .eq(PresalePromptTemplate::getTemplateVersion, activePromptTemplateVersion)
        );
        if (templates == null || templates.isEmpty()) {
            return Map.of();
        }
        return templates.stream().collect(Collectors.toMap(
                PresalePromptTemplate::getId,
                PresalePromptTemplate::getCategory,
                (left, right) -> left
        ));
    }

    private boolean isSampleIntentCategory(String category) {
        return !CATEGORY_COGNITIVE.equals(category) && !CATEGORY_COMPARISON.equals(category);
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
        if (rows == null) {
            rows = new ArrayList<>();
        } else {
            rows = new ArrayList<>(rows);
        }
        rows.sort(Comparator.comparing(PresaleAiPromptResult::getId, Comparator.nullsLast(Long::compareTo)));

        int positive = (int) rows.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
        int neutral = (int) rows.stream().filter(r -> "NEUTRAL".equals(r.getSentiment())).count();
        int negative = (int) rows.stream().filter(r -> "NEGATIVE".equals(r.getSentiment())).count();

        List<SentimentDetail.SentimentKeyword> topKeywords = aggregateTopKeywords(rows);
        List<SentimentDetail.NegativeEvidence> negativeEvidence = aggregateNegativeEvidence(rows);

        return SentimentDetail.builder()
                .positiveCount(positive)
                .neutralCount(neutral)
                .negativeCount(negative)
                .topKeywords(topKeywords)
                .negativeEvidence(negativeEvidence)
                .build();
    }

    private List<SentimentDetail.SentimentKeyword> aggregateTopKeywords(List<PresaleAiPromptResult> rows) {
        Map<String, KeywordAgg> aggByKeyword = new LinkedHashMap<>();
        for (PresaleAiPromptResult row : rows) {
            String raw = row.getTopKeywordsJson();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                JsonNode arr = objectMapper.readTree(raw);
                if (!arr.isArray()) {
                    continue;
                }
                for (JsonNode item : arr) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    JsonNode keywordNode = item.get("keyword");
                    JsonNode sentimentNode = item.get("sentiment");
                    if (keywordNode == null || !keywordNode.isTextual() || sentimentNode == null || !sentimentNode.isTextual()) {
                        continue;
                    }
                    String keyword = keywordNode.asText().trim();
                    if (keyword.isEmpty()) {
                        continue;
                    }
                    SentimentDetail.Sentiment sentiment = parseSentimentEnum(sentimentNode.asText());
                    if (sentiment == null) {
                        continue;
                    }
                    KeywordAgg agg = aggByKeyword.get(keyword);
                    if (agg == null) {
                        aggByKeyword.put(keyword, new KeywordAgg(keyword, sentiment, 1));
                    } else {
                        agg.frequency += 1;
                    }
                }
            } catch (Exception ex) {
                log.warn("Skip invalid top_keywords_json, versionId={}, rowId={}", row.getVersionId(), row.getId(), ex);
            }
        }
        return aggByKeyword.values().stream()
                .sorted(Comparator
                        .comparingInt(KeywordAgg::frequency).reversed()
                        .thenComparing(KeywordAgg::keyword))
                .limit(MAX_TOP_KEYWORDS)
                .map(agg -> SentimentDetail.SentimentKeyword.builder()
                        .keyword(agg.keyword)
                        .frequency(agg.frequency)
                        .sentiment(agg.sentiment)
                        .build())
                .collect(Collectors.toList());
    }

    private List<SentimentDetail.NegativeEvidence> aggregateNegativeEvidence(List<PresaleAiPromptResult> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiPlatformConfig> platformRows = aiPlatformConfigMapper.selectList(PresalePlatformConfigQueries.presaleEnabledWrapper());
        if (platformRows == null) {
            platformRows = List.of();
        }
        Map<String, String> platformNameByCode = platformRows.stream()
                .filter(p -> p.getPlatformCode() != null)
                .collect(Collectors.toMap(AiPlatformConfig::getPlatformCode,
                        p -> p.getPlatformName() == null ? p.getPlatformCode() : p.getPlatformName(),
                        (a, b) -> a));

        List<Long> templateIds = rows.stream()
                .map(PresaleAiPromptResult::getPromptTemplateId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> promptByTemplateId;
        if (templateIds.isEmpty()) {
            promptByTemplateId = Map.of();
        } else {
            List<PresalePromptTemplate> templates = promptTemplateMapper.selectBatchIds(templateIds);
            if (templates == null) {
                templates = List.of();
            }
            promptByTemplateId = templates.stream()
                    .collect(Collectors.toMap(PresalePromptTemplate::getId,
                            t -> t.getPromptContent() == null ? "" : t.getPromptContent(),
                            (a, b) -> a));
        }

        List<Long> analyzeCallIds = rows.stream()
                .map(PresaleAiPromptResult::getAnalyzeCallId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, LocalDateTime> testedAtByAnalyzeCallId;
        if (analyzeCallIds.isEmpty()) {
            testedAtByAnalyzeCallId = Map.of();
        } else {
            List<PresaleAiCall> analyzeCalls = aiCallMapper.selectBatchIds(analyzeCallIds);
            if (analyzeCalls == null) {
                analyzeCalls = List.of();
            }
            testedAtByAnalyzeCallId = analyzeCalls.stream()
                    .collect(Collectors.toMap(PresaleAiCall::getId, PresaleAiCall::getCreatedAt, (a, b) -> a));
        }

        List<SentimentDetail.NegativeEvidence> result = new ArrayList<>();
        for (PresaleAiPromptResult row : rows) {
            String raw = row.getNegativeEvidenceJson();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(raw);
                if (!node.isObject()) {
                    continue;
                }
                JsonNode hasNegativeNode = node.get("has_negative");
                if (hasNegativeNode == null || !hasNegativeNode.isBoolean() || !hasNegativeNode.asBoolean()) {
                    continue;
                }
                JsonNode snippetNode = node.get("snippet");
                if (snippetNode == null || !snippetNode.isTextual() || snippetNode.asText().trim().isEmpty()) {
                    continue;
                }
                String platformCode = row.getPlatformCode();
                String platformName = platformNameByCode.getOrDefault(platformCode, platformCode);
                Long promptTemplateId = row.getPromptTemplateId();
                String query = promptTemplateId == null ? "" : promptByTemplateId.getOrDefault(promptTemplateId, "");
                Long analyzeCallId = row.getAnalyzeCallId();
                LocalDateTime testedAt = analyzeCallId == null ? null : testedAtByAnalyzeCallId.get(analyzeCallId);
                if (testedAt == null) {
                    testedAt = row.getCreatedAt();
                }
                result.add(SentimentDetail.NegativeEvidence.builder()
                        .platformCode(platformCode)
                        .platformName(platformName)
                        .query(query)
                        .snippet(snippetNode.asText().trim())
                        .testedAt(testedAt)
                        .build());
            } catch (Exception ex) {
                log.warn("Skip invalid negative_evidence_json, versionId={}, rowId={}", row.getVersionId(), row.getId(), ex);
            }
        }

        result.sort((a, b) -> {
            LocalDateTime at = a.getTestedAt();
            LocalDateTime bt = b.getTestedAt();
            if (at == null && bt == null) {
                return 0;
            }
            if (at == null) {
                return 1;
            }
            if (bt == null) {
                return -1;
            }
            return bt.compareTo(at);
        });
        if (result.size() > MAX_NEGATIVE_EVIDENCE) {
            return new ArrayList<>(result.subList(0, MAX_NEGATIVE_EVIDENCE));
        }
        return result;
    }

    private SentimentDetail.Sentiment parseSentimentEnum(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return SentimentDetail.Sentiment.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static final class KeywordAgg {
        private final String keyword;
        private final SentimentDetail.Sentiment sentiment;
        private int frequency;

        private KeywordAgg(String keyword, SentimentDetail.Sentiment sentiment, int frequency) {
            this.keyword = keyword;
            this.sentiment = sentiment;
            this.frequency = frequency;
        }

        private String keyword() {
            return keyword;
        }

        private int frequency() {
            return frequency;
        }
    }
}
