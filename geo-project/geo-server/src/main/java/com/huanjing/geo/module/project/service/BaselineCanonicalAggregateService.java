package com.huanjing.geo.module.project.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.project.dto.BaselineCanonicalReportVO;
import com.huanjing.geo.module.project.entity.BaselineCompetitorMention;
import com.huanjing.geo.module.project.entity.BaselineCompetitorSource;
import com.huanjing.geo.module.project.entity.BaselineHighlightSpan;
import com.huanjing.geo.module.project.entity.BaselineMetricSnapshot;
import com.huanjing.geo.module.project.entity.BaselineObservation;
import com.huanjing.geo.module.project.entity.BaselineObservationScore;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.BaselineSnapshot;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorMentionMapper;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorSourceMapper;
import com.huanjing.geo.module.project.mapper.BaselineHighlightSpanMapper;
import com.huanjing.geo.module.project.mapper.BaselineMetricSnapshotMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationScoreMapper;
import com.huanjing.geo.module.project.mapper.BaselineQuestionSnapshotMapper;
import com.huanjing.geo.module.project.mapper.BaselineSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaselineCanonicalAggregateService {
    private static final int EXPECTED_SAMPLES = 3;
    private static final String SCHEMA_VERSION = BaselineCanonicalVersionPolicy.SCHEMA_VERSION;
    private static final String SCORE_ALGORITHM_VERSION = BaselineCanonicalVersionPolicy.SCORE_ALGORITHM_VERSION;
    private static final String HIGHLIGHT_ALGORITHM_VERSION = BaselineCanonicalVersionPolicy.HIGHLIGHT_ALGORITHM_VERSION;
    private static final String COMPETITOR_NORMALIZATION_VERSION = BaselineCanonicalVersionPolicy.COMPETITOR_NORMALIZATION_VERSION;
    private static final String CANONICAL_AGGREGATE_VERSION = BaselineCanonicalVersionPolicy.CANONICAL_AGGREGATE_VERSION;
    private static final List<String> POSITIVE_KEYWORDS = List.of("推荐", "靠谱", "优秀", "领先", "专业", "好评", "值得", "首选");
    private static final List<String> HIGH_SEVERITY_NEGATIVE_KEYWORDS = List.of("不推荐", "投诉", "风险", "不靠谱", "谨慎", "骗子", "欺诈", "违规");
    private static final List<String> MID_SEVERITY_NEGATIVE_KEYWORDS = List.of("差评", "负面", "价格高", "较高", "售后差", "不规范");
    private static final int EXCERPT_LENGTH = 240;
    private static final int LOW_SAMPLE_HEATMAP_THRESHOLD = 3;

    private final CurrentUserService currentUserService;
    private final CompanyMapper companyMapper;
    private final ProjectMapper projectMapper;
    private final BaselineSnapshotMapper baselineSnapshotMapper;
    private final BaselineQuestionSnapshotMapper baselineQuestionSnapshotMapper;
    private final BaselineObservationMapper baselineObservationMapper;
    private final BaselineObservationScoreMapper baselineObservationScoreMapper;
    private final BaselineCompetitorMentionMapper baselineCompetitorMentionMapper;
    private final BaselineCompetitorSourceMapper baselineCompetitorSourceMapper;
    private final BaselineHighlightSpanMapper baselineHighlightSpanMapper;
    private final BaselineMetricSnapshotMapper baselineMetricSnapshotMapper;

    @Transactional
    public BaselineCanonicalReportVO recompute(Long projectId, Long baselineId) {
        Project project = requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadSealedSnapshot(projectId, baselineId);
        CanonicalBuildResult result = buildCanonical(project, snapshot);
        BaselineMetricSnapshot existing = baselineMetricSnapshotMapper.selectOne(new LambdaQueryWrapper<BaselineMetricSnapshot>()
                .eq(BaselineMetricSnapshot::getBaselineId, baselineId)
                .eq(BaselineMetricSnapshot::getScoreAlgorithmVersion, SCORE_ALGORITHM_VERSION)
                .eq(BaselineMetricSnapshot::getHighlightAlgorithmVersion, HIGHLIGHT_ALGORITHM_VERSION)
                .eq(BaselineMetricSnapshot::getCompetitorNormalizationVersion, COMPETITOR_NORMALIZATION_VERSION)
                .eq(BaselineMetricSnapshot::getCanonicalAggregateVersion, CANONICAL_AGGREGATE_VERSION)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        BaselineMetricSnapshot metric = existing == null ? new BaselineMetricSnapshot() : existing;
        metric.setBaselineId(baselineId);
        metric.setCanonicalSchemaVersion(SCHEMA_VERSION);
        metric.setScoreAlgorithmVersion(SCORE_ALGORITHM_VERSION);
        metric.setHighlightAlgorithmVersion(HIGHLIGHT_ALGORITHM_VERSION);
        metric.setCompetitorNormalizationVersion(COMPETITOR_NORMALIZATION_VERSION);
        metric.setCanonicalAggregateVersion(CANONICAL_AGGREGATE_VERSION);
        metric.setCanonicalJson(result.canonicalJson());
        metric.setGeneratedAt(now);
        if (metric.getId() == null) {
            metric.setCreatedAt(now);
            baselineMetricSnapshotMapper.insert(metric);
        } else {
            baselineMetricSnapshotMapper.updateById(metric);
        }
        return toVO(metric);
    }

    public BaselineCanonicalReportVO latest(Long projectId, Long baselineId) {
        requireReadableActiveProject(projectId);
        BaselineSnapshot snapshot = loadSealedSnapshot(projectId, baselineId);
        BaselineMetricSnapshot metric = baselineMetricSnapshotMapper.selectOne(new LambdaQueryWrapper<BaselineMetricSnapshot>()
                .eq(BaselineMetricSnapshot::getBaselineId, snapshot.getId())
                .orderByDesc(BaselineMetricSnapshot::getGeneratedAt, BaselineMetricSnapshot::getId)
                .last("LIMIT 1"));
        if (metric == null) {
            throw new BizException(404, "Baseline canonical snapshot not found");
        }
        return toVO(metric);
    }

    CanonicalBuildResult buildCanonical(Project project, BaselineSnapshot snapshot) {
        List<BaselineQuestionSnapshot> questions = baselineQuestionSnapshotMapper.selectList(
                new LambdaQueryWrapper<BaselineQuestionSnapshot>()
                        .eq(BaselineQuestionSnapshot::getBaselineId, snapshot.getId())
                        .orderByAsc(BaselineQuestionSnapshot::getSortOrder, BaselineQuestionSnapshot::getId));
        List<BaselineObservation> observations = baselineObservationMapper.selectList(
                new LambdaQueryWrapper<BaselineObservation>()
                        .eq(BaselineObservation::getBaselineId, snapshot.getId()));
        if (observations.isEmpty()) {
            throw new BizException(400, "当前基线快照尚未完成观测采集");
        }
        Map<Long, BaselineObservationScore> scoreByObservationId = baselineObservationScoreMapper.selectList(
                        new LambdaQueryWrapper<BaselineObservationScore>()
                                .eq(BaselineObservationScore::getBaselineId, snapshot.getId())
                                .eq(BaselineObservationScore::getAlgorithmVersion, SCORE_ALGORITHM_VERSION))
                .stream()
                .collect(Collectors.toMap(BaselineObservationScore::getObservationId, Function.identity(), (first, ignored) -> first));
        List<BaselineCompetitorMention> competitorMentions = baselineCompetitorMentionMapper.selectList(
                new LambdaQueryWrapper<BaselineCompetitorMention>()
                        .eq(BaselineCompetitorMention::getBaselineId, snapshot.getId())
                        .eq(BaselineCompetitorMention::getAlgorithmVersion, COMPETITOR_NORMALIZATION_VERSION));
        List<BaselineCompetitorSource> competitorSources = baselineCompetitorSourceMapper.selectList(
                new LambdaQueryWrapper<BaselineCompetitorSource>()
                        .eq(BaselineCompetitorSource::getBaselineId, snapshot.getId()));
        List<BaselineHighlightSpan> highlights = baselineHighlightSpanMapper.selectList(
                new LambdaQueryWrapper<BaselineHighlightSpan>()
                        .eq(BaselineHighlightSpan::getAlgorithmVersion, HIGHLIGHT_ALGORITHM_VERSION));

        Map<Long, List<BaselineObservation>> observationsByQuestion = observations.stream()
                .collect(Collectors.groupingBy(BaselineObservation::getQuestionSnapshotId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, BaselineObservation> observationById = observations.stream()
                .collect(Collectors.toMap(BaselineObservation::getId, Function.identity(), (first, ignored) -> first));
        Map<Long, BaselineQuestionSnapshot> questionById = questions.stream()
                .collect(Collectors.toMap(BaselineQuestionSnapshot::getId, Function.identity(), (first, ignored) -> first));
        Set<Long> observationIds = observationById.keySet();
        highlights = highlights.stream()
                .filter(highlight -> observationIds.contains(highlight.getObservationId()))
                .toList();
        Set<Long> negativeObservationIds = highlights.stream()
                .filter(highlight -> "NEGATIVE".equals(highlight.getType()))
                .filter(this::isTrustedNegativeHighlight)
                .map(BaselineHighlightSpan::getObservationId)
                .collect(Collectors.toSet());
        List<Map<String, Object>> cellRows = new ArrayList<>();
        Map<String, TierStats> valueTierStats = new LinkedHashMap<>();
        Map<String, HeatmapStats> heatmapStats = new LinkedHashMap<>();
        int rateDenominator = 0;
        int coveredCellCount = 0;
        int recommendedCellCount = 0;
        int stableCellCount = 0;
        int brandMentionCount = 0;
        int evaluableSentimentCount = 0;
        Map<String, Integer> sentimentBuckets = new LinkedHashMap<>();
        sentimentBuckets.put("POSITIVE", 0);
        sentimentBuckets.put("NEUTRAL", 0);
        sentimentBuckets.put("NEGATIVE", 0);
        int unknownSentimentCount = 0;
        Map<Long, Map<String, Boolean>> questionCoverageByPlatform = new LinkedHashMap<>();
        Map<String, PlatformStats> platformStats = new LinkedHashMap<>();

        for (BaselineQuestionSnapshot question : questions) {
            Map<String, List<BaselineObservation>> byPlatform = observationsByQuestion
                    .getOrDefault(question.getId(), List.of())
                    .stream()
                    .collect(Collectors.groupingBy(BaselineObservation::getPlatformCode, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<BaselineObservation>> entry : byPlatform.entrySet()) {
                CellStats cell = computeCell(entry.getValue(), scoreByObservationId, negativeObservationIds);
                String cellState = BaselineReportSnapshotRules.resolveCellState(EXPECTED_SAMPLES, cell.successSamples(), cell.positiveSamples());
                boolean includeInDenominator = BaselineCanonicalAggregateRules.includeInRateDenominator(EXPECTED_SAMPLES, cell.successSamples());
                boolean covered = BaselineCanonicalAggregateRules.isCovered(EXPECTED_SAMPLES, cell.successSamples(), cell.positiveSamples());
                String metricKind = BaselineCanonicalAggregateRules.metricKindForIntent(question.getIntentType());
                boolean metricPositive = BaselineCanonicalAggregateRules.isMetricPositive(EXPECTED_SAMPLES,
                        cell.successSamples(), cell.positiveSamples(), cell.awarenessSamples(), cell.favorableSamples(), metricKind);
                if (includeInDenominator) {
                    rateDenominator++;
                    if (BaselineReportSnapshotRules.CELL_STATE_STABLE_PRESENT.equals(cellState)
                            || BaselineReportSnapshotRules.CELL_STATE_STABLE_ABSENT.equals(cellState)) {
                        stableCellCount++;
                    }
                    TierStats tierStats = valueTierStats.computeIfAbsent(question.getValueTier(), ignored -> new TierStats());
                    tierStats.denominator++;
                    HeatmapStats heatmap = heatmapStats.computeIfAbsent(question.getIntentType() + ":" + entry.getKey(),
                            ignored -> new HeatmapStats(question.getIntentType(), entry.getKey(), metricKind));
                    heatmap.denominator++;
                    if (metricPositive) {
                        heatmap.positive++;
                    }
                    PlatformStats stats = platformStats.computeIfAbsent(entry.getKey(), ignored -> new PlatformStats());
                    stats.denominator++;
                    if (covered) {
                        stats.appeared++;
                    }
                    if (cell.recommendedSamples() > 0) {
                        stats.recommended++;
                    }
                    stats.rankingSum += cell.rankingSum();
                    stats.rankingCount += cell.rankingCount();
                    stats.positiveSentiment += cell.sentimentBuckets().getOrDefault("POSITIVE", 0);
                    stats.evaluableSentiment += cell.evaluableSentimentCount();
                }
                if (covered) {
                    coveredCellCount++;
                    valueTierStats.computeIfAbsent(question.getValueTier(), ignored -> new TierStats()).appeared++;
                }
                if (cell.recommendedSamples() > 0 && includeInDenominator) {
                    recommendedCellCount++;
                    valueTierStats.computeIfAbsent(question.getValueTier(), ignored -> new TierStats()).recommended++;
                }
                brandMentionCount += cell.brandMentionCount();
                evaluableSentimentCount += cell.evaluableSentimentCount();
                unknownSentimentCount += cell.unknownSentimentCount();
                for (Map.Entry<String, Integer> sentiment : cell.sentimentBuckets().entrySet()) {
                    sentimentBuckets.computeIfPresent(sentiment.getKey(), (ignored, value) -> value + sentiment.getValue());
                }
                questionCoverageByPlatform
                        .computeIfAbsent(question.getId(), ignored -> new LinkedHashMap<>())
                        .put(entry.getKey(), covered);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("question_id", question.getQuestionKey());
                row.put("question_snapshot_id", question.getId());
                row.put("platform_code", entry.getKey());
                row.put("cell_state", cellState);
                row.put("covered", covered);
                row.put("expected_samples", EXPECTED_SAMPLES);
                row.put("success_samples", cell.successSamples());
                row.put("failed_samples", cell.failedSamples());
                row.put("positive_samples", cell.positiveSamples());
                row.put("rate_denominator_eligible", includeInDenominator);
                cellRows.add(row);
            }
        }

        Map<String, BaselineCompetitorSource> configuredSources = configuredSourceLookup(competitorSources);
        int trackedCompetitorMentionCount = competitorMentions.stream()
                .filter(mention -> configuredSourceForMention(mention, configuredSources) != null)
                .mapToInt(mention -> mention.getMentionCount() == null ? 1 : mention.getMentionCount())
                .sum();
        int negativeSentimentCount = sentimentBuckets.getOrDefault("NEGATIVE", 0);
        Map<String, Object> competitors = buildCompetitors(competitorMentions, competitorSources, observationById);
        List<Map<String, Object>> gapMatrix = buildGapMatrix(questions, observationById, competitorMentions,
                competitorSources, questionCoverageByPlatform);
        Map<String, Object> coverage = buildCoverage(coveredCellCount, rateDenominator, recommendedCellCount,
                stableCellCount, sentimentBuckets.getOrDefault("POSITIVE", 0), evaluableSentimentCount,
                valueTierStats, heatmapStats, platformStats);
        Map<String, Object> sentiment = buildSentiment(sentimentBuckets, brandMentionCount, evaluableSentimentCount,
                unknownSentimentCount, observations, scoreByObservationId, negativeObservationIds);
        List<Map<String, Object>> evidenceCards = buildEvidenceCards(observations, scoreByObservationId, highlights, questionById);
        List<Map<String, Object>> keyFindings = buildKeyFindings(coveredCellCount, rateDenominator, brandMentionCount,
                recommendedCellCount, trackedCompetitorMentionCount, negativeSentimentCount);
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("meta", buildMeta(snapshot));
        canonical.put("cell_state_enum", List.of(
                BaselineReportSnapshotRules.CELL_STATE_STABLE_PRESENT,
                BaselineReportSnapshotRules.CELL_STATE_STABLE_ABSENT,
                BaselineReportSnapshotRules.CELL_STATE_UNSTABLE_PARTIAL,
                BaselineReportSnapshotRules.CELL_STATE_INSUFFICIENT_SAMPLE,
                BaselineReportSnapshotRules.CELL_STATE_NO_DATA
        ));
        canonical.put("coverage", coverage);
        canonical.put("hero_metrics", buildHeroMetrics(coveredCellCount, rateDenominator, brandMentionCount,
                trackedCompetitorMentionCount, negativeSentimentCount));
        canonical.put("sentiment", sentiment);
        canonical.put("cells", cellRows);
        canonical.put("competitors", competitors);
        canonical.put("competitor_counts", competitors.get("counts"));
        canonical.put("competitor_gap_matrix", gapMatrix);
        canonical.put("evidence_cards", evidenceCards);
        canonical.put("key_findings", keyFindings);
        canonical.put("delta_placeholders", buildDeltaPlaceholders());
        Map<String, Object> brand = new LinkedHashMap<>();
        brand.put("id", project.getBrandId());
        brand.put("name", project.getBrandName() == null ? "" : project.getBrandName());
        canonical.put("brand", brand);
        return new CanonicalBuildResult(JSONUtil.toJsonStr(canonical), canonical);
    }

    private Map<String, Object> buildMeta(BaselineSnapshot snapshot) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schema_version", SCHEMA_VERSION);
        meta.put("baseline_id", snapshot.getId());
        meta.put("selected_versions", BaselineCanonicalVersionPolicy.selectedVersions());
        meta.put("algorithm_versions", BaselineCanonicalVersionPolicy.expandAlgorithmVersions());
        return meta;
    }

    private CellStats computeCell(List<BaselineObservation> observations,
                                  Map<Long, BaselineObservationScore> scoreByObservationId,
                                  Set<Long> negativeObservationIds) {
        int success = 0;
        int failed = 0;
        int positive = 0;
        int recommended = 0;
        int awareness = 0;
        int favorable = 0;
        int brandMentions = 0;
        int evaluableSentiments = 0;
        int unknownSentiments = 0;
        int rankingSum = 0;
        int rankingCount = 0;
        Map<String, Integer> sentimentBuckets = new LinkedHashMap<>();
        sentimentBuckets.put("POSITIVE", 0);
        sentimentBuckets.put("NEUTRAL", 0);
        sentimentBuckets.put("NEGATIVE", 0);
        for (BaselineObservation observation : observations) {
            if (!"SUCCESS".equals(observation.getCallStatus())) {
                failed++;
                continue;
            }
            success++;
            BaselineObservationScore score = scoreByObservationId.get(observation.getId());
            if (score == null) {
                continue;
            }
            if (hasSubstantiveAwareness(score.getImpressionState())) {
                awareness++;
            }
            if (Boolean.TRUE.equals(score.getMentioned())) {
                positive++;
                brandMentions++;
                if (Boolean.TRUE.equals(score.getRecommended())) {
                    recommended++;
                }
                if (Boolean.TRUE.equals(score.getRecommended())
                        || "POSITIVE".equals(score.getSentiment())
                        || (score.getRankingPosition() != null && score.getRankingPosition() == 1)) {
                    favorable++;
                }
                if (score.getRankingPosition() != null) {
                    rankingSum += score.getRankingPosition();
                    rankingCount++;
                }
                String displaySentiment = displaySentiment(score, observation.getId(), negativeObservationIds);
                if ("UNKNOWN".equals(displaySentiment)) {
                    unknownSentiments++;
                } else if (sentimentBuckets.containsKey(displaySentiment)) {
                    evaluableSentiments++;
                    sentimentBuckets.computeIfPresent(displaySentiment, (ignored, value) -> value + 1);
                }
                continue;
            }
        }
        return new CellStats(success, failed, positive, recommended, awareness, favorable, brandMentions, evaluableSentiments,
                unknownSentiments, rankingSum, rankingCount, sentimentBuckets);
    }

    private String displaySentiment(BaselineObservationScore score, Long observationId, Set<Long> negativeObservationIds) {
        if (score == null || !StringUtils.hasText(score.getSentiment())) {
            return "UNKNOWN";
        }
        if (!"NEGATIVE".equals(score.getSentiment())) {
            return score.getSentiment();
        }
        return negativeObservationIds.contains(observationId) ? "NEGATIVE" : "NEUTRAL";
    }

    private boolean hasSubstantiveAwareness(String impressionState) {
        return impressionState != null
                && !"NO_AWARENESS".equals(impressionState)
                && !"INFO_MISSING".equals(impressionState);
    }

    private Map<String, Object> buildCompetitors(List<BaselineCompetitorMention> mentions,
                                                 List<BaselineCompetitorSource> sources,
                                                 Map<Long, BaselineObservation> observationById) {
        Map<String, BaselineCompetitorSource> sourceByName = configuredSourceLookup(sources);
        Map<String, Integer> trackedCounts = new LinkedHashMap<>();
        Map<String, Integer> untrackedCounts = new LinkedHashMap<>();
        for (BaselineCompetitorMention mention : mentions) {
            String name = mention.getNormalizedName();
            if (name == null || name.isBlank()) {
                continue;
            }
            BaselineCompetitorSource source = configuredSourceForMention(mention, sourceByName);
            if (source != null) {
                trackedCounts.merge(normalizeName(source.getCompetitorName()),
                        mention.getMentionCount() == null ? 1 : mention.getMentionCount(), Integer::sum);
            } else {
                untrackedCounts.merge(normalizeName(name), mention.getMentionCount() == null ? 1 : mention.getMentionCount(), Integer::sum);
            }
        }
        List<BaselineCompetitorSource> configuredSources = sources.stream()
                .filter(this::isConfiguredCompetitorSource)
                .toList();
        List<Map<String, Object>> tracked = configuredSources.stream()
                .map(source -> {
                    String canonicalName = normalizeName(source.getCompetitorName());
                    int mentionCount = trackedCounts.getOrDefault(canonicalName, 0);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", canonicalName);
                    row.put("mention_count", mentionCount);
                    row.put("review_status", source.getReviewStatus());
                    row.put("verified_sources", verifiedSources(source));
                    row.put("render_source_explanation", source != null && "VERIFIED".equals(source.getReviewStatus()));
                    row.put("quotes", competitorQuotes(canonicalName, mentions, observationById, sourceByName));
                    row.put("source_explanation", source != null && "VERIFIED".equals(source.getReviewStatus())
                            ? (source.getSourceNote() == null || source.getSourceNote().isBlank() ? source.getSourceUrl() : source.getSourceNote())
                            : null);
                    return row;
                })
                .sorted((left, right) -> Integer.compare((Integer) right.get("mention_count"), (Integer) left.get("mention_count")))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tracked", tracked);
        result.put("untracked_mentions", untrackedCounts.entrySet().stream()
                .map(entry -> Map.of("name", entry.getKey(), "mention_count", entry.getValue()))
                .toList());
        result.put("verified_sources", sources.stream()
                .filter(source -> "VERIFIED".equals(source.getReviewStatus()))
                .map(this::sourceRow)
                .toList());
        result.put("counts", tracked.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("name")),
                        row -> (Integer) row.get("mention_count"),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                )));
        return result;
    }

    private List<Map<String, Object>> buildGapMatrix(List<BaselineQuestionSnapshot> questions,
                                                     Map<Long, BaselineObservation> observationById,
                                                     List<BaselineCompetitorMention> mentions,
                                                     List<BaselineCompetitorSource> sources,
                                                     Map<Long, Map<String, Boolean>> questionCoverageByPlatform) {
        Map<Long, Boolean> configuredCompetitorIds = sources.stream()
                .filter(this::isConfiguredCompetitorSource)
                .filter(source -> source.getCompetitorId() != null)
                .collect(Collectors.toMap(BaselineCompetitorSource::getCompetitorId, ignored -> true, (first, ignored) -> first));
        Map<String, BaselineCompetitorSource> sourceByName = configuredSourceLookup(sources);
        List<String> configuredCompetitorNames = sources.stream()
                .filter(this::isConfiguredCompetitorSource)
                .map(source -> normalizeName(source.getCompetitorName()))
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        Map<Long, List<BaselineCompetitorMention>> mentionsByQuestion = mentions.stream()
                .filter(mention -> observationById.containsKey(mention.getObservationId()))
                .filter(mention -> isConfiguredTrackedCompetitor(mention, configuredCompetitorIds, sourceByName))
                .collect(Collectors.groupingBy(mention -> observationById.get(mention.getObservationId()).getQuestionSnapshotId()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BaselineQuestionSnapshot question : questions) {
            if (!"HIGH".equals(question.getValueTier())
                    && !BaselineReportSnapshotRules.INTENT_RECOMMENDATION.equals(question.getIntentType())) {
                continue;
            }
            Map<String, Boolean> platformCoverage = questionCoverageByPlatform.getOrDefault(question.getId(), Map.of());
            boolean you = platformCoverage.values().stream().anyMatch(Boolean.TRUE::equals);
            Map<String, Boolean> competitors = new LinkedHashMap<>();
            configuredCompetitorNames.forEach(name -> competitors.put(name, false));
            for (BaselineCompetitorMention mention : mentionsByQuestion.getOrDefault(question.getId(), List.of())) {
                BaselineCompetitorSource source = configuredSourceForMention(mention, sourceByName);
                if (source != null) {
                    competitors.put(normalizeName(source.getCompetitorName()), true);
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question_id", question.getQuestionKey());
            row.put("question_snapshot_id", question.getId());
            row.put("question_text", question.getQuestionText());
            row.put("intent_type", question.getIntentType());
            row.put("value_tier", question.getValueTier());
            row.put("you", you);
            row.put("competitors", competitors);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> competitorQuotes(String competitorName,
                                                       List<BaselineCompetitorMention> mentions,
                                                       Map<Long, BaselineObservation> observationById,
                                                       Map<String, BaselineCompetitorSource> sourceByName) {
        return mentions.stream()
                .filter(mention -> {
                    BaselineCompetitorSource source = configuredSourceForMention(mention, sourceByName);
                    return source != null && normalizeName(source.getCompetitorName()).equals(competitorName);
                })
                .filter(mention -> observationById.containsKey(mention.getObservationId()))
                .limit(2)
                .map(mention -> {
                    BaselineObservation observation = observationById.get(mention.getObservationId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("observation_id", observation.getId());
                    row.put("platform_code", observation.getPlatformCode());
                    row.put("excerpt", cleanDisplayText(excerptWindow(observation.getRawResponseText(), mention.getStartOffset()).text()));
                    return row;
                })
                .toList();
    }

    private Map<String, Object> buildCoverage(int coveredCellCount,
                                              int rateDenominator,
                                              int recommendedCellCount,
                                              int stableCellCount,
                                              int positiveSentimentCount,
                                              int evaluableSentimentCount,
                                              Map<String, TierStats> valueTierStats,
                                              Map<String, HeatmapStats> heatmapStats,
                                              Map<String, PlatformStats> platformStats) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("metric_kind", BaselineCanonicalAggregateRules.METRIC_MENTION_RATE);
        coverage.put("covered_cell_count", coveredCellCount);
        coverage.put("denominator_cell_count", rateDenominator);
        coverage.put("rate", BaselineCanonicalAggregateRules.safeRate(coveredCellCount, rateDenominator));
        coverage.put("value_tiers", valueTierStats.entrySet().stream()
                .map(entry -> {
                    TierStats stats = entry.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("value_tier", entry.getKey());
                    row.put("appeared", stats.appeared);
                    row.put("recommended", stats.recommended);
                    row.put("denominator", stats.denominator);
                    row.put("appeared_rate", BaselineCanonicalAggregateRules.safeRate(stats.appeared, stats.denominator));
                    return row;
                })
                .toList());
        coverage.put("heatmap", heatmapStats.values().stream()
                .map(stats -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("intent_type", stats.intentType);
                    row.put("platform_code", stats.platformCode);
                    row.put("metric_kind", stats.metricKind);
                    row.put("n", stats.denominator);
                    row.put("positive", stats.positive);
                    row.put("low_sample", stats.denominator < LOW_SAMPLE_HEATMAP_THRESHOLD);
                    row.put("rate", BaselineCanonicalAggregateRules.safeRate(stats.positive, stats.denominator));
                    return row;
                })
                .toList());
        coverage.put("platforms", platformStats.entrySet().stream()
                .map(entry -> {
                    PlatformStats stats = entry.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("platform_code", entry.getKey());
                    row.put("mention_rate", BaselineCanonicalAggregateRules.safeRate(stats.appeared, stats.denominator));
                    row.put("recommended_rate", BaselineCanonicalAggregateRules.safeRate(stats.recommended, stats.denominator));
                    row.put("avg_ranking", stats.rankingCount == 0 ? null : Math.round((double) stats.rankingSum / stats.rankingCount * 10D) / 10D);
                    row.put("positive_sentiment_rate", BaselineCanonicalAggregateRules.safeRate(stats.positiveSentiment, stats.evaluableSentiment));
                    row.put("denominator", stats.denominator);
                    return row;
                })
                .toList());
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("visibility", dimension(coveredCellCount, rateDenominator));
        dimensions.put("recommendation", dimension(recommendedCellCount, rateDenominator));
        dimensions.put("sentiment_positive", dimension(positiveSentimentCount, evaluableSentimentCount));
        dimensions.put("stability", dimension(stableCellCount, rateDenominator));
        coverage.put("dimensions", dimensions);
        return coverage;
    }

    private Map<String, Object> dimension(int numerator, int denominator) {
        BaselineCanonicalAggregateRules.Band band = BaselineCanonicalAggregateRules.wilsonBand(numerator, denominator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rate", BaselineCanonicalAggregateRules.safeRate(numerator, denominator));
        row.put("numerator", numerator);
        row.put("denominator", denominator);
        row.put("band", Map.of(
                "low", band.low(),
                "high", band.high(),
                "method", band.method()
        ));
        return row;
    }

    private Map<String, Object> buildSentiment(Map<String, Integer> sentimentBuckets,
                                               int brandMentionCount,
                                               int evaluableSentimentCount,
                                               int unknownSentimentCount,
                                               List<BaselineObservation> observations,
                                               Map<Long, BaselineObservationScore> scoreByObservationId,
                                               Set<Long> negativeObservationIds) {
        Map<String, Object> sentiment = new LinkedHashMap<>();
        sentiment.put("brand_mention_count", brandMentionCount);
        sentiment.put("denominator_evaluable_sentiment_count", evaluableSentimentCount);
        sentiment.put("unknown_count", unknownSentimentCount);
        sentiment.put("distribution", sentimentBuckets);
        sentiment.put("positive_keywords", extractPositiveKeywords(observations, scoreByObservationId));
        List<Map<String, Object>> negativeEvidence = buildNegativeEvidence(observations, scoreByObservationId, negativeObservationIds);
        sentiment.put("negative_evidence_count", negativeEvidence.size());
        sentiment.put("negative_evidence", negativeEvidence);
        sentiment.put("platform_impressions", buildPlatformImpressions(observations, scoreByObservationId));
        return sentiment;
    }

    private List<Map<String, Object>> buildEvidenceCards(List<BaselineObservation> observations,
                                                         Map<Long, BaselineObservationScore> scoreByObservationId,
                                                         List<BaselineHighlightSpan> highlights,
                                                         Map<Long, BaselineQuestionSnapshot> questionById) {
        Map<Long, List<BaselineHighlightSpan>> highlightsByObservation = highlights.stream()
                .collect(Collectors.groupingBy(BaselineHighlightSpan::getObservationId));
        List<Map<String, Object>> candidates = observations.stream()
                .filter(observation -> {
                    BaselineObservationScore score = scoreByObservationId.get(observation.getId());
                    return score != null && (Boolean.TRUE.equals(score.getMentioned())
                            || !highlightsByObservation.getOrDefault(observation.getId(), List.of()).isEmpty());
                })
                .map(observation -> {
                    BaselineQuestionSnapshot question = questionById.get(observation.getQuestionSnapshotId());
                    List<BaselineHighlightSpan> observationHighlights = highlightsByObservation.getOrDefault(observation.getId(), List.of());
                    ExcerptWindow excerptWindow = excerptAroundHighlight(observation.getRawResponseText(), observationHighlights);
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("observation_id", observation.getId());
                    card.put("platform_code", observation.getPlatformCode());
                    card.put("question_id", question == null ? null : question.getQuestionKey());
                    card.put("question_snapshot_id", question == null ? null : question.getId());
                    card.put("question_text", question == null ? "" : question.getQuestionText());
                    card.put("intent_type", question == null ? null : question.getIntentType());
                    card.put("value_tier", question == null ? null : question.getValueTier());
                    card.put("takeaway", takeawayForEvidence(question, scoreByObservationId.get(observation.getId())));
                    card.put("raw_response_excerpt", excerptWindow.text());
                    card.put("highlight_spans", rebaseHighlights(excerptWindow, observationHighlights));
                    card.put("sample_label", "3 次中 " + countMentionsForCell(observations, scoreByObservationId, observation) + " 次提及");
                    return card;
                })
                .toList();
        return selectEvidenceCards(candidates);
    }

    private List<Map<String, Object>> selectEvidenceCards(List<Map<String, Object>> candidates) {
        List<String> intentOrder = List.of(
                BaselineReportSnapshotRules.INTENT_RECOMMENDATION,
                BaselineReportSnapshotRules.INTENT_COMPARISON,
                BaselineReportSnapshotRules.INTENT_PROBLEM,
                BaselineReportSnapshotRules.INTENT_AWARENESS,
                BaselineReportSnapshotRules.INTENT_SCENE
        );
        List<Map<String, Object>> selected = new ArrayList<>();
        java.util.Set<Object> selectedQuestions = new java.util.LinkedHashSet<>();
        for (String intent : intentOrder) {
            int pickedForIntent = 0;
            for (Map<String, Object> candidate : candidates) {
                if (!intent.equals(candidate.get("intent_type"))) {
                    continue;
                }
                Object questionId = candidate.get("question_snapshot_id");
                if (selectedQuestions.contains(questionId)) {
                    continue;
                }
                selected.add(candidate);
                selectedQuestions.add(questionId);
                pickedForIntent++;
                if (pickedForIntent >= 2 || selected.size() >= 15) {
                    break;
                }
            }
            if (selected.size() >= 15) {
                break;
            }
        }
        if (selected.size() < 15) {
            for (Map<String, Object> candidate : candidates) {
                Object questionId = candidate.get("question_snapshot_id");
                if (selectedQuestions.contains(questionId)) {
                    continue;
                }
                selected.add(candidate);
                selectedQuestions.add(questionId);
                if (selected.size() >= 15) {
                    break;
                }
            }
        }
        return selected;
    }

    private String takeawayForEvidence(BaselineQuestionSnapshot question, BaselineObservationScore score) {
        String intent = question == null ? "" : question.getIntentType();
        if (BaselineReportSnapshotRules.INTENT_COMPARISON.equals(intent)) {
            return Boolean.TRUE.equals(score == null ? null : score.getMentioned())
                    ? "用户读到这类回答，会把你纳入比较名单；若同时出现竞品，需要看 AI 的偏向描述。"
                    : "用户读到这类回答，更可能顺着 AI 给出的其他品牌继续比较。";
        }
        if (BaselineReportSnapshotRules.INTENT_RECOMMENDATION.equals(intent)) {
            return Boolean.TRUE.equals(score == null ? null : score.getRecommended())
                    ? "用户读到这类回答，会把你加入候选清单。"
                    : "用户读到这类回答，推荐入口可能被其他机构占位。";
        }
        if (BaselineReportSnapshotRules.INTENT_AWARENESS.equals(intent)) {
            return "用户读到这类回答，会形成对品牌基础信息是否清晰可信的第一印象。";
        }
        if (BaselineReportSnapshotRules.INTENT_PROBLEM.equals(intent)) {
            return "用户读到这类回答，会关注 AI 是否把你和具体问题解决能力关联起来。";
        }
        return "用户读到这类回答，会判断你是否适合当前具体场景。";
    }

    private List<Map<String, Object>> buildKeyFindings(int coveredCellCount,
                                                       int rateDenominator,
                                                       int brandMentionCount,
                                                       int recommendedCellCount,
                                                       int trackedCompetitorMentionCount,
                                                       int negativeSentimentCount) {
        return List.of(
                finding("coverage_overview", Map.of(
                        "covered_cell_count", coveredCellCount,
                        "denominator_cell_count", rateDenominator
                ), "有效样本单元中有 " + coveredCellCount + "/" + rateDenominator + " 达到覆盖口径。"),
                finding("brand_mentions", Map.of("brand_mention_count", brandMentionCount),
                        "品牌在有效回答中被明确提及 " + brandMentionCount + " 次。"),
                finding("recommendation_entry", Map.of("recommended_cell_count", recommendedCellCount),
                        "有 " + recommendedCellCount + " 个有效单元出现推荐信号，这是新客入口的当前起点。"),
                finding("competitive_pressure", Map.of("tracked_competitor_mention_count", trackedCompetitorMentionCount),
                        "已核实/跟踪竞品在回答中被提及 " + trackedCompetitorMentionCount + " 次，说明对手已在部分 AI 场景占位。"),
                finding("negative_risk", Map.of("negative_count", negativeSentimentCount),
                        "明确提及品牌的回答中发现负面倾向 " + negativeSentimentCount + " 次，需要后续优化优先排查。")
        );
    }

    private Map<String, Object> buildHeroMetrics(int coveredCellCount,
                                                 int rateDenominator,
                                                 int brandMentionCount,
                                                 int trackedCompetitorMentionCount,
                                                 int negativeSentimentCount) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("coverage_rate", BaselineCanonicalAggregateRules.safeRate(coveredCellCount, rateDenominator));
        metrics.put("brand_mention_count", brandMentionCount);
        metrics.put("tracked_competitor_mention_count", trackedCompetitorMentionCount);
        metrics.put("negative_count", negativeSentimentCount);
        return metrics;
    }

    private List<Map<String, Object>> buildDeltaPlaceholders() {
        return List.of(
                deltaPlaceholder("coverage.rate", "覆盖率环比"),
                deltaPlaceholder("sentiment.positive_rate", "正向情感环比"),
                deltaPlaceholder("competitors.tracked_mentions", "竞品提及环比")
        );
    }

    private Map<String, Object> deltaPlaceholder(String metricKey, String label) {
        Map<String, Object> placeholder = new LinkedHashMap<>();
        placeholder.put("metric_key", metricKey);
        placeholder.put("label", label);
        placeholder.put("status", "PENDING_NEXT_PERIOD");
        return placeholder;
    }

    private List<Map<String, Object>> verifiedSources(BaselineCompetitorSource source) {
        if (source == null || !"VERIFIED".equals(source.getReviewStatus())) {
            return List.of();
        }
        return List.of(sourceRow(source));
    }

    private Map<String, Object> sourceRow(BaselineCompetitorSource source) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("competitor_id", source.getCompetitorId());
        row.put("competitor_name", source.getCompetitorName());
        row.put("aliases_json", source.getAliasesJson());
        row.put("source_type", source.getSourceType());
        row.put("source_url", source.getSourceUrl());
        row.put("source_note", source.getSourceNote());
        row.put("verified_by", source.getVerifiedBy());
        row.put("verified_at", source.getVerifiedAt());
        return row;
    }

    private Map<String, BaselineCompetitorSource> configuredSourceLookup(List<BaselineCompetitorSource> sources) {
        Map<String, BaselineCompetitorSource> lookup = new LinkedHashMap<>();
        for (BaselineCompetitorSource source : sources) {
            if (!isConfiguredCompetitorSource(source)) {
                continue;
            }
            putSourceLookup(lookup, source.getCompetitorName(), source);
            for (String alias : parseAliases(source.getAliasesJson())) {
                putSourceLookup(lookup, alias, source);
            }
        }
        return lookup;
    }

    private void putSourceLookup(Map<String, BaselineCompetitorSource> lookup,
                                 String name,
                                 BaselineCompetitorSource source) {
        String normalized = normalizeName(name);
        if (!normalized.isBlank()) {
            lookup.putIfAbsent(normalized, source);
        }
    }

    private BaselineCompetitorSource configuredSourceForMention(BaselineCompetitorMention mention,
                                                                Map<String, BaselineCompetitorSource> sourceByName) {
        if (mention == null) {
            return null;
        }
        return sourceByName.get(normalizeName(mention.getNormalizedName()));
    }

    private boolean isConfiguredTrackedCompetitor(BaselineCompetitorMention mention,
                                                  Map<Long, Boolean> configuredCompetitorIds,
                                                  Map<String, BaselineCompetitorSource> sourceByName) {
        if (mention.getCompetitorId() != null && configuredCompetitorIds.containsKey(mention.getCompetitorId())) {
            return true;
        }
        return configuredSourceForMention(mention, sourceByName) != null;
    }

    private boolean isConfiguredCompetitorSource(BaselineCompetitorSource source) {
        return source != null
                && StringUtils.hasText(source.getCompetitorName())
                && !"REJECTED".equals(source.getReviewStatus());
    }

    private List<String> parseAliases(String aliasesJson) {
        if (!StringUtils.hasText(aliasesJson)) {
            return List.of();
        }
        try {
            return JSONUtil.parseArray(aliasesJson).stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> extractPositiveKeywords(List<BaselineObservation> observations,
                                                 Map<Long, BaselineObservationScore> scoreByObservationId) {
        Map<String, Boolean> keywords = new LinkedHashMap<>();
        for (BaselineObservation observation : observations) {
            BaselineObservationScore score = scoreByObservationId.get(observation.getId());
            if (score == null || !Boolean.TRUE.equals(score.getMentioned()) || !"POSITIVE".equals(score.getSentiment())) {
                continue;
            }
            String text = observation.getRawResponseText() == null ? "" : observation.getRawResponseText();
            for (String keyword : POSITIVE_KEYWORDS) {
                if (text.contains(keyword)) {
                    keywords.put(keyword, true);
                }
            }
        }
        return keywords.keySet().stream().limit(12).toList();
    }

    private List<Map<String, Object>> buildPlatformImpressions(List<BaselineObservation> observations,
                                                               Map<Long, BaselineObservationScore> scoreByObservationId) {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (BaselineObservation observation : observations) {
            BaselineObservationScore score = scoreByObservationId.get(observation.getId());
            if (score == null || !"SUCCESS".equals(observation.getCallStatus()) || score.getImpressionState() == null) {
                continue;
            }
            counts.computeIfAbsent(observation.getPlatformCode(), ignored -> new LinkedHashMap<>())
                    .merge(score.getImpressionState(), 1, Integer::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> platform : counts.entrySet()) {
            for (Map.Entry<String, Integer> state : platform.getValue().entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("platform_code", platform.getKey());
                row.put("impression_state", state.getKey());
                row.put("count", state.getValue());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> buildNegativeEvidence(List<BaselineObservation> observations,
                                                            Map<Long, BaselineObservationScore> scoreByObservationId,
                                                            Set<Long> negativeObservationIds) {
        List<NegativeEvidenceCandidate> candidates = observations.stream()
                .filter(observation -> {
                    BaselineObservationScore score = scoreByObservationId.get(observation.getId());
                    return score != null
                            && Boolean.TRUE.equals(score.getMentioned())
                            && "NEGATIVE".equals(score.getSentiment())
                            && negativeObservationIds.contains(observation.getId());
                })
                .map(observation -> new NegativeEvidenceCandidate(observation, resolveNegativeSeverity(observation.getRawResponseText())))
                .sorted((left, right) -> Integer.compare(severityRank(right.severity()), severityRank(left.severity())))
                .toList();
        List<NegativeEvidenceCandidate> selected = new ArrayList<>();
        Set<String> selectedPlatforms = new java.util.LinkedHashSet<>();
        for (NegativeEvidenceCandidate candidate : candidates) {
            if (selected.size() >= 3) {
                break;
            }
            if (selectedPlatforms.add(candidate.observation().getPlatformCode())) {
                selected.add(candidate);
            }
        }
        for (NegativeEvidenceCandidate candidate : candidates) {
            if (selected.size() >= 3) {
                break;
            }
            if (!selected.contains(candidate)) {
                selected.add(candidate);
            }
        }
        return selected.stream()
                .map(candidate -> {
                    BaselineObservation observation = candidate.observation();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("observation_id", observation.getId());
                    row.put("platform_code", observation.getPlatformCode());
                    row.put("severity", candidate.severity());
                    row.put("excerpt", cleanDisplayText(excerptAroundNegativeKeyword(observation.getRawResponseText()).text()));
                    return row;
                })
                .toList();
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private List<Map<String, Object>> rebaseHighlights(ExcerptWindow excerpt, List<BaselineHighlightSpan> spans) {
        return spans.stream()
                .filter(span -> span.getStartOffset() != null && span.getEndOffset() != null)
                .filter(span -> span.getStartOffset() >= excerpt.start()
                        && span.getEndOffset() <= excerpt.start() + excerpt.text().length())
                .map(span -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", span.getType());
                    row.put("text", span.getText());
                    row.put("start_offset", span.getStartOffset() - excerpt.start());
                    row.put("end_offset", span.getEndOffset() - excerpt.start());
                    return row;
                })
                .toList();
    }

    private int countMentionsForCell(List<BaselineObservation> observations,
                                     Map<Long, BaselineObservationScore> scoreByObservationId,
                                     BaselineObservation target) {
        return (int) observations.stream()
                .filter(observation -> Objects.equals(observation.getQuestionSnapshotId(), target.getQuestionSnapshotId()))
                .filter(observation -> Objects.equals(observation.getPlatformCode(), target.getPlatformCode()))
                .map(observation -> scoreByObservationId.get(observation.getId()))
                .filter(score -> score != null && Boolean.TRUE.equals(score.getMentioned()))
                .count();
    }

    private String excerpt(String text) {
        return excerptWindow(text, 0).text();
    }

    private ExcerptWindow excerptAroundHighlight(String text, List<BaselineHighlightSpan> spans) {
        Integer anchor = spans.stream()
                .filter(span -> span.getStartOffset() != null)
                .map(BaselineHighlightSpan::getStartOffset)
                .min(Integer::compareTo)
                .orElse(null);
        return excerptWindow(text, anchor);
    }

    private ExcerptWindow excerptAroundNegativeKeyword(String text) {
        return excerptWindow(text, firstKeywordIndex(text, HIGH_SEVERITY_NEGATIVE_KEYWORDS, MID_SEVERITY_NEGATIVE_KEYWORDS));
    }

    @SafeVarargs
    private Integer firstKeywordIndex(String text, List<String>... keywordGroups) {
        if (text == null) {
            return null;
        }
        Integer first = null;
        for (List<String> group : keywordGroups) {
            for (String keyword : group) {
                int index = text.indexOf(keyword);
                if (index >= 0 && (first == null || index < first)) {
                    first = index;
                }
            }
        }
        return first;
    }

    private ExcerptWindow excerptWindow(String text, Integer anchor) {
        if (text == null) {
            return new ExcerptWindow("", 0);
        }
        if (text.length() <= EXCERPT_LENGTH) {
            return new ExcerptWindow(text, 0);
        }
        int safeAnchor = anchor == null ? 0 : Math.max(0, Math.min(anchor, text.length() - 1));
        int start = Math.max(0, safeAnchor - EXCERPT_LENGTH / 2);
        if (start + EXCERPT_LENGTH > text.length()) {
            start = text.length() - EXCERPT_LENGTH;
        }
        return new ExcerptWindow(text.substring(start, start + EXCERPT_LENGTH), start);
    }

    private String cleanDisplayText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\t", " ")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+[.)]\\s+", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String resolveNegativeSeverity(String text) {
        if (containsAny(text, HIGH_SEVERITY_NEGATIVE_KEYWORDS)) {
            return "HIGH";
        }
        if (containsAny(text, MID_SEVERITY_NEGATIVE_KEYWORDS)) {
            return "MID";
        }
        return "LOW";
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrustedNegativeHighlight(BaselineHighlightSpan highlight) {
        if (highlight == null || !StringUtils.hasText(highlight.getText())) {
            return false;
        }
        return containsAny(highlight.getText(), HIGH_SEVERITY_NEGATIVE_KEYWORDS)
                || containsAny(highlight.getText(), MID_SEVERITY_NEGATIVE_KEYWORDS);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "HIGH" -> 3;
            case "MID" -> 2;
            default -> 1;
        };
    }

    private Map<String, Object> finding(String templateId, Map<String, Object> values, String renderedText) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("template_id", templateId);
        finding.put("values", values);
        finding.put("rendered_text", renderedText);
        return finding;
    }

    private Project requireReadableActiveProject(Long projectId) {
        SysUser user = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.read");
        Project project = projectMapper.selectById(projectId);
        if (project == null || project.getDeletedAt() != null) {
            throw new BizException(404, "Project not found");
        }
        currentUserService.ensurePartnerResourceAccess(user, project.getPartnerId(), "project");
        ensureSalesProjectAccess(user, project);
        if (!"active".equals(project.getStatus())) {
            throw new BizException(400, "仅已启动项目可以生成基线 canonical");
        }
        return project;
    }

    private void ensureSalesProjectAccess(SysUser user, Project project) {
        if (!"sales".equals(user.getRole())) {
            return;
        }
        Company company = companyMapper.selectById(project.getCompanyId());
        if (company == null || company.getDeletedAt() != null
                || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(user.getId())) {
            throw new BizException(403, "No permission to access this project");
        }
        if (!"signed".equals(company.getStatus())) {
            throw new BizException(403, "Sales can only access projects of signed companies");
        }
    }

    private BaselineSnapshot loadSealedSnapshot(Long projectId, Long baselineId) {
        BaselineSnapshot snapshot = baselineSnapshotMapper.selectById(baselineId);
        if (snapshot == null || !projectId.equals(snapshot.getProjectId())) {
            throw new BizException(404, "Baseline snapshot not found");
        }
        if (!"SEALED".equals(snapshot.getStatus())) {
            throw new BizException(400, "仅 SEALED 状态的基线快照允许生成 canonical");
        }
        return snapshot;
    }

    private BaselineCanonicalReportVO toVO(BaselineMetricSnapshot metric) {
        BaselineCanonicalReportVO vo = new BaselineCanonicalReportVO();
        vo.setBaselineId(metric.getBaselineId());
        vo.setCanonicalSchemaVersion(metric.getCanonicalSchemaVersion());
        vo.setScoreAlgorithmVersion(metric.getScoreAlgorithmVersion());
        vo.setHighlightAlgorithmVersion(metric.getHighlightAlgorithmVersion());
        vo.setCompetitorNormalizationVersion(metric.getCompetitorNormalizationVersion());
        vo.setCanonicalAggregateVersion(metric.getCanonicalAggregateVersion());
        vo.setCanonicalJson(metric.getCanonicalJson());
        return vo;
    }

    record CanonicalBuildResult(String canonicalJson, Map<String, Object> canonical) {
    }

    private record CellStats(int successSamples,
                             int failedSamples,
                             int positiveSamples,
                             int recommendedSamples,
                             int awarenessSamples,
                             int favorableSamples,
                             int brandMentionCount,
                             int evaluableSentimentCount,
                             int unknownSentimentCount,
                             int rankingSum,
                             int rankingCount,
                             Map<String, Integer> sentimentBuckets) {
    }

    private record ExcerptWindow(String text, int start) {
    }

    private record NegativeEvidenceCandidate(BaselineObservation observation, String severity) {
    }

    private static final class TierStats {
        int appeared;
        int recommended;
        int denominator;
    }

    private static final class HeatmapStats {
        final String intentType;
        final String platformCode;
        final String metricKind;
        int positive;
        int denominator;

        private HeatmapStats(String intentType, String platformCode, String metricKind) {
            this.intentType = intentType;
            this.platformCode = platformCode;
            this.metricKind = metricKind;
        }
    }

    private static final class PlatformStats {
        int appeared;
        int recommended;
        int denominator;
        int rankingSum;
        int rankingCount;
        int positiveSentiment;
        int evaluableSentiment;
    }
}
