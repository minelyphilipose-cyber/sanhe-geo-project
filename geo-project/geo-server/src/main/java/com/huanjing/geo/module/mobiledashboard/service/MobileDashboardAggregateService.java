package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardAggregateVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardMetricVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MobileDashboardAggregateService {

    private static final String JUDGE_PENDING = "裁判管道未上线，本阶段不输出该指标";
    private static final String NO_SOURCE = "当前平台暂无可用真实数据源";
    private static final String INDEX_SCOPE = "已收录仅统计可测量渠道：自有站点按业务规则视为已收录，有回查结果的自媒体按真实回查计入；未回查渠道不计入分母。";
    private static final String CURRENT_VISIBLE_PUBLISH_STATUS_SQL = "'published','published_confirmed','distributed'";
    private static final String DELIVERY_DRAFT_STATUS_SQL = "'approved','unpublished','distributing','pending_review','under_revision','published','distributed'";
    private static final String MOBILE_QUESTION_TIER = "A";
    private static final String POLL_RESPONSE_TEXT_SQL = """
            COALESCE(
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.platform_response')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.response_text')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.answerText')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.answer_text')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.raw_response'))
            )
            """;
    private static final List<String> AI_PLATFORM_CODES = List.of("doubao", "deepseek", "tongyi", "yuanbao");
    private static final List<String> CONTENT_DETAIL_CHANNELS = List.of("official_site", "douyin", "xiaohongshu", "wechat_mp", "toutiao", "baijiahao", "zhihu");
    private static final Set<String> MEASURABLE_INDEX_CHANNELS = Set.of(
            "official_site", "agent_site", "brand_official_site", "brand_geo_site", "agent_official_site",
            "forum", "forum_site", "industry_site", "authority_media",
            "wechat", "wechat_mp", "douyin", "xiaohongshu", "toutiao", "baijiahao", "zhihu",
            "self_media:wechat", "self_media:wechat_mp", "self_media:douyin", "self_media:xiaohongshu",
            "self_media:toutiao", "self_media:baijiahao", "self_media:zhihu");
    private static final String SELF_INDEX_CHANNEL_SQL = "'official_site','agent_site','brand_official_site','brand_geo_site','agent_official_site','forum','forum_site','industry_site','authority_media'";

    private final JdbcTemplate jdbcTemplate;
    private final MobileDashboardEntityJudgeService entityJudgeService;

    public MobileDashboardAggregateVO.Home home(Long projectId, LocalDate startDate, LocalDate endDate) {
        DateRange range = normalizeRange(startDate, endDate, LocalDate.now().minusDays(13), LocalDate.now());
        MentionAggregate mention = loadLatestMentionAggregate(projectId, null);
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        ContentFacts content = loadContentFacts(projectId, YearMonth.from(range.end()));
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId);

        MobileDashboardAggregateVO.Home vo = new MobileDashboardAggregateVO.Home();
        vo.setOverallMentionRate(rateMetric(mention.mentions(), mention.completed()));
        vo.setTrend(loadMentionTrend(projectId, range));
        vo.setMetrics(List.of(
                keyMetric("ai_recommend_rate", judgeRateMetric(focusJudge)),
                keyMetric("first_recommend_count", judgeCountMetric(focusJudge)),
                keyMetric("covered_question_count", fractionMetric(coverage.covered(), coverage.total())),
                keyMetric("total_asset_count", MobileDashboardMetricVO.available(content.totalPublished()))
        ));
        vo.setPlatformPerformance(loadLatestPlatformPerformance(projectId));
        vo.setSceneCoverage(loadLatestSceneCoverage(projectId));
        vo.setCompetitorComparison(loadCompetitorComparison(projectId, range, focusJudge));
        vo.setContentProgress(toContentProgress(content));
        vo.setEcoAssets(toEcoAssets(content, coverage.covered()));
        return vo;
    }

    public MobileDashboardAggregateVO.Monitor monitor(Long projectId,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      String platformCode,
                                                      Integer page,
                                                      Integer size) {
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId, platformCode);
        MobileDashboardAggregateVO.Monitor vo = new MobileDashboardAggregateVO.Monitor();
        MobileDashboardAggregateVO.MonitorOverview overview = new MobileDashboardAggregateVO.MonitorOverview();
        overview.setMonitoredQuestions(MobileDashboardMetricVO.available(coverage.total()));
        overview.setBrandMentioned(MobileDashboardMetricVO.available(coverage.covered()));
        overview.setAiRecommendRate(judgeRateMetric(focusJudge));
        overview.setFirstRecommendCount(judgeCountMetric(focusJudge));
        vo.setOverview(overview);
        vo.setPlatformFilters(new ArrayList<>(AI_PLATFORM_CODES));
        vo.setQuestionList(loadLatestQuestionMonitorList(projectId, platformCode, focusJudge, page, size));
        vo.setScenePerformance(loadLatestSceneCoverage(projectId));
        MobileDashboardAggregateVO.QuestionCoverageProgress progress = new MobileDashboardAggregateVO.QuestionCoverageProgress();
        progress.setCovered(MobileDashboardMetricVO.available(coverage.covered()));
        progress.setMonitoring(MobileDashboardMetricVO.available(Math.max(coverage.total() - coverage.covered(), 0)));
        progress.setBuilding(MobileDashboardMetricVO.available(countBuildingQuestionCoverage(projectId)));
        vo.setQuestionCoverage(progress);
        return vo;
    }

    public MobileDashboardAggregateVO.QuestionMonitorItem questionDetail(Long projectId, Long pollResultId) {
        if (pollResultId == null || pollResultId <= 0) {
            throw new BizException(400, "pollResultId is required");
        }
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId);
        boolean judgeReady = entityJudgeService.coverageReady(focusJudge);
        String judgeReason = judgeNotReadyReason(focusJudge);
        List<QuestionMonitorRow> rows = jdbcTemplate.query("""
                SELECT pr.keyword_result_id,
                       pr.id AS poll_result_id,
                       %1$s AS platform_code,
                       COALESCE(NULLIF(pr.keyword_text_snapshot, ''), CONCAT('问题 #', pr.id)) AS question_title,
                       pr.updated_at AS completed_at,
                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS mentioned,
                       j.judge_status,
                       j.recommended,
                       j.first_recommend,
                       j.rank_position,
                       j.evidence,
                       %2$s AS response_text
                  FROM poll_results pr
                  LEFT JOIN poll_result_entity_judge j
                    ON j.poll_result_id = pr.id
                   AND j.entity_type = 'focus_brand'
                   AND j.entity_ref_id = 0
                   AND j.entity_config_version = 1
                   AND j.judge_prompt_version = ?
                 WHERE pr.project_id = ?
                   AND pr.id = ?
                   AND pr.status = 'completed'
                   AND pr.question_tier = ?
                   AND pr.platform_code IN (%3$s)
                """.formatted(aiPlatformSqlCase("pr.platform_code"), POLL_RESPONSE_TEXT_SQL, supportedAiPlatformAliasSql()), (rs, rowNum) -> {
            boolean rowJudgeReady = judgeReady && "success".equalsIgnoreCase(rs.getString("judge_status"));
            return new QuestionMonitorRow(
                    rs.getLong("keyword_result_id"),
                    rs.getLong("poll_result_id"),
                    normalizeAiPlatformCode(rs.getString("platform_code")),
                    rs.getString("question_title"),
                    nullableDateTime(rs, "completed_at"),
                    rs.getBoolean("mentioned"),
                    rowJudgeReady,
                    nullableBoolean(rs, "recommended"),
                    nullableBoolean(rs, "first_recommend"),
                    nullableInt(rs, "rank_position"),
                    rowJudgeReady && StringUtils.hasText(rs.getString("evidence")) ? rs.getString("evidence") : null,
                    boundedText(rs.getString("response_text"), 4000)
            );
        }, MobileDashboardEntityJudgeService.PROMPT_VERSION, projectId, pollResultId, MOBILE_QUESTION_TIER);
        if (rows.isEmpty()) {
            throw new BizException(404, "未找到该问题监测记录");
        }
        return mergeQuestionMonitorRows(rows, judgeReady, judgeReason).get(0);
    }

    public MobileDashboardAggregateVO.Content content(Long projectId, YearMonth month) {
        YearMonth safeMonth = month == null ? YearMonth.now() : month;
        ContentFacts content = loadContentFacts(projectId, safeMonth);
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        List<String> contentChannels = loadConfiguredContentChannels(projectId);

        MobileDashboardAggregateVO.Content vo = new MobileDashboardAggregateVO.Content();
        vo.setOverview(toContentProgress(content));
        vo.setPlatformCompletion(loadPlatformCompletion(projectId, content.monthPublishedByChannel(), contentChannels));
        vo.setTaskList(loadContentTaskList(projectId, DateRange.month(safeMonth)));
        vo.setOwnedPublish(loadOwnedPublish(content, contentChannels));
        vo.setEcoAssets(toEcoAssets(content, coverage.covered()));
        return vo;
    }

    public MobileDashboardAggregateVO.Report report(Long projectId) {
        DateRange range = normalizeRange(null, null, LocalDate.now().minusDays(13), LocalDate.now());
        MentionAggregate mention = loadLatestMentionAggregate(projectId, null);
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        ContentFacts content = loadContentFacts(projectId, YearMonth.now());
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId);

        MobileDashboardAggregateVO.Report vo = new MobileDashboardAggregateVO.Report();
        vo.setOverallMentionRate(rateMetric(mention.mentions(), mention.completed()));
        vo.setTrend(loadMentionTrend(projectId, range));
        vo.setCoreResults(List.of(
                keyMetric("ai_recommend_rate", judgeRateMetric(focusJudge)),
                keyMetric("first_recommend_count", judgeCountMetric(focusJudge)),
                keyMetric("covered_question_count", fractionMetric(coverage.covered(), coverage.total())),
                keyMetric("total_asset_count", MobileDashboardMetricVO.available(content.totalPublished()))
        ));
        MobileDashboardAggregateVO.HighlightList highlights = new MobileDashboardAggregateVO.HighlightList();
        highlights.setAvailable(false);
        highlights.setReason(NO_SOURCE);
        vo.setHighlights(highlights);
        MobileDashboardAggregateVO.DeliverySummary summary = new MobileDashboardAggregateVO.DeliverySummary();
        summary.setPublished(MobileDashboardMetricVO.available(content.monthPublished()));
        summary.setAssetNew(MobileDashboardMetricVO.available(content.monthPublished()));
        summary.setIndexed(MobileDashboardMetricVO.available(content.monthIndexed()));
        summary.setCoveredQuestions(MobileDashboardMetricVO.available(coverage.covered()));
        summary.setIndexMeasurementScope(INDEX_SCOPE);
        vo.setDeliverySummary(summary);
        vo.setEcoAssets(toEcoAssets(content, coverage.covered()));
        return vo;
    }

    private MentionAggregate loadMentionAggregate(Long projectId, DateRange range, String platformCode) {
        List<Object> args = new ArrayList<>(List.of(projectId, Date.valueOf(range.start()), Date.valueOf(range.end()), MOBILE_QUESTION_TIER));
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND platform_code IN (%s) ".formatted(aliasSql(normalizeAiPlatformCode(platformCode)));
        }
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(completed_count), 0) AS completed_count,
                       COALESCE(SUM(CASE WHEN effective_hit_count > 0 THEN effective_hit_count ELSE hit_count END), 0) AS mention_count,
                       COUNT(DISTINCT CASE
                           WHEN (CASE WHEN effective_hit_count > 0 THEN effective_hit_count ELSE hit_count END) > 0
                           THEN %s END) AS covered_platform_count
                 FROM poll_platform_daily_summary
                 WHERE project_id = ?
                   AND batch_date BETWEEN ? AND ?
                   AND question_tier = ?
                """.formatted(aiPlatformSqlCase("platform_code")) + platformClause,
                (rs, rowNum) -> new MentionAggregate(
                        rs.getLong("completed_count"),
                        rs.getLong("mention_count"),
                        rs.getLong("covered_platform_count")
                ), args.toArray());
    }

    private MentionAggregate loadLatestMentionAggregate(Long projectId, String platformCode) {
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND pr.platform_code IN (%s) ".formatted(aliasSql(normalizeAiPlatformCode(platformCode)));
        }
        return jdbcTemplate.queryForObject("""
                WITH latest AS (
                    SELECT pr.id,
                           %1$s AS platform_code,
                           CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS hit_flag,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.keyword_result_id, %1$s
                               ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                           ) AS rn
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND pr.platform_code IN (%2$s)
                       %3$s
                )
                SELECT COUNT(*) AS completed_count,
                       COALESCE(SUM(hit_flag), 0) AS mention_count,
                       COUNT(DISTINCT CASE WHEN hit_flag > 0 THEN platform_code END) AS covered_platform_count
                  FROM latest
                 WHERE rn = 1
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql(), platformClause),
                (rs, rowNum) -> new MentionAggregate(
                        rs.getLong("completed_count"),
                        rs.getLong("mention_count"),
                        rs.getLong("covered_platform_count")
                ), projectId, MOBILE_QUESTION_TIER);
    }

    private MentionAggregate loadCompleteBatchMentionAggregate(Long projectId, LocalDate completeBatchDate, String platformCode) {
        if (completeBatchDate == null) {
            return new MentionAggregate(0, 0, 0);
        }
        return loadMentionAggregate(projectId, new DateRange(completeBatchDate, completeBatchDate), platformCode);
    }

    private List<MobileDashboardAggregateVO.TrendPoint> loadMentionTrend(Long projectId, DateRange range) {
        Map<LocalDate, MentionAggregate> byDate = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT batch_date,
                       COALESCE(SUM(completed_count), 0) AS completed_count,
                       COALESCE(SUM(CASE WHEN effective_hit_count > 0 THEN effective_hit_count ELSE hit_count END), 0) AS mention_count
                 FROM poll_platform_daily_summary
                 WHERE project_id = ?
                   AND batch_date BETWEEN ? AND ?
                   AND question_tier = ?
                 GROUP BY batch_date
                 ORDER BY batch_date ASC
                """, (RowCallbackHandler) rs -> byDate.put(rs.getDate("batch_date").toLocalDate(),
                        new MentionAggregate(
                                rs.getLong("completed_count"),
                                rs.getLong("mention_count"),
                                0
                        )), projectId, Date.valueOf(range.start()), Date.valueOf(range.end()), MOBILE_QUESTION_TIER);
        List<MobileDashboardAggregateVO.TrendPoint> points = new ArrayList<>();
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            MentionAggregate aggregate = byDate.getOrDefault(date, new MentionAggregate(0, 0, 0));
            MobileDashboardAggregateVO.TrendPoint point = new MobileDashboardAggregateVO.TrendPoint();
            point.setDate(date);
            point.setValue(percent(aggregate.mentions(), aggregate.completed()));
            points.add(point);
        }
        return points;
    }

    private List<MobileDashboardAggregateVO.PlatformMetric> loadPlatformPerformance(Long projectId, DateRange range) {
        return jdbcTemplate.query("""
                SELECT %s AS platform_code,
                       COALESCE(SUM(completed_count), 0) AS completed_count,
                       COALESCE(SUM(CASE WHEN effective_hit_count > 0 THEN effective_hit_count ELSE hit_count END), 0) AS mention_count
                 FROM poll_platform_daily_summary
                 WHERE project_id = ?
                   AND batch_date BETWEEN ? AND ?
                   AND question_tier = ?
                   AND platform_code IN (%s)
                 GROUP BY %s
                """.formatted(aiPlatformSqlCase("platform_code"), supportedAiPlatformAliasSql(), aiPlatformSqlCase("platform_code")), (rs, rowNum) -> {
                    String code = normalizeAiPlatformCode(rs.getString("platform_code"));
                    if (!StringUtils.hasText(code)) {
                        return null;
                    }
                    long completed = rs.getLong("completed_count");
                    long mentions = rs.getLong("mention_count");
                    MobileDashboardAggregateVO.PlatformMetric row = new MobileDashboardAggregateVO.PlatformMetric();
                    row.setCode(code);
                    row.setCompletedCount(completed);
                    row.setMentionCount(mentions);
                    row.setRate(rateMetric(mentions, completed));
                    return row;
                }, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()), MOBILE_QUESTION_TIER)
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((MobileDashboardAggregateVO.PlatformMetric row) -> metricValue(row.getRate())).reversed()
                        .thenComparingInt(row -> aiPlatformOrder(row.getCode())))
                .toList();
    }

    private List<MobileDashboardAggregateVO.PlatformMetric> loadLatestPlatformPerformance(Long projectId) {
        return jdbcTemplate.query("""
                WITH latest AS (
                    SELECT pr.id,
                           %1$s AS platform_code,
                           CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS effective_hit,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.keyword_result_id, %1$s
                               ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                           ) AS rn
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND pr.platform_code IN (%2$s)
                )
                SELECT platform_code,
                       COUNT(*) AS completed_count,
                       COALESCE(SUM(effective_hit), 0) AS mention_count
                  FROM latest
                 WHERE rn = 1
                 GROUP BY platform_code
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql()), (rs, rowNum) -> {
                    String code = normalizeAiPlatformCode(rs.getString("platform_code"));
                    if (!StringUtils.hasText(code)) {
                        return null;
                    }
                    long completed = rs.getLong("completed_count");
                    long mentions = rs.getLong("mention_count");
                    MobileDashboardAggregateVO.PlatformMetric row = new MobileDashboardAggregateVO.PlatformMetric();
                    row.setCode(code);
                    row.setCompletedCount(completed);
                    row.setMentionCount(mentions);
                    row.setRate(rateMetric(mentions, completed));
                    return row;
                }, projectId, MOBILE_QUESTION_TIER)
                .stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((MobileDashboardAggregateVO.PlatformMetric row) -> metricValue(row.getRate())).reversed()
                        .thenComparingInt(row -> aiPlatformOrder(row.getCode())))
                .toList();
    }

    private List<MobileDashboardAggregateVO.PlatformMetric> loadCompleteBatchPlatformPerformance(Long projectId,
                                                                                                 LocalDate completeBatchDate) {
        if (completeBatchDate == null) {
            return List.of();
        }
        return loadPlatformPerformance(projectId, new DateRange(completeBatchDate, completeBatchDate));
    }

    private List<MobileDashboardAggregateVO.SceneMetric> loadSceneCoverage(Long projectId, DateRange range) {
        List<SceneRow> rows = jdbcTemplate.query("""
                SELECT COALESCE(r.scene_code, '') AS scene_code,
                       COUNT(DISTINCT r.id) AS total_count,
                       COUNT(DISTINCT CASE
                           WHEN (CASE WHEN agg.effective_hit_count > 0 THEN agg.effective_hit_count ELSE agg.hit_count END) > 0
                           THEN r.id END) AS covered_count
                  FROM project_keyword_group_rel rel
                  JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                  JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                  LEFT JOIN (
                        SELECT s.keyword_result_id,
                               COALESCE(SUM(s.hit_count), 0) AS hit_count,
                               COALESCE(SUM(s.effective_hit_count), 0) AS effective_hit_count
                         FROM poll_keyword_daily_summary s
                         WHERE s.project_id = ?
                           AND s.batch_date BETWEEN ? AND ?
                           AND s.question_tier = ?
                         GROUP BY s.keyword_result_id
                  ) agg ON agg.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                 GROUP BY COALESCE(r.scene_code, '')
                """, (rs, rowNum) -> new SceneRow(
                        normalizeSceneCode(rs.getString("scene_code")),
                        rs.getLong("covered_count"),
                        rs.getLong("total_count")
                ), projectId, Date.valueOf(range.start()), Date.valueOf(range.end()), MOBILE_QUESTION_TIER, projectId, MOBILE_QUESTION_TIER);
        Map<String, SceneRow> merged = new LinkedHashMap<>();
        for (SceneRow row : rows) {
            if (!StringUtils.hasText(row.code())) {
                continue;
            }
            merged.merge(row.code(), row, (a, b) -> new SceneRow(a.code(), a.covered() + b.covered(), a.total() + b.total()));
        }
        merged.putIfAbsent("conversion", new SceneRow("conversion", 0, 0));
        return merged.values().stream().map(row -> {
            MobileDashboardAggregateVO.SceneMetric vo = new MobileDashboardAggregateVO.SceneMetric();
            vo.setCode(row.code());
            vo.setVisible(!"conversion".equals(row.code()));
            vo.setCovered(MobileDashboardMetricVO.available(row.covered()));
            vo.setTotal(MobileDashboardMetricVO.available(row.total()));
            return vo;
        }).toList();
    }

    private List<MobileDashboardAggregateVO.SceneMetric> loadLatestSceneCoverage(Long projectId) {
        List<SceneRow> rows = jdbcTemplate.query("""
                SELECT COALESCE(r.scene_code, '') AS scene_code,
                       COUNT(DISTINCT r.id) AS total_count,
                       COUNT(DISTINCT CASE WHEN COALESCE(lbq.hit_flag, 0) > 0 THEN r.id END) AS covered_count
                  FROM project_keyword_group_rel rel
                  JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                  JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                  LEFT JOIN (
                        SELECT project_id,
                               question_tier,
                               keyword_result_id,
                               MAX(hit_flag) AS hit_flag
                          FROM (
                                SELECT pr.project_id,
                                       pr.question_tier,
                                       pr.keyword_result_id,
                                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS hit_flag,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY pr.project_id, pr.question_tier, pr.keyword_result_id, pr.platform_code
                                           ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                                       ) AS rn
                                  FROM poll_results pr
                                 WHERE pr.status = 'completed'
                                   AND pr.keyword_result_id IS NOT NULL
                                   AND pr.platform_code IN (%s)
                          ) latest
                         WHERE rn = 1
                         GROUP BY project_id, question_tier, keyword_result_id
                  ) lbq ON lbq.project_id = rel.project_id
                       AND lbq.question_tier = r.question_tier
                       AND lbq.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                 GROUP BY COALESCE(r.scene_code, '')
                """.formatted(supportedAiPlatformAliasSql()),
                (rs, rowNum) -> new SceneRow(
                        normalizeSceneCode(rs.getString("scene_code")),
                        rs.getLong("covered_count"),
                        rs.getLong("total_count")
                ), projectId, MOBILE_QUESTION_TIER);
        Map<String, SceneRow> merged = new LinkedHashMap<>();
        for (SceneRow row : rows) {
            if (!StringUtils.hasText(row.code())) {
                continue;
            }
            merged.merge(row.code(), row, (a, b) -> new SceneRow(a.code(), a.covered() + b.covered(), a.total() + b.total()));
        }
        merged.putIfAbsent("conversion", new SceneRow("conversion", 0, 0));
        return merged.values().stream().map(row -> {
            MobileDashboardAggregateVO.SceneMetric vo = new MobileDashboardAggregateVO.SceneMetric();
            vo.setCode(row.code());
            vo.setVisible(!"conversion".equals(row.code()));
            vo.setCovered(MobileDashboardMetricVO.available(row.covered()));
            vo.setTotal(MobileDashboardMetricVO.available(row.total()));
            return vo;
        }).toList();
    }

    private List<MobileDashboardAggregateVO.SceneMetric> loadCompleteBatchSceneCoverage(Long projectId,
                                                                                       LocalDate completeBatchDate) {
        if (completeBatchDate == null) {
            return loadSceneCoverage(projectId, new DateRange(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1)));
        }
        if (hasCompleteBatchPollResults(projectId, completeBatchDate)) {
            return loadCompleteBatchResultSceneCoverage(projectId, completeBatchDate);
        }
        return loadSceneCoverage(projectId, new DateRange(completeBatchDate, completeBatchDate));
    }

    private QuestionCoverage loadCumulativeQuestionCoverage(Long projectId) {
        return loadQuestionCoverage(projectId, null);
    }

    private QuestionCoverage loadLatestQuestionCoverage(Long projectId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT r.id) AS total_count,
                       COUNT(DISTINCT CASE WHEN COALESCE(lbq.hit_flag, 0) > 0 THEN r.id END) AS covered_count
                  FROM project_keyword_group_rel rel
                  JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                  JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                  LEFT JOIN (
                        SELECT project_id,
                               question_tier,
                               keyword_result_id,
                               MAX(hit_flag) AS hit_flag
                          FROM (
                                SELECT pr.project_id,
                                       pr.question_tier,
                                       pr.keyword_result_id,
                                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS hit_flag,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY pr.project_id, pr.question_tier, pr.keyword_result_id, pr.platform_code
                                           ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                                       ) AS rn
                                  FROM poll_results pr
                                 WHERE pr.status = 'completed'
                                   AND pr.keyword_result_id IS NOT NULL
                                   AND pr.platform_code IN (%s)
                          ) latest
                         WHERE rn = 1
                         GROUP BY project_id, question_tier, keyword_result_id
                  ) lbq ON lbq.project_id = rel.project_id
                       AND lbq.question_tier = r.question_tier
                       AND lbq.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                """.formatted(supportedAiPlatformAliasSql()),
                (rs, rowNum) -> new QuestionCoverage(rs.getLong("covered_count"), rs.getLong("total_count")),
                projectId, MOBILE_QUESTION_TIER);
    }

    private QuestionCoverage loadCompleteBatchQuestionCoverage(Long projectId, LocalDate completeBatchDate) {
        if (completeBatchDate == null) {
            return new QuestionCoverage(0, loadCoreQuestionTotal(projectId));
        }
        if (hasCompleteBatchPollResults(projectId, completeBatchDate)) {
            return loadCompleteBatchResultQuestionCoverage(projectId, completeBatchDate);
        }
        return loadQuestionCoverage(projectId, new DateRange(completeBatchDate, completeBatchDate));
    }

    private boolean hasCompleteBatchPollResults(Long projectId, LocalDate completeBatchDate) {
        try {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.batch_date = ?
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND pr.platform_code IN (%s)
                    """.formatted(supportedAiPlatformAliasSql()), Long.class,
                    projectId, Date.valueOf(completeBatchDate), MOBILE_QUESTION_TIER);
            return count != null && count > 0;
        } catch (DataAccessException ignored) {
            return false;
        }
    }

    private QuestionCoverage loadCompleteBatchResultQuestionCoverage(Long projectId, LocalDate completeBatchDate) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN hit_flag > 0 THEN 1 ELSE 0 END), 0) AS covered_count
                  FROM (
                    SELECT DISTINCT r.id,
                           COALESCE(latest_hits.hit_flag, 0) AS hit_flag
                      FROM project_keyword_group_rel rel
                      JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                      JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                      LEFT JOIN (
                            SELECT keyword_result_id,
                                   MAX(hit_flag) AS hit_flag
                              FROM (
                                    SELECT pr.keyword_result_id,
                                           CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS hit_flag,
                                           ROW_NUMBER() OVER (
                                               PARTITION BY pr.keyword_result_id, %s
                                               ORDER BY pr.updated_at DESC, pr.id DESC
                                           ) AS rn
                                      FROM poll_results pr
                                     WHERE pr.project_id = ?
                                       AND pr.status = 'completed'
                                       AND pr.batch_date = ?
                                       AND pr.question_tier = ?
                                       AND pr.keyword_result_id IS NOT NULL
                                       AND pr.platform_code IN (%s)
                              ) latest
                             WHERE rn = 1
                             GROUP BY keyword_result_id
                      ) latest_hits ON latest_hits.keyword_result_id = r.id
                     WHERE rel.project_id = ?
                       AND COALESCE(kg.deleted, 0) = 0
                       AND r.question_tier = ?
                ) question_rows
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql()),
                (rs, rowNum) -> new QuestionCoverage(rs.getLong("covered_count"), rs.getLong("total_count")),
                projectId, Date.valueOf(completeBatchDate), MOBILE_QUESTION_TIER,
                projectId, MOBILE_QUESTION_TIER);
    }

    private List<MobileDashboardAggregateVO.SceneMetric> loadCompleteBatchResultSceneCoverage(Long projectId,
                                                                                              LocalDate completeBatchDate) {
        List<SceneRow> rows = jdbcTemplate.query("""
                SELECT scene_code,
                       COUNT(*) AS total_count,
                       COALESCE(SUM(CASE WHEN hit_flag > 0 THEN 1 ELSE 0 END), 0) AS covered_count
                  FROM (
                    SELECT DISTINCT r.id,
                           COALESCE(r.scene_code, '') AS scene_code,
                           COALESCE(latest_hits.hit_flag, 0) AS hit_flag
                      FROM project_keyword_group_rel rel
                      JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                      JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                      LEFT JOIN (
                            SELECT keyword_result_id,
                                   MAX(hit_flag) AS hit_flag
                              FROM (
                                    SELECT pr.keyword_result_id,
                                           CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS hit_flag,
                                           ROW_NUMBER() OVER (
                                               PARTITION BY pr.keyword_result_id, %s
                                               ORDER BY pr.updated_at DESC, pr.id DESC
                                           ) AS rn
                                      FROM poll_results pr
                                     WHERE pr.project_id = ?
                                       AND pr.status = 'completed'
                                       AND pr.batch_date = ?
                                       AND pr.question_tier = ?
                                       AND pr.keyword_result_id IS NOT NULL
                                       AND pr.platform_code IN (%s)
                              ) latest
                             WHERE rn = 1
                             GROUP BY keyword_result_id
                      ) latest_hits ON latest_hits.keyword_result_id = r.id
                     WHERE rel.project_id = ?
                       AND COALESCE(kg.deleted, 0) = 0
                       AND r.question_tier = ?
                 ) question_rows
                 GROUP BY scene_code
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql()),
                (rs, rowNum) -> new SceneRow(
                        normalizeSceneCode(rs.getString("scene_code")),
                        rs.getLong("covered_count"),
                        rs.getLong("total_count")
                ), projectId, Date.valueOf(completeBatchDate), MOBILE_QUESTION_TIER,
                projectId, MOBILE_QUESTION_TIER);
        Map<String, SceneRow> merged = new LinkedHashMap<>();
        for (SceneRow row : rows) {
            if (!StringUtils.hasText(row.code())) {
                continue;
            }
            merged.merge(row.code(), row, (a, b) -> new SceneRow(a.code(), a.covered() + b.covered(), a.total() + b.total()));
        }
        merged.putIfAbsent("conversion", new SceneRow("conversion", 0, 0));
        return merged.values().stream().map(row -> {
            MobileDashboardAggregateVO.SceneMetric vo = new MobileDashboardAggregateVO.SceneMetric();
            vo.setCode(row.code());
            vo.setVisible(!"conversion".equals(row.code()));
            vo.setCovered(MobileDashboardMetricVO.available(row.covered()));
            vo.setTotal(MobileDashboardMetricVO.available(row.total()));
            return vo;
        }).toList();
    }

    private LocalDate loadLatestCompletePollBatchDate(Long projectId) {
        long totalQuestionCount = loadCoreQuestionTotal(projectId);
        if (totalQuestionCount <= 0) {
            return null;
        }
        List<LocalDate> dates = jdbcTemplate.query("""
                SELECT s.batch_date
                  FROM poll_keyword_daily_summary s
                 WHERE s.project_id = ?
                   AND s.question_tier = ?
                   AND s.keyword_result_id IS NOT NULL
                 GROUP BY s.batch_date
                HAVING COUNT(DISTINCT CASE WHEN s.completed_count > 0 THEN s.keyword_result_id END) >= ?
                 ORDER BY s.batch_date DESC
                 LIMIT 1
                """, (rs, rowNum) -> rs.getDate("batch_date").toLocalDate(),
                projectId, MOBILE_QUESTION_TIER, totalQuestionCount);
        return dates.isEmpty() ? null : dates.get(0);
    }

    private long loadCoreQuestionTotal(Long projectId) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT r.id)
                  FROM project_keyword_group_rel rel
                  JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                  JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                """, Long.class, projectId, MOBILE_QUESTION_TIER);
        return total == null ? 0 : total;
    }

    private QuestionCoverage loadQuestionCoverage(Long projectId, DateRange range) {
        String summaryDateClause = "";
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (range != null) {
            summaryDateClause = " AND s.batch_date BETWEEN ? AND ? ";
            args.add(Date.valueOf(range.start()));
            args.add(Date.valueOf(range.end()));
        }
        args.add(MOBILE_QUESTION_TIER);
        args.add(projectId);
        args.add(MOBILE_QUESTION_TIER);
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT r.id) AS total_count,
                       COUNT(DISTINCT CASE
                           WHEN (CASE WHEN agg.effective_hit_count > 0 THEN agg.effective_hit_count ELSE agg.hit_count END) > 0
                           THEN r.id END) AS covered_count
                  FROM project_keyword_group_rel rel
                  JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                  JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                  LEFT JOIN (
                        SELECT s.keyword_result_id,
                               COALESCE(SUM(s.hit_count), 0) AS hit_count,
                               COALESCE(SUM(s.effective_hit_count), 0) AS effective_hit_count
                         FROM poll_keyword_daily_summary s
                         WHERE s.project_id = ?
                           %s
                           AND s.question_tier = ?
                         GROUP BY s.keyword_result_id
                  ) agg ON agg.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                """.formatted(summaryDateClause), (rs, rowNum) -> new QuestionCoverage(rs.getLong("covered_count"), rs.getLong("total_count")),
                args.toArray());
    }

    private MobileDashboardAggregateVO.QuestionMonitorList loadQuestionMonitorList(Long projectId,
                                                                                   DateRange range,
                                                                                   MobileDashboardEntityJudgeService.JudgeCoverage focusJudge) {
        boolean judgeReady = entityJudgeService.coverageReady(focusJudge);
        String judgeReason = judgeNotReadyReason(focusJudge);
        List<QuestionMonitorRow> rows = jdbcTemplate.query("""
                SELECT pr.keyword_result_id,
                       pr.id AS poll_result_id,
                       %s AS platform_code,
                       COALESCE(NULLIF(pr.keyword_text_snapshot, ''), CONCAT('问题 #', pr.id)) AS question_title,
                       pr.updated_at AS completed_at,
                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS mentioned,
                       j.judge_status,
                       j.recommended,
                       j.first_recommend,
                       j.rank_position,
                       j.evidence,
                       %s AS response_text
                  FROM poll_results pr
                  LEFT JOIN poll_result_entity_judge j
                    ON j.poll_result_id = pr.id
                   AND j.entity_type = 'focus_brand'
                   AND j.entity_ref_id = 0
                   AND j.entity_config_version = 1
                   AND j.judge_prompt_version = ?
                 WHERE pr.project_id = ?
                   AND pr.status = 'completed'
                   AND pr.batch_date BETWEEN ? AND ?
                   AND pr.question_tier = ?
                   AND pr.platform_code IN (%s)
                 ORDER BY CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 0 ELSE 1 END,
                          pr.batch_date DESC,
                          pr.updated_at DESC,
                          pr.id DESC
                """.formatted(aiPlatformSqlCase("pr.platform_code"), POLL_RESPONSE_TEXT_SQL, supportedAiPlatformAliasSql()), (rs, rowNum) -> {
                    boolean rowJudgeReady = judgeReady && "success".equalsIgnoreCase(rs.getString("judge_status"));
                    return new QuestionMonitorRow(
                            rs.getLong("keyword_result_id"),
                            rs.getLong("poll_result_id"),
                            normalizeAiPlatformCode(rs.getString("platform_code")),
                            rs.getString("question_title"),
                            nullableDateTime(rs, "completed_at"),
                            rs.getBoolean("mentioned"),
                            rowJudgeReady,
                            nullableBoolean(rs, "recommended"),
                            nullableBoolean(rs, "first_recommend"),
                            nullableInt(rs, "rank_position"),
                            rowJudgeReady && StringUtils.hasText(rs.getString("evidence")) ? rs.getString("evidence") : null,
                            boundedText(rs.getString("response_text"), 4000)
                    );
        }, MobileDashboardEntityJudgeService.PROMPT_VERSION, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()), MOBILE_QUESTION_TIER);
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items = mergeQuestionMonitorRows(rows, judgeReady, judgeReason);
        return toQuestionMonitorList(items, 1, 20, "暂无重点问题监测数据");
    }

    private MobileDashboardAggregateVO.QuestionMonitorList loadLatestQuestionMonitorList(Long projectId,
                                                                                         String platformCode,
                                                                                         MobileDashboardEntityJudgeService.JudgeCoverage focusJudge,
                                                                                         Integer page,
                                                                                         Integer size) {
        boolean judgeReady = entityJudgeService.coverageReady(focusJudge);
        String judgeReason = judgeNotReadyReason(focusJudge);
        int resolvedPage = normalizePage(page);
        int resolvedSize = normalizePageSize(size);
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND pr.platform_code IN (%s) ".formatted(aliasSql(normalizeAiPlatformCode(platformCode)));
        }
        List<QuestionMonitorRow> rows = jdbcTemplate.query("""
                WITH question_pool AS (
                    SELECT DISTINCT r.id AS keyword_result_id,
                           r.keyword_text AS question_title,
                           r.sort_order
                      FROM project_keyword_group_rel rel
                      JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                      JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                     WHERE rel.project_id = ?
                       AND COALESCE(kg.deleted, 0) = 0
                       AND r.question_tier = ?
                ),
                latest AS (
                    SELECT pr.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.keyword_result_id, %1$s
                               ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                           ) AS rn
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND pr.platform_code IN (%2$s)
                       %3$s
                )
                SELECT qp.keyword_result_id,
                       pr.id AS poll_result_id,
                       COALESCE(%1$s, ?) AS platform_code,
                       COALESCE(NULLIF(pr.keyword_text_snapshot, ''), qp.question_title, CONCAT('问题 #', qp.keyword_result_id)) AS question_title,
                       pr.updated_at AS completed_at,
                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS mentioned,
                       j.judge_status,
                       j.recommended,
                       j.first_recommend,
                       j.rank_position,
                       j.evidence,
                       %4$s AS response_text
                  FROM question_pool qp
                  LEFT JOIN latest pr ON pr.keyword_result_id = qp.keyword_result_id
                   AND pr.rn = 1
                  LEFT JOIN poll_result_entity_judge j
                    ON j.poll_result_id = pr.id
                   AND j.entity_type = 'focus_brand'
                   AND j.entity_ref_id = 0
                   AND j.entity_config_version = 1
                   AND j.judge_prompt_version = ?
                 ORDER BY CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 0 ELSE 1 END,
                          pr.batch_date DESC,
                          pr.updated_at DESC,
                          pr.id DESC,
                          qp.sort_order ASC,
                          qp.keyword_result_id ASC
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql(), platformClause, POLL_RESPONSE_TEXT_SQL), (rs, rowNum) -> {
            boolean rowJudgeReady = judgeReady && "success".equalsIgnoreCase(rs.getString("judge_status"));
            return new QuestionMonitorRow(
                    rs.getLong("keyword_result_id"),
                    nullableLong(rs, "poll_result_id"),
                    normalizeAiPlatformCode(rs.getString("platform_code")),
                    rs.getString("question_title"),
                    nullableDateTime(rs, "completed_at"),
                    rs.getBoolean("mentioned"),
                    rowJudgeReady,
                    nullableBoolean(rs, "recommended"),
                    nullableBoolean(rs, "first_recommend"),
                    nullableInt(rs, "rank_position"),
                    rowJudgeReady && StringUtils.hasText(rs.getString("evidence")) ? rs.getString("evidence") : null,
                    boundedText(rs.getString("response_text"), 4000)
            );
        }, projectId, MOBILE_QUESTION_TIER, projectId, MOBILE_QUESTION_TIER,
                StringUtils.hasText(platformCode) ? normalizeAiPlatformCode(platformCode) : "all",
                MobileDashboardEntityJudgeService.PROMPT_VERSION);
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items = mergeQuestionMonitorRows(rows, judgeReady, judgeReason);
        return toQuestionMonitorList(items, resolvedPage, resolvedSize, "暂无重点问题监测数据");
    }

    private MobileDashboardAggregateVO.QuestionMonitorList loadCompleteBatchQuestionMonitorList(Long projectId,
                                                                                                String platformCode,
                                                                                                MobileDashboardEntityJudgeService.JudgeCoverage focusJudge,
                                                                                                LocalDate completeBatchDate) {
        if (completeBatchDate == null) {
            MobileDashboardAggregateVO.QuestionMonitorList list = new MobileDashboardAggregateVO.QuestionMonitorList();
            list.setItems(List.of());
            list.setAvailable(false);
            list.setReason("暂无完整批次监测数据");
            return list;
        }
        boolean judgeReady = entityJudgeService.coverageReady(focusJudge);
        String judgeReason = judgeNotReadyReason(focusJudge);
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND pr.platform_code IN (%s) ".formatted(aliasSql(normalizeAiPlatformCode(platformCode)));
        }
        List<QuestionMonitorRow> rows = jdbcTemplate.query("""
                WITH latest AS (
                    SELECT pr.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.keyword_result_id, %1$s
                               ORDER BY pr.updated_at DESC, pr.id DESC
                           ) AS rn
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.batch_date = ?
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND pr.platform_code IN (%2$s)
                       %3$s
                )
                SELECT pr.keyword_result_id,
                       pr.id AS poll_result_id,
                       %1$s AS platform_code,
                       COALESCE(NULLIF(pr.keyword_text_snapshot, ''), CONCAT('问题 #', pr.id)) AS question_title,
                       pr.updated_at AS completed_at,
                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS mentioned,
                       j.judge_status,
                       j.recommended,
                       j.first_recommend,
                       j.rank_position,
                       j.evidence,
                       %4$s AS response_text
                  FROM latest pr
                  LEFT JOIN poll_result_entity_judge j
                    ON j.poll_result_id = pr.id
                   AND j.entity_type = 'focus_brand'
                   AND j.entity_ref_id = 0
                   AND j.entity_config_version = 1
                   AND j.judge_prompt_version = ?
                 WHERE pr.rn = 1
                 ORDER BY CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 0 ELSE 1 END,
                          pr.updated_at DESC,
                          pr.id DESC
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql(), platformClause, POLL_RESPONSE_TEXT_SQL), (rs, rowNum) -> {
            boolean rowJudgeReady = judgeReady && "success".equalsIgnoreCase(rs.getString("judge_status"));
            return new QuestionMonitorRow(
                    rs.getLong("keyword_result_id"),
                    rs.getLong("poll_result_id"),
                    normalizeAiPlatformCode(rs.getString("platform_code")),
                    rs.getString("question_title"),
                    nullableDateTime(rs, "completed_at"),
                    rs.getBoolean("mentioned"),
                    rowJudgeReady,
                    nullableBoolean(rs, "recommended"),
                    nullableBoolean(rs, "first_recommend"),
                    nullableInt(rs, "rank_position"),
                    rowJudgeReady && StringUtils.hasText(rs.getString("evidence")) ? rs.getString("evidence") : null,
                    boundedText(rs.getString("response_text"), 4000)
            );
        }, projectId, Date.valueOf(completeBatchDate), MOBILE_QUESTION_TIER, MobileDashboardEntityJudgeService.PROMPT_VERSION);
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items = mergeQuestionMonitorRows(rows, judgeReady, judgeReason);
        return toQuestionMonitorList(items, 1, 20, "暂无完整批次监测数据");
    }

    private List<MobileDashboardAggregateVO.QuestionMonitorItem> mergeQuestionMonitorRows(List<QuestionMonitorRow> rows,
                                                                                          boolean judgeReady,
                                                                                          String judgeReason) {
        Map<Long, List<QuestionMonitorRow>> grouped = new LinkedHashMap<>();
        for (QuestionMonitorRow row : rows) {
            grouped.computeIfAbsent(row.keywordResultId(), key -> new ArrayList<>()).add(row);
        }
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items = new ArrayList<>();
        for (List<QuestionMonitorRow> group : grouped.values()) {
            QuestionMonitorRow first = group.get(0);
            QuestionMonitorRow displayRow = group.stream()
                    .filter(QuestionMonitorRow::mentioned)
                    .filter(row -> StringUtils.hasText(row.platformCode()))
                    .filter(row -> StringUtils.hasText(row.responseText()))
                    .findFirst()
                    .orElseGet(() -> group.stream()
                            .filter(QuestionMonitorRow::mentioned)
                            .filter(row -> StringUtils.hasText(row.platformCode()))
                            .findFirst()
                            .orElse(first));
            boolean mentioned = group.stream().anyMatch(QuestionMonitorRow::mentioned);
            boolean hasSuccessfulJudge = group.stream().anyMatch(QuestionMonitorRow::rowJudgeReady);
            boolean recommended = group.stream().anyMatch(row -> row.rowJudgeReady() && Boolean.TRUE.equals(row.recommended()));
            boolean firstRecommend = group.stream().anyMatch(row -> row.rowJudgeReady() && Boolean.TRUE.equals(row.firstRecommend()));
            Integer rank = group.stream()
                    .filter(QuestionMonitorRow::rowJudgeReady)
                    .map(QuestionMonitorRow::rankPosition)
                    .filter(Objects::nonNull)
                    .min(Integer::compareTo)
                    .orElse(null);

            MobileDashboardAggregateVO.QuestionMonitorItem item = new MobileDashboardAggregateVO.QuestionMonitorItem();
            item.setKeywordResultId(first.keywordResultId());
            item.setPollResultId(displayRow.pollResultId());
            item.setPlatformCode(displayRow.platformCode());
            item.setPlatformCodes(mentioned && StringUtils.hasText(displayRow.platformCode())
                    ? List.of(displayRow.platformCode())
                    : List.of());
            item.setQuestionTitle(displayRow.questionTitle());
            item.setCompletedAt(displayRow.completedAt());
            item.setMentioned(mentioned);
            item.setRecommended(hasSuccessfulJudge
                    ? MobileDashboardMetricVO.available(recommended)
                    : MobileDashboardMetricVO.unavailable(judgeReady ? "当前问题暂无成功裁判结果" : judgeReason));
            item.setFirstRecommend(hasSuccessfulJudge
                    ? MobileDashboardMetricVO.available(firstRecommend)
                    : MobileDashboardMetricVO.unavailable(judgeReady ? "当前问题暂无成功裁判结果" : judgeReason));
            item.setRankPosition(hasSuccessfulJudge && rank != null
                    ? MobileDashboardMetricVO.available(rank)
                    : MobileDashboardMetricVO.unavailable(hasSuccessfulJudge ? "未识别推荐位次" : (judgeReady ? "当前问题暂无成功裁判结果" : judgeReason)));
            item.setEvidence(mentioned
                    ? group.stream()
                    .filter(row -> Objects.equals(row.platformCode(), displayRow.platformCode()))
                    .map(QuestionMonitorRow::evidence)
                    .filter(StringUtils::hasText)
                    .filter(evidence -> !isInternalJudgeReason(evidence))
                    .findFirst()
                    .orElse(null)
                    : null);
            item.setResponseText(displayRow.responseText());
            List<String> tags = new ArrayList<>();
            if (mentioned) {
                tags.add("mentioned");
            }
            if (hasSuccessfulJudge && recommended) {
                tags.add("recommended");
            }
            if (hasSuccessfulJudge && firstRecommend) {
                tags.add("first_recommend");
            }
            item.setTags(tags);
            items.add(item);
        }
        items.sort(Comparator
                .comparingInt(this::questionMonitorPriority)
                .thenComparing(
                        MobileDashboardAggregateVO.QuestionMonitorItem::getCompletedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        MobileDashboardAggregateVO.QuestionMonitorItem::getPollResultId,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ));
        return items;
    }

    private MobileDashboardAggregateVO.QuestionMonitorList toQuestionMonitorList(List<MobileDashboardAggregateVO.QuestionMonitorItem> items,
                                                                                 int page,
                                                                                 int size,
                                                                                 String emptyReason) {
        int total = items.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        int resolvedPage = totalPages == 0 ? 1 : Math.min(page, totalPages);
        int fromIndex = total == 0 ? 0 : Math.min((resolvedPage - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        MobileDashboardAggregateVO.QuestionMonitorList list = new MobileDashboardAggregateVO.QuestionMonitorList();
        list.setPage(resolvedPage);
        list.setSize(size);
        list.setTotal(total);
        list.setTotalPages(totalPages);
        list.setItems(total == 0 ? List.of() : new ArrayList<>(items.subList(fromIndex, toIndex)));
        list.setAvailable(total > 0);
        if (total == 0) {
            list.setReason(emptyReason);
        }
        return list;
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizePageSize(Integer size) {
        if (size == null || size < 1) {
            return 5;
        }
        return Math.min(size, 20);
    }

    private int questionMonitorPriority(MobileDashboardAggregateVO.QuestionMonitorItem item) {
        if (metricTrue(item.getFirstRecommend())) {
            return 0;
        }
        if (metricTrue(item.getRecommended()) || (item.getRankPosition() != null && item.getRankPosition().isAvailable())) {
            return 1;
        }
        if (Boolean.TRUE.equals(item.getMentioned())) {
            return 2;
        }
        return 3;
    }

    private boolean metricTrue(MobileDashboardMetricVO<Boolean> metric) {
        return metric != null && metric.isAvailable() && Boolean.TRUE.equals(metric.getValue());
    }

    private boolean isInternalJudgeReason(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("no_tracked_entity_matched")
                || normalized.equals("no_entity_hit")
                || normalized.equals("deterministic_no_entity_hit")
                || normalized.startsWith("no_tracked_entity_")
                || normalized.startsWith("deterministic_");
    }

    private ContentFacts loadContentFacts(Long projectId, YearMonth month) {
        DateRange monthRange = DateRange.month(month);
        long totalPublished = countPublished(projectId, null);
        long monthPublished = countPublished(projectId, monthRange);
        long monthContent = countMonthContent(projectId, monthRange);
        long building = countBuildingContent(projectId, monthRange);
        Map<String, Long> publishedByChannel = loadPublishedByChannel(projectId, monthRange);
        long indexedTotal = countIndexed(projectId, null);
        long indexedMonth = countIndexed(projectId, monthRange);
        Map<String, Long> indexedByChannel = loadIndexedByChannel(projectId, monthRange);
        return new ContentFacts(totalPublished, monthPublished, monthContent, building, indexedTotal, indexedMonth,
                publishedByChannel, indexedByChannel);
    }

    private List<String> loadConfiguredContentChannels(Long projectId) {
        List<String> channels = jdbcTemplate.query("""
                SELECT channel_code
                  FROM project_channel_allocation
                 WHERE project_id = ?
                   AND period_type_snapshot IN ('month', 'monthly')
                   AND allocated_count > 0
                """, (rs, rowNum) -> normalizeContentChannelCode(rs.getString("channel_code")), projectId).stream()
                .filter(StringUtils::hasText)
                .filter(CONTENT_DETAIL_CHANNELS::contains)
                .distinct()
                .sorted(Comparator.comparingInt(CONTENT_DETAIL_CHANNELS::indexOf))
                .toList();
        return channels.isEmpty() ? CONTENT_DETAIL_CHANNELS : channels;
    }

    private List<MobileDashboardAggregateVO.PlatformCompletion> loadPlatformCompletion(Long projectId, Map<String, Long> publishedByChannel) {
        return loadPlatformCompletion(projectId, publishedByChannel, loadConfiguredContentChannels(projectId));
    }

    private List<MobileDashboardAggregateVO.PlatformCompletion> loadPlatformCompletion(Long projectId,
                                                                                       Map<String, Long> publishedByChannel,
                                                                                       List<String> contentChannels) {
        Map<String, Long> quotaByChannel = jdbcTemplate.query("""
                SELECT channel_code, allocated_count
                  FROM project_channel_allocation
                 WHERE project_id = ?
                   AND period_type_snapshot IN ('month', 'monthly')
                   AND allocated_count > 0
                """, (rs, rowNum) -> Map.entry(normalizeContentChannelCode(rs.getString("channel_code")), rs.getLong("allocated_count")),
                projectId).stream().collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
        return contentChannels.stream()
                .filter(code -> quotaByChannel.getOrDefault(code, 0L) > 0 || publishedByChannel.getOrDefault(code, 0L) > 0)
                .map(code -> {
                    long quota = quotaByChannel.getOrDefault(code, 0L);
                    long published = publishedByChannel.getOrDefault(code, 0L);
                    MobileDashboardAggregateVO.PlatformCompletion vo = new MobileDashboardAggregateVO.PlatformCompletion();
                    vo.setCode(code);
                    vo.setQuota(quota);
                    vo.setPublished(published);
                    vo.setCompletionRate(quota > 0
                            ? rateMetric(published, quota)
                            : MobileDashboardMetricVO.unavailable("暂无逐渠道月度配额，仅展示已发布数"));
                    return vo;
                }).toList();
    }

    private List<MobileDashboardAggregateVO.OwnedPublish> loadOwnedPublish(ContentFacts facts) {
        return loadOwnedPublish(facts, CONTENT_DETAIL_CHANNELS);
    }

    private List<MobileDashboardAggregateVO.OwnedPublish> loadOwnedPublish(ContentFacts facts, List<String> contentChannels) {
        return contentChannels.stream().map(code -> {
            MobileDashboardAggregateVO.OwnedPublish vo = new MobileDashboardAggregateVO.OwnedPublish();
            vo.setCode(code);
            vo.setPublished(MobileDashboardMetricVO.available(facts.monthPublishedByChannel().getOrDefault(code, 0L)));
            vo.setIndexed(MobileDashboardMetricVO.available(facts.monthIndexedByChannel().getOrDefault(code, 0L)));
            return vo;
        }).toList();
    }

    private long countMonthContent(Long projectId, DateRange range) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT id)
                  FROM article_draft
                 WHERE project_id = ?
                   AND (
                        EXISTS (
                            SELECT 1
                              FROM article_publish_record pr
                             WHERE pr.article_id = article_draft.id
                               AND pr.project_id = article_draft.project_id
                               AND pr.publish_status IN (%s)
                               AND DATE(COALESCE(pr.published_at, pr.verified_at, pr.created_at)) BETWEEN ? AND ?
                        )
                        OR (
                            DATE(created_at) BETWEEN ? AND ?
                            AND %s
                        )
                   )
                """.formatted(CURRENT_VISIBLE_PUBLISH_STATUS_SQL, deliveryDraftPredicate("article_draft")),
                Long.class, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()),
                Date.valueOf(range.start()), Date.valueOf(range.end()));
        return value == null ? 0 : value;
    }

    private MobileDashboardAggregateVO.TaskList loadContentTaskList(Long projectId, DateRange range) {
        List<MobileDashboardAggregateVO.ContentTaskItem> items = jdbcTemplate.query("""
                SELECT ad.id,
                       ad.title,
                       ad.topic,
                       ad.topic_as_question,
                       ad.target_channel,
                       ad.status AS draft_status,
                       ad.updated_at,
                       ad.created_at,
                       pub.platform_codes,
                       pub.visible_count,
                       pub.indexed_count,
                       pub.latest_publish_at,
                       pub.publish_url
                  FROM article_draft ad
                  LEFT JOIN (
                        SELECT article_id,
                               GROUP_CONCAT(DISTINCT COALESCE(target_channel, target_kind, '') ORDER BY COALESCE(target_channel, target_kind, '') SEPARATOR ',') AS platform_codes,
                               COUNT(DISTINCT id) AS visible_count,
                               COUNT(DISTINCT CASE
                                   WHEN COALESCE(target_channel, target_kind, '') IN (%s)
                                    AND (
                                        COALESCE(target_channel, target_kind, '') IN (%s)
                                        OR verified_at IS NOT NULL
                                    )
                                   THEN id END) AS indexed_count,
                               MAX(COALESCE(published_at, verified_at, created_at)) AS latest_publish_at,
                               SUBSTRING_INDEX(
                                   GROUP_CONCAT(
                                       NULLIF(TRIM(published_url), '')
                                       ORDER BY COALESCE(published_at, verified_at, created_at) DESC, id DESC
                                       SEPARATOR '\n'
                                   ),
                                   '\n',
                                   1
                               ) AS publish_url
                         FROM article_publish_record
                         WHERE project_id = ?
                           AND publish_status IN (%s)
                           AND COALESCE(target_channel, target_kind, '') IN (%s)
                         GROUP BY article_id
                 ) pub ON pub.article_id = ad.id
                 WHERE ad.project_id = ?
                   AND DATE(COALESCE(pub.latest_publish_at, ad.updated_at, ad.created_at)) BETWEEN ? AND ?
                   AND (
                        COALESCE(pub.visible_count, 0) > 0
                        OR (
                            %s
                            AND COALESCE(ad.target_channel, '') IN (%s)
                        )
                   )
                 ORDER BY CASE
                            WHEN COALESCE(pub.indexed_count, 0) > 0 THEN 0
                            WHEN COALESCE(pub.visible_count, 0) > 0 THEN 1
                            ELSE 2
                          END,
                          COALESCE(pub.latest_publish_at, ad.updated_at, ad.created_at) DESC,
                          ad.id DESC
                 LIMIT 4
                """.formatted(quoted(MEASURABLE_INDEX_CHANNELS), SELF_INDEX_CHANNEL_SQL, CURRENT_VISIBLE_PUBLISH_STATUS_SQL, contentDetailChannelSql(), deliveryDraftPredicate("ad"), contentDetailChannelSql()), (rs, rowNum) -> {
                    long visibleCount = rs.getLong("visible_count");
                    long indexedCount = rs.getLong("indexed_count");
                    String status = indexedCount > 0 ? "indexed" : (visibleCount > 0 ? "published" : "building");
                    MobileDashboardAggregateVO.ContentTaskItem item = new MobileDashboardAggregateVO.ContentTaskItem();
                    item.setDraftId(rs.getLong("id"));
                    item.setTitle(rs.getString("title"));
                    item.setKeywords(taskKeywords(rs.getString("topic_as_question"), rs.getString("topic")));
                    item.setPlatformCodes(taskPlatformCodes(rs.getString("platform_codes"), rs.getString("target_channel")));
                    item.setPublishUrl(rs.getString("publish_url"));
                    item.setStatus(status);
                    LocalDateTime date = nullableDateTime(rs, "latest_publish_at");
                    if (date == null) {
                        date = nullableDateTime(rs, "updated_at");
                    }
                    if (date == null) {
                        date = nullableDateTime(rs, "created_at");
                    }
                    item.setDate(date);
                    return item;
                }, projectId, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()));
        MobileDashboardAggregateVO.TaskList list = new MobileDashboardAggregateVO.TaskList();
        list.setItems(items);
        list.setAvailable(!items.isEmpty());
        if (items.isEmpty()) {
            list.setReason("暂无内容任务数据");
        }
        return list;
    }

    private long countBuildingContent(Long projectId, DateRange range) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT id)
                  FROM article_draft
                 WHERE project_id = ?
                   AND DATE(created_at) BETWEEN ? AND ?
                   AND %s
                   AND NOT EXISTS (
                        SELECT 1
                          FROM article_publish_record pr
                         WHERE pr.article_id = article_draft.id
                           AND pr.project_id = article_draft.project_id
                           AND pr.publish_status IN (%s)
                   )
                """.formatted(deliveryDraftPredicate("article_draft"), CURRENT_VISIBLE_PUBLISH_STATUS_SQL), Long.class, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()));
        return value == null ? 0 : value;
    }

    private long countBuildingQuestionCoverage(Long projectId) {
        Set<String> buildingScenes = loadBuildingQuestionSceneCodes(projectId);
        if (buildingScenes.isEmpty()) {
            return 0;
        }
        List<QuestionSceneCoverageRow> rows = jdbcTemplate.query("""
                SELECT r.id AS keyword_result_id,
                       COALESCE(r.scene_code, '') AS scene_code,
                       COALESCE(lbq.hit_flag, 0) AS hit_flag
                  FROM project_keyword_group_rel rel
                  JOIN keyword_group kg ON kg.id = rel.keyword_group_id
                  JOIN keyword_group_result r ON r.group_id = rel.keyword_group_id
                  LEFT JOIN (
                        SELECT project_id,
                               question_tier,
                               keyword_result_id,
                               MAX(hit_flag) AS hit_flag
                          FROM (
                                SELECT pr.project_id,
                                       pr.question_tier,
                                       pr.keyword_result_id,
                                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 1 ELSE 0 END AS hit_flag,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY pr.project_id, pr.question_tier, pr.keyword_result_id, pr.platform_code
                                           ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                                       ) AS rn
                                  FROM poll_results pr
                                 WHERE pr.status = 'completed'
                                   AND pr.keyword_result_id IS NOT NULL
                                   AND pr.platform_code IN (%s)
                          ) latest
                         WHERE rn = 1
                         GROUP BY project_id, question_tier, keyword_result_id
                  ) lbq ON lbq.project_id = rel.project_id
                       AND lbq.question_tier = r.question_tier
                       AND lbq.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                """.formatted(supportedAiPlatformAliasSql()),
                (rs, rowNum) -> new QuestionSceneCoverageRow(
                        rs.getLong("keyword_result_id"),
                        normalizeSceneCode(rs.getString("scene_code")),
                        rs.getLong("hit_flag") > 0
                ), projectId, MOBILE_QUESTION_TIER);
        return rows.stream()
                .filter(row -> !row.covered())
                .filter(row -> buildingScenes.contains(row.sceneCode()))
                .map(QuestionSceneCoverageRow::keywordResultId)
                .distinct()
                .count();
    }

    private Set<String> loadBuildingQuestionSceneCodes(Long projectId) {
        return new LinkedHashSet<>(jdbcTemplate.query("""
                SELECT DISTINCT t.question_scene_code AS scene_code
                  FROM article_draft ad
                  JOIN article_prompt_template t ON t.id = ad.prompt_template_id
                 WHERE ad.project_id = ?
                   AND t.question_scene_code IS NOT NULL
                   AND t.question_scene_code <> ''
                   AND %s
                   AND NOT EXISTS (
                        SELECT 1
                          FROM article_publish_record pr
                         WHERE pr.article_id = ad.id
                           AND pr.project_id = ad.project_id
                           AND pr.publish_status IN (%s)
                   )
                """.formatted(deliveryDraftPredicate("ad"), CURRENT_VISIBLE_PUBLISH_STATUS_SQL),
                (rs, rowNum) -> normalizeSceneCode(rs.getString("scene_code")), projectId));
    }

    private long countPublished(Long projectId, DateRange range) {
        QueryParts query = publishDateClause(range);
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT article_id)
                  FROM article_publish_record
                 WHERE project_id = ?
                   AND publish_status IN (%s)
                """.formatted(CURRENT_VISIBLE_PUBLISH_STATUS_SQL) + query.sql(), Long.class, query.args(projectId));
        return value == null ? 0 : value;
    }

    private Map<String, Long> loadPublishedByChannel(Long projectId, DateRange range) {
        QueryParts query = publishDateClause(range);
        return jdbcTemplate.query("""
                SELECT COALESCE(target_channel, target_kind, '') AS channel_code,
                       COUNT(DISTINCT article_id) AS published_count
                  FROM article_publish_record
                 WHERE project_id = ?
                   AND publish_status IN (%s)
                """.formatted(CURRENT_VISIBLE_PUBLISH_STATUS_SQL) + query.sql() + """
                 GROUP BY COALESCE(target_channel, target_kind, '')
                """, (rs, rowNum) -> Map.entry(normalizeContentChannelCode(rs.getString("channel_code")), rs.getLong("published_count")),
                query.args(projectId)).stream().collect(LinkedHashMap::new, (m, e) -> m.merge(e.getKey(), e.getValue(), Long::sum), Map::putAll);
    }

    private long countIndexed(Long projectId, DateRange range) {
        QueryParts query = publishDateClause(range);
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT article_id)
                  FROM article_publish_record
                 WHERE project_id = ?
                   AND publish_status IN (%s)
                   AND COALESCE(target_channel, target_kind, '') IN (%s)
                   AND (
                        COALESCE(target_channel, target_kind, '') IN (%s)
                        OR verified_at IS NOT NULL
                   )
                """.formatted(CURRENT_VISIBLE_PUBLISH_STATUS_SQL, quoted(MEASURABLE_INDEX_CHANNELS), SELF_INDEX_CHANNEL_SQL) + query.sql(), Long.class, query.args(projectId));
        return value == null ? 0 : value;
    }

    private Map<String, Long> loadIndexedByChannel(Long projectId, DateRange range) {
        QueryParts query = publishDateClause(range);
        return jdbcTemplate.query("""
                SELECT COALESCE(target_channel, target_kind, '') AS channel_code,
                       COUNT(DISTINCT article_id) AS indexed_count
                  FROM article_publish_record
                 WHERE project_id = ?
                   AND publish_status IN (%s)
                   AND COALESCE(target_channel, target_kind, '') IN (%s)
                   AND (
                        COALESCE(target_channel, target_kind, '') IN (%s)
                        OR verified_at IS NOT NULL
                   )
                """.formatted(CURRENT_VISIBLE_PUBLISH_STATUS_SQL, quoted(MEASURABLE_INDEX_CHANNELS), SELF_INDEX_CHANNEL_SQL) + query.sql() + """
                 GROUP BY COALESCE(target_channel, target_kind, '')
                """, (rs, rowNum) -> Map.entry(normalizeContentChannelCode(rs.getString("channel_code")), rs.getLong("indexed_count")),
                query.args(projectId)).stream().collect(LinkedHashMap::new, (m, e) -> m.merge(e.getKey(), e.getValue(), Long::sum), Map::putAll);
    }

    private MobileDashboardAggregateVO.ContentProgress toContentProgress(ContentFacts facts) {
        MobileDashboardAggregateVO.ContentProgress vo = new MobileDashboardAggregateVO.ContentProgress();
        vo.setMonthContent(MobileDashboardMetricVO.available(facts.monthContent()));
        vo.setPublished(MobileDashboardMetricVO.available(facts.monthPublished()));
        vo.setIndexed(MobileDashboardMetricVO.available(facts.monthIndexed()));
        vo.setBuilding(MobileDashboardMetricVO.available(facts.monthBuilding()));
        vo.setIndexMeasurementScope(INDEX_SCOPE);
        return vo;
    }

    private MobileDashboardAggregateVO.EcoAssets toEcoAssets(ContentFacts facts, long coveredQuestions) {
        MobileDashboardAggregateVO.EcoAssets vo = new MobileDashboardAggregateVO.EcoAssets();
        vo.setTotalAssets(MobileDashboardMetricVO.available(facts.totalPublished()));
        vo.setMonthNew(MobileDashboardMetricVO.available(facts.monthPublished()));
        vo.setIndexed(MobileDashboardMetricVO.available(facts.totalIndexed()));
        vo.setCoveredQuestions(MobileDashboardMetricVO.available(coveredQuestions));
        vo.setIndexMeasurementScope(INDEX_SCOPE);
        return vo;
    }

    private MobileDashboardAggregateVO.CompetitorComparison unavailableCompetitor() {
        MobileDashboardAggregateVO.CompetitorComparison vo = new MobileDashboardAggregateVO.CompetitorComparison();
        vo.setAvailable(false);
        vo.setReason(JUDGE_PENDING);
        return vo;
    }

    private MobileDashboardAggregateVO.CompetitorComparison loadCompetitorComparison(Long projectId,
                                                                                     DateRange range,
                                                                                     MobileDashboardEntityJudgeService.JudgeCoverage focusJudge) {
        MobileDashboardAggregateVO.CompetitorComparison vo = new MobileDashboardAggregateVO.CompetitorComparison();
        List<MobileDashboardEntityJudgeService.CompetitorSummary> summaries =
                entityJudgeService.latestCompetitorSummaries(projectId);
        if (summaries.isEmpty()) {
            vo.setAvailable(false);
            vo.setReason("当前项目未配置竞品");
            return vo;
        }
        List<MobileDashboardEntityJudgeService.CompetitorSummary> qaPassed = summaries.stream()
                .filter(row -> "passed".equalsIgnoreCase(row.qaStatus()))
                .toList();
        if (qaPassed.isEmpty()) {
            vo.setAvailable(false);
            vo.setReason("竞品裁判准确率尚未通过生产 QA");
            return vo;
        }
        if (!entityJudgeService.coverageReady(focusJudge)) {
            vo.setAvailable(false);
            vo.setReason(judgeNotReadyReason(focusJudge));
            return vo;
        }
        List<MobileDashboardEntityJudgeService.CompetitorSummary> readyCompetitors = qaPassed.stream()
                .filter(row -> entityJudgeService.coverageReady(row.coverage()))
                .toList();
        if (readyCompetitors.isEmpty()) {
            vo.setAvailable(false);
            vo.setReason("竞品裁判样本分析中，覆盖率未达" + entityJudgeService.coverageThresholdPercent() + "%");
            return vo;
        }

        vo.setAvailable(true);
        vo.getRows().add(Map.of(
                "displayName", projectDisplayName(projectId),
                "entityType", "focus_brand",
                "recommendedCount", focusJudge.recommendedCount(),
                "firstRecommendCount", focusJudge.firstRecommendCount(),
                "coveragePercent", percent(focusJudge.successCount(), focusJudge.expectedCount()),
                "highlight", true
        ));
        for (MobileDashboardEntityJudgeService.CompetitorSummary row : readyCompetitors) {
            vo.getRows().add(Map.of(
                    "displayName", competitorDisplayName(row.displayOrder()),
                    "entityType", "competitor",
                    "recommendedCount", row.coverage().recommendedCount(),
                    "firstRecommendCount", row.coverage().firstRecommendCount(),
                    "coveragePercent", percent(row.coverage().successCount(), row.coverage().expectedCount()),
                    "highlight", false
            ));
        }
        return vo;
    }

    private MobileDashboardMetricVO<Integer> judgeRateMetric(MobileDashboardEntityJudgeService.JudgeCoverage coverage) {
        if (!entityJudgeService.coverageReady(coverage)) {
            return MobileDashboardMetricVO.unavailable(judgeNotReadyReason(coverage));
        }
        return MobileDashboardMetricVO.available(percent(coverage.recommendedCount(), coverage.successCount()), "%");
    }

    private MobileDashboardMetricVO<Long> judgeCountMetric(MobileDashboardEntityJudgeService.JudgeCoverage coverage) {
        if (!entityJudgeService.coverageReady(coverage)) {
            return MobileDashboardMetricVO.unavailable(judgeNotReadyReason(coverage));
        }
        return MobileDashboardMetricVO.available(coverage.firstRecommendCount());
    }

    private String judgeNotReadyReason(MobileDashboardEntityJudgeService.JudgeCoverage coverage) {
        if (coverage == null || coverage.expectedCount() <= 0) {
            return "暂无裁判样本";
        }
        int current = percent(coverage.successCount(), coverage.expectedCount());
        return "裁判样本分析中，覆盖率" + current + "%未达" + entityJudgeService.coverageThresholdPercent() + "%";
    }

    private String projectDisplayName(Long projectId) {
        String name = jdbcTemplate.queryForObject("""
                SELECT COALESCE(NULLIF(brand_name, ''), project_name)
                  FROM project
                 WHERE id = ?
                """, String.class, projectId);
        return StringUtils.hasText(name) ? name : "本品牌";
    }

    private String competitorDisplayName(int displayOrder) {
        int index = Math.max(1, displayOrder);
        if (index <= 26) {
            return "竞品" + (char) ('A' + index - 1);
        }
        return "竞品" + index;
    }

    private MobileDashboardAggregateVO.KeyMetric keyMetric(String key, MobileDashboardMetricVO<?> metric) {
        MobileDashboardAggregateVO.KeyMetric vo = new MobileDashboardAggregateVO.KeyMetric();
        vo.setKey(key);
        vo.setMetric(metric);
        return vo;
    }

    private MobileDashboardMetricVO<Integer> rateMetric(long numerator, long denominator) {
        if (denominator <= 0) {
            return MobileDashboardMetricVO.unavailable("暂无已完成样本");
        }
        return MobileDashboardMetricVO.available(percent(numerator, denominator), "%");
    }

    private MobileDashboardMetricVO<String> fractionMetric(long numerator, long denominator) {
        if (denominator <= 0) {
            return MobileDashboardMetricVO.unavailable("暂无监测问题");
        }
        return MobileDashboardMetricVO.available(numerator + "/" + denominator);
    }

    private int percent(long numerator, long denominator) {
        return denominator <= 0 ? 0 : (int) Math.round(numerator * 100.0 / denominator);
    }

    private Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nullableDateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private String boundedText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private List<String> taskKeywords(String topicAsQuestion, String topic) {
        List<String> values = new ArrayList<>();
        addIfText(values, topicAsQuestion);
        addIfText(values, topic);
        return values.stream().distinct().limit(3).toList();
    }

    private List<String> taskPlatformCodes(String platformCodes, String fallbackTargetChannel) {
        List<String> values = new ArrayList<>();
        if (StringUtils.hasText(platformCodes)) {
            for (String code : platformCodes.split(",")) {
                addIfText(values, normalizeContentChannelCode(code));
            }
        }
        if (values.isEmpty()) {
            addIfText(values, normalizeContentChannelCode(fallbackTargetChannel));
        }
        return values.stream().filter(StringUtils::hasText).distinct().toList();
    }

    private void addIfText(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private DateRange normalizeRange(LocalDate start, LocalDate end, LocalDate defaultStart, LocalDate defaultEnd) {
        LocalDate safeEnd = end == null ? defaultEnd : end;
        LocalDate safeStart = start == null ? defaultStart : start;
        return safeStart.isAfter(safeEnd) ? new DateRange(safeEnd, safeStart) : new DateRange(safeStart, safeEnd);
    }

    private QueryParts publishDateClause(DateRange range) {
        if (range == null) {
            return new QueryParts("", List.of());
        }
        return new QueryParts(" AND DATE(COALESCE(published_at, verified_at, created_at)) BETWEEN ? AND ? ",
                List.of(Date.valueOf(range.start()), Date.valueOf(range.end())));
    }

    private String normalizeAiPlatformCode(String code) {
        String value = normalize(code);
        return switch (value) {
            case "qwen" -> "tongyi";
            case "ernie" -> "wenxin";
            case "hunyuan" -> "yuanbao";
            default -> value;
        };
    }

    private String normalizeContentChannelCode(String code) {
        String value = normalize(code);
        return switch (value) {
            case "wechat", "self_media:wechat", "self_media:wechat_mp" -> "wechat_mp";
            case "self_media:douyin" -> "douyin";
            case "self_media:xiaohongshu" -> "xiaohongshu";
            case "self_media:toutiao" -> "toutiao";
            case "self_media:baijiahao" -> "baijiahao";
            case "self_media:zhihu" -> "zhihu";
            case "agent_site", "agent_site_article", "brand_official_site", "brand_geo_site", "agent_official_site" -> "official_site";
            default -> value;
        };
    }

    private String contentDetailChannelSql() {
        return "'official_site','agent_site','agent_site_article','brand_official_site','brand_geo_site','agent_official_site',"
                + "'wechat','wechat_mp','douyin','xiaohongshu','toutiao','baijiahao','zhihu',"
                + "'self_media:wechat','self_media:wechat_mp','self_media:douyin','self_media:xiaohongshu',"
                + "'self_media:toutiao','self_media:baijiahao','self_media:zhihu'";
    }

    private String normalizeSceneCode(String code) {
        String value = normalize(code);
        return switch (value) {
            case "brand", "awareness", "brand_awareness", "cognition" -> "brand_awareness";
            case "regional", "region", "area", "regional_recommendation", "local" -> "regional_recommendation";
            case "decision", "decision_scenario", "compare", "comparison" -> "decision_scenario";
            case "deal" -> "purchase_consultation";
            case "conversion", "transaction" -> "conversion";
            default -> value;
        };
    }

    private String deliveryDraftPredicate(String alias) {
        return """
                %1$s.status IN (%2$s)
                AND COALESCE(%1$s.allocation_mode, '') <> 'auto'
                """.formatted(alias, DELIVERY_DRAFT_STATUS_SQL);
    }

    private String normalizeQuestionText(String text) {
        return normalize(text).replaceAll("\\s+", "");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private int aiPlatformOrder(String code) {
        int index = AI_PLATFORM_CODES.indexOf(normalizeAiPlatformCode(code));
        return index < 0 ? AI_PLATFORM_CODES.size() : index;
    }

    private int metricValue(MobileDashboardMetricVO<Integer> metric) {
        return metric != null && metric.isAvailable() && metric.getValue() != null ? metric.getValue() : -1;
    }

    private String aliasSql(String normalizedCode) {
        return switch (normalizedCode) {
            case "tongyi" -> "'tongyi','qwen'";
            case "wenxin" -> "'wenxin','ernie'";
            case "yuanbao" -> "'yuanbao','hunyuan'";
            default -> "'" + normalizedCode.replace("'", "''") + "'";
        };
    }

    private String quoted(Collection<String> values) {
        return values.stream().map(v -> "'" + v.replace("'", "''") + "'").reduce((a, b) -> a + "," + b).orElse("''");
    }

    private String supportedAiPlatformAliasSql() {
        return "'doubao','deepseek','tongyi','qwen','yuanbao','hunyuan'";
    }

    private String aiPlatformSqlCase(String expression) {
        return """
                CASE
                    WHEN %1$s = 'doubao' THEN 'doubao'
                    WHEN %1$s = 'deepseek' THEN 'deepseek'
                    WHEN %1$s IN ('tongyi', 'qwen') THEN 'tongyi'
                    WHEN %1$s IN ('yuanbao', 'hunyuan') THEN 'yuanbao'
                    ELSE NULL
                END
                """.formatted(expression);
    }

    private record DateRange(LocalDate start, LocalDate end) {
        static DateRange month(YearMonth month) {
            LocalDate end = month.atEndOfMonth().isAfter(LocalDate.now()) ? LocalDate.now() : month.atEndOfMonth();
            return new DateRange(month.atDay(1), end);
        }
    }

    private record QueryParts(String sql, List<Object> values) {
        Object[] args(Long projectId) {
            List<Object> args = new ArrayList<>();
            args.add(projectId);
            args.addAll(values);
            return args.toArray();
        }
    }

    private record MentionAggregate(long completed, long mentions, long coveredPlatformCount) {
    }

    private record QuestionCoverage(long covered, long total) {
    }

    private record SceneRow(String code, long covered, long total) {
    }

    private record QuestionSceneCoverageRow(Long keywordResultId, String sceneCode, boolean covered) {
    }

    private record QuestionMonitorRow(Long keywordResultId,
                                      Long pollResultId,
                                      String platformCode,
                                      String questionTitle,
                                      LocalDateTime completedAt,
                                      boolean mentioned,
                                      boolean rowJudgeReady,
                                      Boolean recommended,
                                      Boolean firstRecommend,
                                      Integer rankPosition,
                                      String evidence,
                                      String responseText) {
    }

    private record ContentFacts(long totalPublished,
                                long monthPublished,
                                long monthContent,
                                long monthBuilding,
                                long totalIndexed,
                                long monthIndexed,
                                Map<String, Long> monthPublishedByChannel,
                                Map<String, Long> monthIndexedByChannel) {
    }
}
