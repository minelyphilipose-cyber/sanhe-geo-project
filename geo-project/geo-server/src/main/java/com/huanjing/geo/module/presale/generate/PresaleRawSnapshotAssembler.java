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
import com.huanjing.geo.module.presale.dto.snapshot.raw.SamplePrompt;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiCall;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptJudgeResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersionPromptTemplate;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiCallMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptJudgeResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
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
    private static final List<String> PAGE03_SAMPLE_CATEGORIES = List.of("推荐型", "问题型", "场景型");
    private static final String CATEGORY_COGNITIVE = "认知型";
    private static final String CATEGORY_COMPARISON = "对比型";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_SKIPPED_DEGRADED = "SKIPPED_DEGRADED";
    private static final String STAGE_QUERY = "QUERY";
    private static final String STAGE_ANALYZE = "ANALYZE";
    private static final String JUDGE_PREFERRED_TARGET = "target";
    private static final String JUDGE_PREFERRED_COMPETITOR = "competitor";
    private static final String JUDGE_PREFERRED_TIE = "tie";
    private static final String JUDGE_PREFERRED_UNCLEAR = "unclear";
    private static final int MAX_SCENE_ADVANTAGES = 5;
    private static final int MAX_TOP_KEYWORDS = 10;
    private static final int MAX_NEGATIVE_EVIDENCE = 3;
    private static final String PLATFORM_DOUBAO = "doubao";
    private static final int DOUBAO_WEIGHT = 2;
    private static final String COMPETITOR_SOURCE_SPECIFIED = "specified";
    private static final String COMPETITOR_SOURCE_EXTRACTED = "extracted";

    private final PresaleAiCallMapper aiCallMapper;
    private final PresaleAiPromptResultMapper aiPromptResultMapper;
    private final PresaleAiPromptJudgeResultMapper judgeResultMapper;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    private final PresaleBenchmarkResolver benchmarkResolver;
    private final PresaleCompetitorAggregator competitorAggregator;
    private final ObjectMapper objectMapper;

    public PresaleRawSnapshotAssembler(PresaleAiCallMapper aiCallMapper,
                                       PresaleAiPromptResultMapper aiPromptResultMapper,
                                       PresaleAiPromptJudgeResultMapper judgeResultMapper,
                                       AiPlatformConfigMapper aiPlatformConfigMapper,
                                       PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper,
                                       PresaleBenchmarkResolver benchmarkResolver,
                                       PresaleCompetitorAggregator competitorAggregator,
                                       ObjectMapper objectMapper) {
        this.aiCallMapper = aiCallMapper;
        this.aiPromptResultMapper = aiPromptResultMapper;
        this.judgeResultMapper = judgeResultMapper;
        this.aiPlatformConfigMapper = aiPlatformConfigMapper;
        this.versionPromptTemplateMapper = versionPromptTemplateMapper;
        this.benchmarkResolver = benchmarkResolver;
        this.competitorAggregator = competitorAggregator;
        this.objectMapper = objectMapper;
    }

    public String assemble(Long versionId,
                           PresaleReport report,
                           PresaleReportVersion version,
                           Set<String> degradedPlatforms,
                           List<String> extractedCompetitorDisplayNames) {
        return assemble(versionId, report, version, degradedPlatforms,
                extractedCompetitorDisplayNames, null);
    }

    public String assemble(Long versionId,
                           PresaleReport report,
                           PresaleReportVersion version,
                           Set<String> degradedPlatforms,
                           List<String> extractedCompetitorDisplayNames,
                           List<AiPlatformConfig> reportPlatforms) {
        List<PresaleCompetitorAggregator.ExtractedCompetitor> extractedCompetitors =
                buildLegacyExtractedCompetitors(versionId, report, extractedCompetitorDisplayNames);
        return assembleWithCompetitorStats(versionId, report, version, degradedPlatforms,
                extractedCompetitors, reportPlatforms);
    }

    public String assembleWithCompetitorStats(Long versionId,
                                              PresaleReport report,
                                              PresaleReportVersion version,
                                              Set<String> degradedPlatforms,
                                              List<PresaleCompetitorAggregator.ExtractedCompetitor> extractedCompetitors) {
        return assembleWithCompetitorStats(versionId, report, version, degradedPlatforms,
                extractedCompetitors, null);
    }

    public String assembleWithCompetitorStats(Long versionId,
                                              PresaleReport report,
                                              PresaleReportVersion version,
                                              Set<String> degradedPlatforms,
                                              List<PresaleCompetitorAggregator.ExtractedCompetitor> extractedCompetitors,
                                              List<AiPlatformConfig> reportPlatforms) {
        try {
            List<String> extractedCompetitorDisplayNames = extractedCompetitors == null
                    ? List.of()
                    : extractedCompetitors.stream()
                    .map(PresaleCompetitorAggregator.ExtractedCompetitor::name)
                    .toList();
            RawMeta meta = buildMeta(report, version);
            ClientInfo clientInfo = buildClientInfo(report);
            List<AiPlatformConfig> fixedReportPlatforms = normalizeReportPlatforms(reportPlatforms);
            List<PlatformBreakdown> platformBreakdown = buildPlatformBreakdown(
                    versionId, degradedPlatforms, fixedReportPlatforms);
            TestSummary testSummary = buildTestSummary(versionId, degradedPlatforms, extractedCompetitorDisplayNames, platformBreakdown);
            List<Competitor> competitors = buildCompetitors(versionId, report, extractedCompetitors);
            List<String> specifiedCompetitors = parseSpecifiedCompetitors(report);
            List<String> groupSceneAdvantages = aggregateGroupSceneAdvantages(versionId, extractedCompetitorDisplayNames);
            List<SamplePrompt> samplePrompts = buildSamplePrompts(versionId);
            SentimentDetail sentimentDetail = buildSentimentDetail(versionId, fixedReportPlatforms);
            BenchmarksFrozen benchmarksFrozen = benchmarkResolver.resolve(
                    report.getIndustry(), report.getIndustryRole());

            RawSnapshotDTO raw = RawSnapshotDTO.builder()
                    .meta(meta)
                    .clientInfo(clientInfo)
                    .testSummary(testSummary)
                    .platformBreakdown(platformBreakdown)
                    .competitors(competitors)
                    .competitorSource(specifiedCompetitors.isEmpty() ? COMPETITOR_SOURCE_EXTRACTED : COMPETITOR_SOURCE_SPECIFIED)
                    .specifiedCompetitors(specifiedCompetitors)
                    .groupSceneAdvantages(groupSceneAdvantages)
                    .samplePrompts(samplePrompts)
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
                .brandFormerNames(parseBrandFormerNames(report))
                .industry(report.getIndustry())
                .industryRole(report.getIndustryRole())
                .representedBrands(emptyToNull(parseJsonStringArray(
                        report.getRepresentedBrands(), "represented_brands", report.getId())))
                .region(report.getRegion())
                .userDemand(report.getUserDemand())
                .build();
    }

    private List<String> emptyToNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private TestSummary buildTestSummary(Long versionId,
                                         Set<String> degradedPlatforms,
                                         List<String> extractedCompetitorDisplayNames,
                                         List<PlatformBreakdown> platformBreakdown) {
        Set<String> safeDegradedSet = degradedPlatforms == null ? Set.of() : degradedPlatforms;
        int platformCount = platformBreakdown == null ? 0 : platformBreakdown.size();
        int genericPromptCount = countPromptTemplates(versionId, 0);
        int competitorPromptCount = countPromptTemplates(versionId, 1);
        int competitorCount = extractedCompetitorDisplayNames == null ? 0 : extractedCompetitorDisplayNames.size();

        int totalPrompts = genericPromptCount + (competitorCount > 0 ? competitorPromptCount : 0);
        int batch1PromptTestCount = countPromptResults(versionId, 1, safeDegradedSet);
        int batch2PromptTestCount = countPromptResults(versionId, 2, safeDegradedSet);
        int promptTestCount = batch1PromptTestCount + batch2PromptTestCount;
        int sampleQueryCountRaw = platformBreakdown == null ? 0 : platformBreakdown.stream()
                .mapToInt(p -> p.getTotalTests() == null ? 0 : p.getTotalTests())
                .sum();
        int mentionRateWeightedDenominator = platformBreakdown == null ? 0 : platformBreakdown.stream()
                .mapToInt(p -> (p.getTotalTests() == null ? 0 : p.getTotalTests()) * platformWeight(p.getPlatformCode()))
                .sum();
        int queryCallCount = countCallsByStage(versionId, STAGE_QUERY, safeDegradedSet);
        int analyzeCallCount = countCallsByStage(versionId, STAGE_ANALYZE, safeDegradedSet);
        int judgeCallCount = countJudgeRows(versionId, safeDegradedSet);
        int totalCalls = queryCallCount + analyzeCallCount + judgeCallCount;
        int successfulCalls = countCallsByStageAndStatus(versionId, STAGE_QUERY, STATUS_SUCCESS, safeDegradedSet)
                + countCallsByStageAndStatus(versionId, STAGE_ANALYZE, STATUS_SUCCESS, safeDegradedSet)
                + countJudgeRowsByStatus(versionId, STATUS_SUCCESS, safeDegradedSet);
        int failedCalls = Math.max(0, totalCalls - successfulCalls);

        TestSummary out = new TestSummary();
        out.setTotalPrompts(totalPrompts);
        out.setTotalPlatforms(platformCount);
        out.setTotalCalls(totalCalls);
        out.setPromptTestCount(promptTestCount);
        setOptionalInteger(out, "setSampleQueryCountRaw", sampleQueryCountRaw);
        setOptionalInteger(out, "setMentionRateWeightedDenominator", mentionRateWeightedDenominator);
        out.setBatch1PromptTestCount(batch1PromptTestCount);
        out.setBatch2PromptTestCount(batch2PromptTestCount);
        out.setQueryCallCount(queryCallCount);
        out.setAnalyzeCallCount(analyzeCallCount);
        out.setJudgeCallCount(judgeCallCount);
        out.setSuccessfulCalls(successfulCalls);
        out.setSuccessCallCount(successfulCalls);
        out.setFailedCalls(failedCalls);
        out.setFailedCallCount(failedCalls);
        out.setExcludedCount(0);
        out.setRounds(2);
        out.setIsDegraded(safeDegradedSet.size() >= 4);
        out.setDegradedPlatforms(new ArrayList<>(safeDegradedSet));
        out.setDegradedPlatformCount(safeDegradedSet.size());
        return out;
    }

    private void setOptionalInteger(TestSummary target, String setterName, Integer value) {
        try {
            TestSummary.class.getMethod(setterName, Integer.class).invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            // 热加载场景下旧 DTO 类可能暂时没有新增口径字段;跳过即可,不影响报告生成主链路。
        } catch (Exception ex) {
            log.warn("Skip optional TestSummary field, setter={}", setterName, ex);
        }
    }

    private int countPromptTemplates(Long versionId, int hasCompetitorVar) {
        Long count = versionPromptTemplateMapper.selectCount(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, hasCompetitorVar)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countNonSkippedCalls(Long versionId, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiCall> wrapper = new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId)
                .in(PresaleAiCall::getBatchNo, 1, 2)
                .in(PresaleAiCall::getStage, "QUERY", "ANALYZE")
                .ne(PresaleAiCall::getCallStatus, STATUS_SKIPPED_DEGRADED);
        excludeDegradedCallPlatforms(wrapper, degradedPlatforms);
        Long count = aiCallMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private int countPromptResults(Long versionId, int batchNo, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiPromptResult> wrapper = new LambdaQueryWrapper<PresaleAiPromptResult>()
                .eq(PresaleAiPromptResult::getVersionId, versionId)
                .eq(PresaleAiPromptResult::getEffectiveSample, true)
                .eq(PresaleAiPromptResult::getBatchNo, batchNo);
        if (degradedPlatforms != null && !degradedPlatforms.isEmpty()) {
            wrapper.notIn(PresaleAiPromptResult::getPlatformCode, degradedPlatforms);
        }
        Long count = aiPromptResultMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private int countCallsByStage(Long versionId, String stage, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiCall> wrapper = new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId)
                .in(PresaleAiCall::getBatchNo, 1, 2)
                .eq(PresaleAiCall::getStage, stage)
                .ne(PresaleAiCall::getCallStatus, STATUS_SKIPPED_DEGRADED);
        excludeDegradedCallPlatforms(wrapper, degradedPlatforms);
        Long count = aiCallMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private int countCallsByStageAndStatus(Long versionId, String stage, String status, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiCall> wrapper = new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId)
                .in(PresaleAiCall::getBatchNo, 1, 2)
                .eq(PresaleAiCall::getStage, stage)
                .eq(PresaleAiCall::getCallStatus, status);
        excludeDegradedCallPlatforms(wrapper, degradedPlatforms);
        Long count = aiCallMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private int countCallsByStatus(Long versionId, String status, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiCall> wrapper = new LambdaQueryWrapper<PresaleAiCall>()
                .eq(PresaleAiCall::getVersionId, versionId)
                .in(PresaleAiCall::getBatchNo, 1, 2)
                .in(PresaleAiCall::getStage, "QUERY", "ANALYZE")
                .eq(PresaleAiCall::getCallStatus, status);
        excludeDegradedCallPlatforms(wrapper, degradedPlatforms);
        Long count = aiCallMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private int countJudgeRows(Long versionId, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiPromptJudgeResult> wrapper = new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                .eq(PresaleAiPromptJudgeResult::getVersionId, versionId);
        excludeDegradedJudgePlatforms(wrapper, degradedPlatforms);
        Long count = judgeResultMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private int countJudgeRowsByStatus(Long versionId, String status, Set<String> degradedPlatforms) {
        LambdaQueryWrapper<PresaleAiPromptJudgeResult> wrapper = new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                .eq(PresaleAiPromptJudgeResult::getVersionId, versionId)
                .eq(PresaleAiPromptJudgeResult::getJudgeStatus, status);
        excludeDegradedJudgePlatforms(wrapper, degradedPlatforms);
        Long count = judgeResultMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private void excludeDegradedCallPlatforms(LambdaQueryWrapper<PresaleAiCall> wrapper,
                                              Set<String> degradedPlatforms) {
        if (degradedPlatforms != null && !degradedPlatforms.isEmpty()) {
            wrapper.notIn(PresaleAiCall::getPlatformCode, degradedPlatforms);
        }
    }

    private void excludeDegradedJudgePlatforms(LambdaQueryWrapper<PresaleAiPromptJudgeResult> wrapper,
                                               Set<String> degradedPlatforms) {
        if (degradedPlatforms != null && !degradedPlatforms.isEmpty()) {
            wrapper.notIn(PresaleAiPromptJudgeResult::getPlatformCode, degradedPlatforms);
        }
    }

    private List<SamplePrompt> buildSamplePrompts(Long versionId) {
        List<PresaleReportVersionPromptTemplate> templates = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
                        .in(PresaleReportVersionPromptTemplate::getCategory, PAGE03_SAMPLE_CATEGORIES)
                        .eq(PresaleReportVersionPromptTemplate::getHasCompetitorVar, 0)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getSortOrderInVersion)
                        .orderByAsc(PresaleReportVersionPromptTemplate::getId)
        );
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<SamplePrompt> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String category : PAGE03_SAMPLE_CATEGORIES) {
            templates.stream()
                    .filter(item -> category.equals(item.getCategory()))
                    .map(PresaleReportVersionPromptTemplate::getPromptContent)
                    .map(this::normalizeSamplePrompt)
                    .filter(text -> text != null && seen.add(text))
                    .findFirst()
                    .ifPresent(text -> out.add(SamplePrompt.builder()
                            .category(category)
                            .promptContent(text)
                            .build()));
        }
        if (out.size() >= 3) {
            return out;
        }
        for (PresaleReportVersionPromptTemplate template : templates) {
            String text = normalizeSamplePrompt(template.getPromptContent());
            if (text == null || !seen.add(text)) {
                continue;
            }
            out.add(SamplePrompt.builder()
                    .category(template.getCategory())
                    .promptContent(text)
                    .build());
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
    }

    private int platformWeight(String platformCode) {
        return PLATFORM_DOUBAO.equalsIgnoreCase(platformCode) ? DOUBAO_WEIGHT : 1;
    }

    private List<String> parseSpecifiedCompetitors(PresaleReport report) {
        if (report == null || report.getSpecifiedCompetitors() == null || report.getSpecifiedCompetitors().isBlank()) {
            return List.of();
        }
        return parseJsonStringArray(report.getSpecifiedCompetitors(), "specified_competitors", report.getId());
    }

    private List<String> parseJsonStringArray(String json, String fieldName, Long reportId) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    out.add(item.asText().trim());
                }
            }
            return out;
        } catch (Exception ex) {
            log.warn("Skip invalid {} json, reportId={}", fieldName, reportId, ex);
            return List.of();
        }
    }

    private String normalizeSamplePrompt(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty() || text.contains("{competitor}")) {
            return null;
        }
        return text;
    }

    private List<PlatformBreakdown> buildPlatformBreakdown(Long versionId,
                                                           Set<String> degradedPlatforms,
                                                           List<AiPlatformConfig> platforms) {
        Map<Long, String> categoryByTemplateId = loadVersionTemplateCategoryMap(versionId);
        Set<String> safeDegraded = degradedPlatforms == null ? Set.of() : degradedPlatforms;
        List<PlatformBreakdown> out = new ArrayList<>();
        for (AiPlatformConfig platform : platforms) {
            String platformCode = platform.getPlatformCode();
            if (safeDegraded.contains(platformCode)) {
                continue;
            }
            List<PresaleAiPromptResult> batch1Rows = aiPromptResultMapper.selectList(
                    new LambdaQueryWrapper<PresaleAiPromptResult>()
                            .eq(PresaleAiPromptResult::getVersionId, versionId)
                            .eq(PresaleAiPromptResult::getEffectiveSample, true)
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
                        return isSampleIntentCategory(category)
                                && PresaleSampleInclusion.isIncluded(
                                        row.getIsMentioned() == null ? "FAILED" : STATUS_SUCCESS,
                                        0,
                                        safeDegraded.contains(platformCode)
                                );
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
                    .isDegraded(false)
                    .build());
        }
        return out;
    }

    private List<AiPlatformConfig> listEnabledPlatforms() {
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(PresalePlatformConfigQueries.presaleEnabledWrapper());
        return platforms == null ? List.of() : platforms;
    }

    private List<AiPlatformConfig> normalizeReportPlatforms(List<AiPlatformConfig> reportPlatforms) {
        List<AiPlatformConfig> source = reportPlatforms == null ? listEnabledPlatforms() : reportPlatforms;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Map<String, AiPlatformConfig> byCode = new LinkedHashMap<>();
        for (AiPlatformConfig platform : source) {
            if (platform == null || platform.getPlatformCode() == null
                    || platform.getPlatformCode().isBlank()) {
                continue;
            }
            byCode.putIfAbsent(platform.getPlatformCode().trim(), platform);
        }
        return List.copyOf(byCode.values());
    }

    private Map<Long, String> loadVersionTemplateCategoryMap(Long versionId) {
        List<PresaleReportVersionPromptTemplate> templates = versionPromptTemplateMapper.selectList(
                new LambdaQueryWrapper<PresaleReportVersionPromptTemplate>()
                        .eq(PresaleReportVersionPromptTemplate::getReportVersionId, versionId)
        );
        if (templates == null || templates.isEmpty()) {
            return Map.of();
        }
        return templates.stream().collect(Collectors.toMap(
                PresaleReportVersionPromptTemplate::getId,
                PresaleReportVersionPromptTemplate::getCategory,
                (left, right) -> left
        ));
    }

    private boolean isSampleIntentCategory(String category) {
        return category != null
                && !CATEGORY_COGNITIVE.equals(category)
                && !CATEGORY_COMPARISON.equals(category);
    }

    private List<Competitor> buildCompetitors(Long versionId,
                                              PresaleReport report,
                                              List<PresaleCompetitorAggregator.ExtractedCompetitor> extractedCompetitors) {
        if (extractedCompetitors == null || extractedCompetitors.isEmpty()) {
            return List.of();
        }
        PresaleCompetitorAggregator.Batch1MentionStats stats =
                competitorAggregator.aggregateBatch1MentionStats(versionId, selfBrandNames(report));

        int rank = 1;
        List<Competitor> out = new ArrayList<>();
        for (PresaleCompetitorAggregator.ExtractedCompetitor competitor : extractedCompetitors) {
            String competitorDisplayName = competitor.name();
            int mentionCount = competitor.mentionCount();
            double mentionRate = stats.denominatorRows() == 0 ? 0.0
                    : (mentionCount * 100.0 / stats.denominatorRows());
            ComparisonVerdictStats comparisonStats = buildComparisonVerdictStats(versionId, competitorDisplayName);
            List<String> comparisonAdvantages = aggregateCompetitorSceneAdvantages(versionId, competitorDisplayName);
            out.add(Competitor.builder()
                    .rank(rank++)
                    .name(competitorDisplayName)
                    .mentionCount(mentionCount)
                    .mentionRate(mentionRate)
                    .avgRanking(null)
                    .sceneAdvantagesRaw(comparisonAdvantages)
                    .comparisonVerdictCount(comparisonStats.comparisonVerdictCount())
                    .targetPreferredCount(comparisonStats.targetPreferredCount())
                    .competitorPreferredCount(comparisonStats.competitorPreferredCount())
                    .tieCount(comparisonStats.tieCount())
                    .unclearCount(comparisonStats.unclearCount())
                    .targetPreferredRate(comparisonStats.targetPreferredRate())
                    .competitorPreferredRate(comparisonStats.competitorPreferredRate())
                    .comparisonAdvantages(comparisonAdvantages)
                    .build());
        }
        return out;
    }

    private ComparisonVerdictStats buildComparisonVerdictStats(Long versionId, String competitorDisplayName) {
        if (competitorDisplayName == null || competitorDisplayName.isBlank()) {
            return ComparisonVerdictStats.empty();
        }
        List<PresaleAiPromptJudgeResult> judgeRows = judgeResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                        .eq(PresaleAiPromptJudgeResult::getVersionId, versionId)
                        .eq(PresaleAiPromptJudgeResult::getBatchNo, 2)
                        .eq(PresaleAiPromptJudgeResult::getCategory, "COMPARISON")
                        .eq(PresaleAiPromptJudgeResult::getJudgeStatus, STATUS_SUCCESS)
                        .inSql(PresaleAiPromptJudgeResult::getPromptResultId,
                                effectivePromptResultIdSql(versionId))
        );
        int target = 0;
        int competitor = 0;
        int tie = 0;
        int unclear = 0;
        for (PresaleAiPromptJudgeResult row : judgeRows == null ? List.<PresaleAiPromptJudgeResult>of() : judgeRows) {
            if (competitorAggregator.matchCompetitorDisplayName(row.getCompetitorName(), List.of(competitorDisplayName)).isEmpty()) {
                continue;
            }
            String preferredBrand = row.getPreferredBrand();
            if (JUDGE_PREFERRED_TARGET.equals(preferredBrand)) {
                target++;
            } else if (JUDGE_PREFERRED_COMPETITOR.equals(preferredBrand)) {
                competitor++;
            } else if (JUDGE_PREFERRED_TIE.equals(preferredBrand)) {
                tie++;
            } else if (JUDGE_PREFERRED_UNCLEAR.equals(preferredBrand)) {
                unclear++;
            }
        }
        return ComparisonVerdictStats.of(target, competitor, tie, unclear);
    }

    private List<PresaleCompetitorAggregator.ExtractedCompetitor> buildLegacyExtractedCompetitors(
            Long versionId,
            PresaleReport report,
            List<String> extractedCompetitorDisplayNames) {
        if (extractedCompetitorDisplayNames == null || extractedCompetitorDisplayNames.isEmpty()) {
            return List.of();
        }
        PresaleCompetitorAggregator.Batch1MentionStats stats =
                competitorAggregator.aggregateBatch1MentionStats(versionId, selfBrandNames(report));
        List<PresaleCompetitorAggregator.ExtractedCompetitor> out = new ArrayList<>();
        for (String competitorDisplayName : extractedCompetitorDisplayNames) {
            String normalized = competitorAggregator.normalizeName(competitorDisplayName);
            int mentionCount = stats.countByNormalized().getOrDefault(normalized, 0);
            out.add(new PresaleCompetitorAggregator.ExtractedCompetitor(
                    competitorDisplayName, mentionCount, List.of(competitorDisplayName)));
        }
        return out;
    }

    private List<String> aggregateGroupSceneAdvantages(Long versionId, List<String> extractedCompetitorDisplayNames) {
        String groupName = CompetitorGroupKeyUtils.storageKey(extractedCompetitorDisplayNames);
        if (groupName.isBlank() || !groupName.contains(CompetitorGroupKeyUtils.SEPARATOR)) {
            return List.of();
        }
        return aggregateSceneAdvantages(versionId, groupName);
    }

    private List<String> selfBrandNames(PresaleReport report) {
        if (report == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (report.getBrandName() != null && !report.getBrandName().isBlank()) {
            out.add(report.getBrandName().trim());
        }
        out.addAll(parseBrandFormerNames(report));
        out.addAll(parseRepresentedBrands(report));
        return out;
    }

    private List<String> parseBrandFormerNames(PresaleReport report) {
        return report == null
                ? List.of()
                : parseJsonStringArray(report.getBrandFormerNames(), "brand_former_names", report.getId());
    }

    private List<String> parseRepresentedBrands(PresaleReport report) {
        return report == null
                ? List.of()
                : parseJsonStringArray(report.getRepresentedBrands(), "represented_brands", report.getId());
    }

    private List<String> aggregateCompetitorSceneAdvantages(Long versionId, String competitorDisplayName) {
        if (competitorDisplayName == null || competitorDisplayName.isBlank()) {
            return List.of();
        }
        List<PresaleAiPromptJudgeResult> judgeRows = judgeResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptJudgeResult>()
                        .eq(PresaleAiPromptJudgeResult::getVersionId, versionId)
                        .eq(PresaleAiPromptJudgeResult::getBatchNo, 2)
                        .eq(PresaleAiPromptJudgeResult::getCategory, "COMPARISON")
                        .eq(PresaleAiPromptJudgeResult::getJudgeStatus, "SUCCESS")
                        .inSql(PresaleAiPromptJudgeResult::getPromptResultId,
                                effectivePromptResultIdSql(versionId))
        );
        Map<String, Integer> freq = new HashMap<>();
        for (PresaleAiPromptJudgeResult row : judgeRows == null ? List.<PresaleAiPromptJudgeResult>of() : judgeRows) {
            if (competitorAggregator.matchCompetitorDisplayName(row.getCompetitorName(), List.of(competitorDisplayName)).isEmpty()) {
                continue;
            }
            String advantages = row.getCompetitorAdvantages();
            if (advantages == null || advantages.isBlank()) {
                continue;
            }
            try {
                JsonNode arr = objectMapper.readTree(advantages);
                if (!arr.isArray()) {
                    continue;
                }
                for (JsonNode item : arr) {
                    if (!item.isTextual()) {
                        continue;
                    }
                    String scene = item.asText().trim();
                    if (!scene.isEmpty()) {
                        freq.merge(scene, 1, Integer::sum);
                    }
                }
            } catch (Exception ex) {
                log.warn("Skip invalid competitor_advantages, versionId={}, judgeRowId={}", versionId, row.getId(), ex);
            }
        }
        if (!freq.isEmpty()) {
            return topScenes(freq);
        }
        return aggregateSceneAdvantages(versionId, competitorDisplayName);
    }

    private List<String> aggregateSceneAdvantages(Long versionId, String competitorDisplayName) {
        List<PresaleAiPromptResult> batch2Rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .eq(PresaleAiPromptResult::getEffectiveSample, true)
                        .eq(PresaleAiPromptResult::getBatchNo, 2)
                        .isNotNull(PresaleAiPromptResult::getIsMentioned)
        );
        Map<String, Integer> freq = new HashMap<>();
        for (PresaleAiPromptResult row : batch2Rows) {
            if (competitorAggregator.matchCompetitorDisplayName(row.getCompetitorName(), List.of(competitorDisplayName)).isEmpty()) {
                continue;
            }
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
        return topScenes(freq);
    }

    private List<String> topScenes(Map<String, Integer> freq) {
        return freq.entrySet().stream()
                .sorted(Comparator
                        .comparing(Map.Entry<String, Integer>::getValue, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_SCENE_ADVANTAGES)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String effectivePromptResultIdSql(Long versionId) {
        if (versionId == null || versionId < 1) {
            throw new IllegalArgumentException("versionId must be positive");
        }
        return "SELECT id FROM presale_ai_prompt_result WHERE effective_sample = 1 AND version_id = "
                + versionId;
    }

    private SentimentDetail buildSentimentDetail(Long versionId,
                                                 List<AiPlatformConfig> reportPlatforms) {
        List<PresaleAiPromptResult> rows = aiPromptResultMapper.selectList(
                new LambdaQueryWrapper<PresaleAiPromptResult>()
                        .eq(PresaleAiPromptResult::getVersionId, versionId)
                        .eq(PresaleAiPromptResult::getEffectiveSample, true)
                        .eq(PresaleAiPromptResult::getIsMentioned, 1)
                        .isNotNull(PresaleAiPromptResult::getSentiment)
        );
        if (rows == null) {
            rows = new ArrayList<>();
        } else {
            rows = rows.stream()
                    .filter(row -> row != null && Integer.valueOf(1).equals(row.getIsMentioned()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        rows.sort(Comparator.comparing(PresaleAiPromptResult::getId, Comparator.nullsLast(Long::compareTo)));

        int positive = (int) rows.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
        int neutral = (int) rows.stream().filter(r -> "NEUTRAL".equals(r.getSentiment())).count();
        int negative = (int) rows.stream().filter(r -> "NEGATIVE".equals(r.getSentiment())).count();

        List<SentimentDetail.SentimentKeyword> topKeywords = aggregateTopKeywords(rows);
        List<SentimentDetail.NegativeEvidence> negativeEvidence = aggregateNegativeEvidence(
                rows, reportPlatforms);

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

    private List<SentimentDetail.NegativeEvidence> aggregateNegativeEvidence(
            List<PresaleAiPromptResult> rows,
            List<AiPlatformConfig> reportPlatforms) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiPlatformConfig> platformRows = reportPlatforms == null ? List.of() : reportPlatforms;
        Map<String, String> platformNameByCode = platformRows.stream()
                .filter(p -> p.getPlatformCode() != null)
                .collect(Collectors.toMap(AiPlatformConfig::getPlatformCode,
                        p -> p.getPlatformName() == null ? p.getPlatformCode() : p.getPlatformName(),
                        (a, b) -> a));

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
            SentimentDetail.Sentiment rowSentiment = parseSentimentEnum(row.getSentiment());
            if (rowSentiment != SentimentDetail.Sentiment.NEGATIVE) {
                continue;
            }
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
                String query = (row.getRequestPromptContent() == null || row.getRequestPromptContent().isBlank())
                        ? "—"
                        : row.getRequestPromptContent();
                Long analyzeCallId = row.getAnalyzeCallId();
                LocalDateTime testedAt = analyzeCallId == null ? null : testedAtByAnalyzeCallId.get(analyzeCallId);
                if (testedAt == null) {
                    testedAt = row.getCreatedAt();
                }
                result.add(SentimentDetail.NegativeEvidence.builder()
                        .sentiment(SentimentDetail.Sentiment.NEGATIVE)
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

    private record ComparisonVerdictStats(int targetPreferredCount,
                                          int competitorPreferredCount,
                                          int tieCount,
                                          int unclearCount,
                                          int comparisonVerdictCount,
                                          double targetPreferredRate,
                                          double competitorPreferredRate) {
        static ComparisonVerdictStats empty() {
            return of(0, 0, 0, 0);
        }

        static ComparisonVerdictStats of(int targetPreferredCount,
                                         int competitorPreferredCount,
                                         int tieCount,
                                         int unclearCount) {
            int verdictCount = targetPreferredCount + competitorPreferredCount + tieCount + unclearCount;
            int rateDenominator = targetPreferredCount + competitorPreferredCount + tieCount;
            double targetRate = rateDenominator == 0 ? 0.0 : targetPreferredCount * 100.0 / rateDenominator;
            double competitorRate = rateDenominator == 0 ? 0.0 : competitorPreferredCount * 100.0 / rateDenominator;
            return new ComparisonVerdictStats(
                    targetPreferredCount,
                    competitorPreferredCount,
                    tieCount,
                    unclearCount,
                    verdictCount,
                    targetRate,
                    competitorRate
            );
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
