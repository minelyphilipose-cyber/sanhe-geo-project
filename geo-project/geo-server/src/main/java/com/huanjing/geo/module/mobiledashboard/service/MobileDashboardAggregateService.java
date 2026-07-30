package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.ArticlePromptChannels;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardAggregateVO;
import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardMetricVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
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
    private static final String MONITOR_STATUS_MENTIONED = "mentioned";
    private static final String MONITOR_STATUS_NOT_MENTIONED = "not_mentioned";
    private static final String MONITOR_STATUS_SEARCH_NOT_TRIGGERED = "search_not_triggered";
    private static final String MONITOR_STATUS_PENDING = "pending";
    private static final String EFFECTIVE_WEB_SEARCH_REQUEST_SQL = """
            pr.execution_finalized = 1
            AND pr.effective_attempt_id IS NOT NULL
            AND pr.search_requested = 1
            """;
    private static final String EFFECTIVE_WEB_SEARCH_RESULT_SQL = EFFECTIVE_WEB_SEARCH_REQUEST_SQL + """
            AND pr.search_triggered = 1
            """;
    private static final String WEB_SEARCH_MENTION_SQL = """
            CASE WHEN pr.search_triggered = 1
                  AND (pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.brand_in_answer = 1))
                 THEN 1 ELSE 0 END
            """;
    private static final String POLL_CHANNEL_SQL = "COALESCE(NULLIF(TRIM(pr.channel_code), ''), pr.platform_code)";
    private static final String POLL_RESPONSE_TEXT_SQL = """
            COALESCE(
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.platform_response')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.response_text')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.answerText')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.answer_text')),
                JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.raw_response'))
            )
            """;
    private static final Set<String> MEASURABLE_INDEX_CHANNELS = Set.of(
            "official_site", "agent_site", "brand_geo_site", "agent_official_site",
            "forum", "forum_site", "industry_site", "authority_media",
            "wechat", "wechat_mp", "douyin", "xiaohongshu", "toutiao", "baijiahao", "zhihu",
            "self_media:wechat", "self_media:wechat_mp", "self_media:douyin", "self_media:xiaohongshu",
            "self_media:toutiao", "self_media:baijiahao", "self_media:zhihu");
    private static final String SELF_INDEX_CHANNEL_SQL = "'official_site','agent_site','brand_geo_site','agent_official_site','forum','forum_site','industry_site','authority_media'";
    private static final String PUBLIC_CONTENT_PUBLISH_URL_SQL = """
            CASE
              WHEN url_quality IN ('public_url', 'verified_public_url')
               AND NULLIF(TRIM(published_url), '') IS NOT NULL
               AND LOWER(TRIM(published_url)) NOT LIKE '%%/preview%%'
               AND LOWER(TRIM(published_url)) NOT LIKE '%%/edit%%'
               AND LOWER(TRIM(published_url)) NOT LIKE '%%creator.xiaohongshu.com%%'
               AND LOWER(TRIM(published_url)) NOT LIKE '%%mp.toutiao.com/profile_v4/graphic/preview%%'
               AND LOWER(TRIM(published_url)) NOT LIKE '%%baijiahao.baidu.com/builder/preview%%'
              THEN NULLIF(TRIM(published_url), '')
            END
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MobileDashboardEntityJudgeService entityJudgeService;
    private final MobileDashboardAiPlatformCatalog aiPlatformCatalog;

    public MobileDashboardAggregateVO.Home home(Long projectId, LocalDate startDate, LocalDate endDate) {
        DateRange range = normalizeRange(startDate, endDate, LocalDate.now().minusDays(13), LocalDate.now());
        MentionAggregate mention = loadLatestMentionAggregate(projectId, null, range.end());
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        ContentFacts content = loadContentFacts(projectId, YearMonth.from(range.end()));
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId);

        MobileDashboardAggregateVO.Home vo = new MobileDashboardAggregateVO.Home();
        vo.setOverallMentionRate(rateMetric(mention.mentions(), mention.requested()));
        vo.setMeasurement(toMeasurementMeta(mention, focusJudge));
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
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId, platformCode);
        MentionAggregate mention = loadLatestMentionAggregate(projectId, platformCode);
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId, platformCode);
        MobileDashboardAggregateVO.Monitor vo = new MobileDashboardAggregateVO.Monitor();
        MobileDashboardAggregateVO.MonitorOverview overview = new MobileDashboardAggregateVO.MonitorOverview();
        overview.setMonitoredQuestions(MobileDashboardMetricVO.available(coverage.total()));
        overview.setBrandMentioned(MobileDashboardMetricVO.available(coverage.covered()));
        overview.setAiRecommendRate(judgeRateMetric(focusJudge));
        overview.setFirstRecommendCount(judgeCountMetric(focusJudge));
        vo.setOverview(overview);
        vo.setMeasurement(toMeasurementMeta(mention, focusJudge));
        vo.setPlatformFilters(new ArrayList<>(aiPlatformCatalog.scope().canonicalCodes()));
        vo.setQuestionList(loadLatestQuestionMonitorList(projectId, platformCode, focusJudge, page, size));
        vo.setScenePerformance(loadLatestSceneCoverage(projectId, platformCode));
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
        List<QuestionMonitorRow> rows = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT pr.keyword_result_id,
                       pr.id AS poll_result_id,
                       %1$s AS platform_code,
                       COALESCE(NULLIF(pr.keyword_text_snapshot, ''), CONCAT('问题 #', pr.id)) AS question_title,
                       pr.updated_at AS completed_at,
                       %4$s AS mentioned,
                       pr.search_triggered,
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
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND %5$s
                   AND %6$s IN (%3$s)
                """.formatted(aiPlatformSqlCase(POLL_CHANNEL_SQL), POLL_RESPONSE_TEXT_SQL,
                supportedAiPlatformAliasSql(), WEB_SEARCH_MENTION_SQL,
                EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL), "pr"), (rs, rowNum) -> {
            Boolean searchTriggered = nullableBoolean(rs, "search_triggered");
            boolean rowJudgeReady = Boolean.TRUE.equals(searchTriggered)
                    && judgeReady
                    && "success".equalsIgnoreCase(rs.getString("judge_status"));
            return new QuestionMonitorRow(
                    rs.getLong("keyword_result_id"),
                    rs.getLong("poll_result_id"),
                    normalizeAiPlatformCode(rs.getString("platform_code")),
                    rs.getString("question_title"),
                    nullableDateTime(rs, "completed_at"),
                    rs.getBoolean("mentioned"),
                    searchTriggered,
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
        MobileDashboardAggregateVO.QuestionMonitorItem item = mergeQuestionMonitorRows(rows, judgeReady, judgeReason).get(0);
        enrichQuestionMonitorItems(List.of(item));
        item.setSearchSources(loadQuestionSearchSources(projectId, pollResultId));
        item.setRelatedContentTasks(loadRelatedBuildingContentTasks(projectId, item.getKeywordResultId()));
        return item;
    }

    private List<MobileDashboardAggregateVO.QuestionSearchSource> loadQuestionSearchSources(Long projectId,
                                                                                             Long pollResultId) {
        List<MobileDashboardAggregateVO.QuestionSearchSource> candidates = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT s.id AS source_id,
                       (
                           SELECT MIN(c.citation_index)
                             FROM poll_citations c
                            WHERE c.attempt_id = pr.effective_attempt_id
                              AND c.source_id = s.id
                              AND c.confidence IN ('CONFIRMED', 'PROBABLE')
                       ) AS citation_index,
                       s.rank_no,
                       s.title,
                       COALESCE(NULLIF(TRIM(s.normalized_url), ''), NULLIF(TRIM(s.original_url), '')) AS source_url,
                       s.domain,
                       s.snippet,
                       s.publish_time,
                       s.brand_matched,
                       CASE WHEN EXISTS (
                            SELECT 1
                              FROM poll_citations c
                             WHERE c.attempt_id = pr.effective_attempt_id
                               AND c.source_id = s.id
                               AND c.confidence IN ('CONFIRMED', 'PROBABLE')
                       ) THEN 1 ELSE 0 END AS cited
                  FROM poll_results pr
                  JOIN poll_search_sources s ON s.attempt_id = pr.effective_attempt_id
                 WHERE pr.project_id = ?
                   AND pr.id = ?
                   AND pr.status = 'completed'
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND %s
                 ORDER BY cited DESC,
                          COALESCE(s.brand_matched, 0) DESC,
                          COALESCE(s.rank_no, 2147483647) ASC,
                          s.id ASC
                 LIMIT 30
                """.formatted(EFFECTIVE_WEB_SEARCH_RESULT_SQL), "pr"), (rs, rowNum) -> {
            String url = safePublicSourceUrl(rs.getString("source_url"));
            if (!StringUtils.hasText(url)) {
                return null;
            }
            MobileDashboardAggregateVO.QuestionSearchSource source =
                    new MobileDashboardAggregateVO.QuestionSearchSource();
            source.setSourceId(rs.getLong("source_id"));
            source.setCitationIndex(nullableInt(rs, "citation_index"));
            source.setRankNo(nullableInt(rs, "rank_no"));
            source.setTitle(boundedText(rs.getString("title"), 160));
            source.setUrl(url);
            source.setDomain(sourceDomain(url));
            source.setSnippet(boundedText(rs.getString("snippet"), 220));
            source.setPublishTime(nullableDateTime(rs, "publish_time"));
            source.setCited(rs.getBoolean("cited"));
            source.setBrandMatched(nullableBoolean(rs, "brand_matched"));
            return source;
        }, projectId, pollResultId);

        Set<String> seenUrls = new LinkedHashSet<>();
        List<MobileDashboardAggregateVO.QuestionSearchSource> sources = new ArrayList<>();
        for (MobileDashboardAggregateVO.QuestionSearchSource candidate : candidates) {
            if (candidate == null || !seenUrls.add(sourceUrlDedupKey(candidate.getUrl()))) {
                continue;
            }
            sources.add(candidate);
            if (sources.size() >= 6) {
                break;
            }
        }
        return sources;
    }

    public MobileDashboardAggregateVO.Content content(Long projectId, YearMonth month) {
        return content(projectId, month, 1, 4);
    }

    public MobileDashboardAggregateVO.Content content(Long projectId, YearMonth month, Integer taskPage, Integer taskSize) {
        YearMonth safeMonth = month == null ? YearMonth.now() : month;
        ContentFacts content = loadContentFacts(projectId, safeMonth);
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        List<String> contentChannels = loadConfiguredContentChannels(projectId);
        List<String> boundContentChannels = loadBoundContentChannels(projectId, contentChannels);

        MobileDashboardAggregateVO.Content vo = new MobileDashboardAggregateVO.Content();
        vo.setUpdatedAt(loadLatestMentionAggregate(projectId, null).latestUpdatedAt());
        vo.setOverview(toContentProgress(content));
        vo.setContentPlatforms(MobileDashboardContentChannelCatalog.platformOptions());
        vo.setPlatformCompletion(loadPlatformCompletion(projectId, content.monthPublishedByChannel(), boundContentChannels));
        vo.setTaskList(loadContentTaskList(projectId, DateRange.month(safeMonth), taskPage, taskSize));
        vo.setOwnedPublish(loadOwnedPublish(content, boundContentChannels));
        vo.setEcoAssets(toEcoAssets(content, coverage.covered()));
        return vo;
    }

    public MobileDashboardAggregateVO.Report report(Long projectId) {
        DateRange range = normalizeRange(null, null, LocalDate.now().minusDays(13), LocalDate.now());
        MentionAggregate mention = loadLatestMentionAggregate(projectId, null, range.end());
        QuestionCoverage coverage = loadLatestQuestionCoverage(projectId);
        ContentFacts content = loadContentFacts(projectId, YearMonth.now());
        MobileDashboardEntityJudgeService.JudgeCoverage focusJudge = entityJudgeService.latestFocusCoverage(projectId);

        MobileDashboardAggregateVO.Report vo = new MobileDashboardAggregateVO.Report();
        vo.setOverallMentionRate(rateMetric(mention.mentions(), mention.requested()));
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
            platformClause = " AND %s IN (%s) ".formatted(POLL_CHANNEL_SQL, aliasSql(normalizeAiPlatformCode(platformCode)));
        }
        return jdbcTemplate.queryForObject(MobileDashboardQuestionScopeSql.apply("""
                SELECT COUNT(*) AS requested_count,
                       COALESCE(SUM(CASE WHEN pr.search_triggered = 1 THEN 1 ELSE 0 END), 0) AS completed_count,
                       COALESCE(SUM(%1$s), 0) AS mention_count,
                       COUNT(DISTINCT CASE
                           WHEN %1$s > 0 THEN %2$s END) AS covered_platform_count
                  FROM poll_results pr
                 WHERE pr.project_id = ?
                   AND pr.batch_date BETWEEN ? AND ?
                   AND pr.status = 'completed'
                   AND pr.question_tier = ?
                   AND pr.keyword_result_id IS NOT NULL
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND %3$s
                   AND %4$s IN (%5$s)
                """.formatted(WEB_SEARCH_MENTION_SQL, aiPlatformSqlCase(POLL_CHANNEL_SQL),
                EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL, supportedAiPlatformAliasSql()) + platformClause, "pr"),
                (rs, rowNum) -> new MentionAggregate(
                        rs.getLong("requested_count"),
                        rs.getLong("completed_count"),
                        rs.getLong("mention_count"),
                        rs.getLong("covered_platform_count"),
                        null,
                        null
                ), args.toArray());
    }

    private MentionAggregate loadLatestMentionAggregate(Long projectId, String platformCode) {
        return loadLatestMentionAggregate(projectId, platformCode, null);
    }

    private MentionAggregate loadLatestMentionAggregate(Long projectId,
                                                        String platformCode,
                                                        LocalDate asOfDate) {
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND %s IN (%s) ".formatted(POLL_CHANNEL_SQL, aliasSql(normalizeAiPlatformCode(platformCode)));
        }
        String dateClause = asOfDate == null ? "" : " AND pr.batch_date <= ? ";
        List<Object> args = new ArrayList<>(List.of(projectId, MOBILE_QUESTION_TIER));
        if (asOfDate != null) {
            args.add(Date.valueOf(asOfDate));
        }
        return jdbcTemplate.queryForObject(MobileDashboardQuestionScopeSql.apply("""
                WITH latest AS (
                    SELECT pr.id,
                           %1$s AS platform_code,
                           %4$s AS hit_flag,
                           pr.search_triggered,
                           pr.batch_date,
                           pr.updated_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.keyword_result_id, %1$s
                               ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                           ) AS rn
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND ENABLED_MONITORING_QUESTION_SCOPE
                       AND %5$s
                       AND %6$s IN (%2$s)
                       %3$s
                       %7$s
                )
                SELECT COUNT(*) AS requested_count,
                       COALESCE(SUM(CASE WHEN search_triggered = 1 THEN 1 ELSE 0 END), 0) AS completed_count,
                       COALESCE(SUM(hit_flag), 0) AS mention_count,
                       COUNT(DISTINCT CASE WHEN hit_flag > 0 THEN platform_code END) AS covered_platform_count,
                       MAX(batch_date) AS latest_batch_date,
                       MAX(updated_at) AS latest_updated_at
                  FROM latest
                 WHERE rn = 1
                """.formatted(aiPlatformSqlCase(POLL_CHANNEL_SQL), supportedAiPlatformAliasSql(), platformClause,
                WEB_SEARCH_MENTION_SQL, EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL, dateClause), "pr"),
                (rs, rowNum) -> new MentionAggregate(
                        rs.getLong("requested_count"),
                        rs.getLong("completed_count"),
                        rs.getLong("mention_count"),
                        rs.getLong("covered_platform_count"),
                        rs.getDate("latest_batch_date") == null ? null : rs.getDate("latest_batch_date").toLocalDate(),
                        nullableDateTime(rs, "latest_updated_at")
                ), args.toArray());
    }

    private MentionAggregate loadCompleteBatchMentionAggregate(Long projectId, LocalDate completeBatchDate, String platformCode) {
        if (completeBatchDate == null) {
            return new MentionAggregate(0, 0, 0, 0, null, null);
        }
        return loadMentionAggregate(projectId, new DateRange(completeBatchDate, completeBatchDate), platformCode);
    }

    private List<MobileDashboardAggregateVO.TrendPoint> loadMentionTrend(Long projectId, DateRange range) {
        List<MentionSnapshotRow> rows = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT pr.keyword_result_id,
                       %1$s AS platform_code,
                       pr.batch_date,
                       pr.updated_at,
                       %2$s AS hit_flag
                  FROM poll_results pr
                 WHERE pr.project_id = ?
                   AND pr.batch_date <= ?
                   AND pr.status = 'completed'
                   AND pr.question_tier = ?
                   AND pr.keyword_result_id IS NOT NULL
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND %3$s
                   AND %4$s IN (%5$s)
                 ORDER BY pr.batch_date ASC, pr.updated_at ASC, pr.id ASC
                """.formatted(aiPlatformSqlCase(POLL_CHANNEL_SQL), WEB_SEARCH_MENTION_SQL,
                EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL, supportedAiPlatformAliasSql()), "pr"),
                (rs, rowNum) -> new MentionSnapshotRow(
                        rs.getLong("keyword_result_id"),
                        rs.getString("platform_code"),
                        rs.getDate("batch_date").toLocalDate(),
                        rs.getBoolean("hit_flag")
                ), projectId, Date.valueOf(range.end()), MOBILE_QUESTION_TIER);

        Map<MentionSnapshotKey, MentionSnapshotRow> latest = new LinkedHashMap<>();
        List<MobileDashboardAggregateVO.TrendPoint> points = new ArrayList<>();
        int rowIndex = 0;
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            while (rowIndex < rows.size() && !rows.get(rowIndex).batchDate().isAfter(date)) {
                MentionSnapshotRow row = rows.get(rowIndex++);
                latest.put(new MentionSnapshotKey(row.keywordResultId(), row.platformCode()), row);
            }
            long mentions = latest.values().stream().filter(MentionSnapshotRow::mentioned).count();
            MobileDashboardAggregateVO.TrendPoint point = new MobileDashboardAggregateVO.TrendPoint();
            point.setDate(date);
            point.setValue(latest.isEmpty()
                    ? null
                    : percent(mentions, latest.size()));
            points.add(point);
        }
        return points;
    }

    private List<MobileDashboardAggregateVO.PlatformMetric> loadPlatformPerformance(Long projectId, DateRange range) {
        return enrichPlatformMetrics(jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT %1$s AS platform_code,
                       COALESCE(SUM(CASE WHEN pr.search_triggered = 1 THEN 1 ELSE 0 END), 0) AS completed_count,
                       COALESCE(SUM(%2$s), 0) AS mention_count
                  FROM poll_results pr
                 WHERE pr.project_id = ?
                   AND pr.batch_date BETWEEN ? AND ?
                   AND pr.status = 'completed'
                   AND pr.question_tier = ?
                   AND pr.keyword_result_id IS NOT NULL
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND %3$s
                   AND %4$s IN (%5$s)
                 GROUP BY %1$s
                """.formatted(aiPlatformSqlCase(POLL_CHANNEL_SQL), WEB_SEARCH_MENTION_SQL,
                EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL, supportedAiPlatformAliasSql()), "pr"), (rs, rowNum) -> {
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
                .toList());
    }

    private List<MobileDashboardAggregateVO.PlatformMetric> loadLatestPlatformPerformance(Long projectId) {
        return enrichPlatformMetrics(jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                WITH latest AS (
                    SELECT pr.id,
                           %1$s AS platform_code,
                           %3$s AS effective_hit,
                           pr.search_triggered,
                           ROW_NUMBER() OVER (
                               PARTITION BY pr.keyword_result_id, %1$s
                               ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                           ) AS rn
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND ENABLED_MONITORING_QUESTION_SCOPE
                       AND %4$s
                       AND %5$s IN (%2$s)
                )
                SELECT platform_code,
                       COALESCE(SUM(CASE WHEN search_triggered = 1 THEN 1 ELSE 0 END), 0) AS completed_count,
                       COALESCE(SUM(effective_hit), 0) AS mention_count
                  FROM latest
                 WHERE rn = 1
                 GROUP BY platform_code
                """.formatted(aiPlatformSqlCase(POLL_CHANNEL_SQL), supportedAiPlatformAliasSql(), WEB_SEARCH_MENTION_SQL,
                EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL), "pr"), (rs, rowNum) -> {
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
                .toList());
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
                   AND r.polling_enabled = 1
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
        return loadLatestSceneCoverage(projectId, null);
    }

    private List<MobileDashboardAggregateVO.SceneMetric> loadLatestSceneCoverage(Long projectId, String platformCode) {
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND %s IN (%s) ".formatted(POLL_CHANNEL_SQL,
                    aliasSql(normalizeAiPlatformCode(platformCode)));
        }
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
                                       %2$s AS hit_flag,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY pr.project_id, pr.question_tier, pr.keyword_result_id, %3$s
                                           ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                                       ) AS rn
                                  FROM poll_results pr
                                 WHERE pr.status = 'completed'
                                   AND pr.keyword_result_id IS NOT NULL
                                   AND %4$s
                                   AND %5$s IN (%1$s)
                                   %6$s
                          ) latest
                         WHERE rn = 1
                         GROUP BY project_id, question_tier, keyword_result_id
                  ) lbq ON lbq.project_id = rel.project_id
                       AND lbq.question_tier = r.question_tier
                       AND lbq.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                   AND r.polling_enabled = 1
                 GROUP BY COALESCE(r.scene_code, '')
                """.formatted(supportedAiPlatformAliasSql(), WEB_SEARCH_MENTION_SQL,
                aiPlatformSqlCase(POLL_CHANNEL_SQL), EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL,
                platformClause),
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
        return loadLatestQuestionCoverage(projectId, null);
    }

    private QuestionCoverage loadLatestQuestionCoverage(Long projectId, String platformCode) {
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND %s IN (%s) ".formatted(POLL_CHANNEL_SQL,
                    aliasSql(normalizeAiPlatformCode(platformCode)));
        }
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
                                       %2$s AS hit_flag,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY pr.project_id, pr.question_tier, pr.keyword_result_id, %3$s
                                           ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                                       ) AS rn
                                  FROM poll_results pr
                                 WHERE pr.status = 'completed'
                                   AND pr.keyword_result_id IS NOT NULL
                                   AND %4$s
                                   AND %5$s IN (%1$s)
                                   %6$s
                          ) latest
                         WHERE rn = 1
                         GROUP BY project_id, question_tier, keyword_result_id
                  ) lbq ON lbq.project_id = rel.project_id
                       AND lbq.question_tier = r.question_tier
                       AND lbq.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                   AND r.polling_enabled = 1
                """.formatted(supportedAiPlatformAliasSql(), WEB_SEARCH_MENTION_SQL,
                aiPlatformSqlCase(POLL_CHANNEL_SQL), EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL,
                platformClause),
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
            Long count = jdbcTemplate.queryForObject(MobileDashboardQuestionScopeSql.apply("""
                    SELECT COUNT(*)
                      FROM poll_results pr
                     WHERE pr.project_id = ?
                       AND pr.status = 'completed'
                       AND pr.batch_date = ?
                       AND pr.question_tier = ?
                       AND pr.keyword_result_id IS NOT NULL
                       AND ENABLED_MONITORING_QUESTION_SCOPE
                       AND pr.platform_code IN (%s)
                    """.formatted(supportedAiPlatformAliasSql()), "pr"), Long.class,
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
                       AND r.polling_enabled = 1
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
                       AND r.polling_enabled = 1
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
        List<LocalDate> dates = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT s.batch_date
                  FROM poll_keyword_daily_summary s
                 WHERE s.project_id = ?
                   AND s.question_tier = ?
                   AND s.keyword_result_id IS NOT NULL
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                 GROUP BY s.batch_date
                HAVING COUNT(DISTINCT CASE WHEN s.completed_count > 0 THEN s.keyword_result_id END) >= ?
                 ORDER BY s.batch_date DESC
                 LIMIT 1
                """, "s"), (rs, rowNum) -> rs.getDate("batch_date").toLocalDate(),
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
                   AND r.polling_enabled = 1
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
                   AND r.polling_enabled = 1
                """.formatted(summaryDateClause), (rs, rowNum) -> new QuestionCoverage(rs.getLong("covered_count"), rs.getLong("total_count")),
                args.toArray());
    }

    private MobileDashboardAggregateVO.QuestionMonitorList loadQuestionMonitorList(Long projectId,
                                                                                   DateRange range,
                                                                                   MobileDashboardEntityJudgeService.JudgeCoverage focusJudge) {
        boolean judgeReady = entityJudgeService.coverageReady(focusJudge);
        String judgeReason = judgeNotReadyReason(focusJudge);
        List<QuestionMonitorRow> rows = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
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
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND pr.platform_code IN (%s)
                 ORDER BY CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.is_hit = 1) THEN 0 ELSE 1 END,
                          pr.batch_date DESC,
                          pr.updated_at DESC,
                          pr.id DESC
                """.formatted(aiPlatformSqlCase("pr.platform_code"), POLL_RESPONSE_TEXT_SQL, supportedAiPlatformAliasSql()), "pr"), (rs, rowNum) -> {
                    boolean rowJudgeReady = judgeReady && "success".equalsIgnoreCase(rs.getString("judge_status"));
                    return new QuestionMonitorRow(
                            rs.getLong("keyword_result_id"),
                            rs.getLong("poll_result_id"),
                            normalizeAiPlatformCode(rs.getString("platform_code")),
                            rs.getString("question_title"),
                            nullableDateTime(rs, "completed_at"),
                            rs.getBoolean("mentioned"),
                            true,
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
            platformClause = " AND %s IN (%s) ".formatted(POLL_CHANNEL_SQL, aliasSql(normalizeAiPlatformCode(platformCode)));
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
                       AND r.polling_enabled = 1
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
                       AND %5$s
                       AND %6$s IN (%2$s)
                       %3$s
                )
                SELECT qp.keyword_result_id,
                       pr.id AS poll_result_id,
                       COALESCE(%1$s, ?) AS platform_code,
                       COALESCE(NULLIF(pr.keyword_text_snapshot, ''), qp.question_title, CONCAT('问题 #', qp.keyword_result_id)) AS question_title,
                       pr.updated_at AS completed_at,
                       %7$s AS mentioned,
                       pr.search_triggered,
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
                 ORDER BY CASE WHEN pr.effective_hit = 1 THEN 0 ELSE 1 END,
                          pr.batch_date DESC,
                          pr.updated_at DESC,
                          pr.id DESC,
                          qp.sort_order ASC,
                          qp.keyword_result_id ASC
                """.formatted(aiPlatformSqlCase(POLL_CHANNEL_SQL), supportedAiPlatformAliasSql(), platformClause,
                POLL_RESPONSE_TEXT_SQL, EFFECTIVE_WEB_SEARCH_REQUEST_SQL, POLL_CHANNEL_SQL,
                WEB_SEARCH_MENTION_SQL), (rs, rowNum) -> {
            Boolean searchTriggered = nullableBoolean(rs, "search_triggered");
            boolean rowJudgeReady = Boolean.TRUE.equals(searchTriggered)
                    && judgeReady
                    && "success".equalsIgnoreCase(rs.getString("judge_status"));
            return new QuestionMonitorRow(
                    rs.getLong("keyword_result_id"),
                    nullableLong(rs, "poll_result_id"),
                    normalizeAiPlatformCode(rs.getString("platform_code")),
                    rs.getString("question_title"),
                    nullableDateTime(rs, "completed_at"),
                    rs.getBoolean("mentioned"),
                    searchTriggered,
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
        enrichQuestionMonitorItems(items);
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
        List<QuestionMonitorRow> rows = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
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
                       AND ENABLED_MONITORING_QUESTION_SCOPE
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
                """.formatted(aiPlatformSqlCase("pr.platform_code"), supportedAiPlatformAliasSql(), platformClause, POLL_RESPONSE_TEXT_SQL), "pr"), (rs, rowNum) -> {
            boolean rowJudgeReady = judgeReady && "success".equalsIgnoreCase(rs.getString("judge_status"));
            return new QuestionMonitorRow(
                    rs.getLong("keyword_result_id"),
                    rs.getLong("poll_result_id"),
                    normalizeAiPlatformCode(rs.getString("platform_code")),
                    rs.getString("question_title"),
                    nullableDateTime(rs, "completed_at"),
                    rs.getBoolean("mentioned"),
                    true,
                    rowJudgeReady,
                    nullableBoolean(rs, "recommended"),
                    nullableBoolean(rs, "first_recommend"),
                    nullableInt(rs, "rank_position"),
                    rowJudgeReady && StringUtils.hasText(rs.getString("evidence")) ? rs.getString("evidence") : null,
                    boundedText(rs.getString("response_text"), 4000)
            );
        }, projectId, Date.valueOf(completeBatchDate), MOBILE_QUESTION_TIER, MobileDashboardEntityJudgeService.PROMPT_VERSION);
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items = mergeQuestionMonitorRows(rows, judgeReady, judgeReason);
        enrichQuestionMonitorItems(items);
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
            QuestionMonitorRow displayRow = chooseQuestionMonitorDisplayRow(group, first);
            boolean mentioned = group.stream().anyMatch(QuestionMonitorRow::mentioned);
            boolean searchTriggered = group.stream().anyMatch(row -> Boolean.TRUE.equals(row.searchTriggered()));
            boolean hasCompletedResult = group.stream().anyMatch(row -> row.pollResultId() != null);
            boolean hasSuccessfulJudge = group.stream().anyMatch(QuestionMonitorRow::rowJudgeReady);
            boolean recommended = group.stream().anyMatch(row -> row.mentioned()
                    && row.rowJudgeReady()
                    && Boolean.TRUE.equals(row.recommended()));
            boolean firstRecommend = group.stream().anyMatch(row -> row.mentioned()
                    && row.rowJudgeReady()
                    && Boolean.TRUE.equals(row.recommended())
                    && Boolean.TRUE.equals(row.firstRecommend()));
            Integer rank = group.stream()
                    .filter(QuestionMonitorRow::mentioned)
                    .filter(QuestionMonitorRow::rowJudgeReady)
                    .filter(row -> Boolean.TRUE.equals(row.recommended()))
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
            item.setMonitorStatus(mentioned
                    ? MONITOR_STATUS_MENTIONED
                    : (searchTriggered
                    ? MONITOR_STATUS_NOT_MENTIONED
                    : (hasCompletedResult ? MONITOR_STATUS_SEARCH_NOT_TRIGGERED : MONITOR_STATUS_PENDING)));
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

    private QuestionMonitorRow chooseQuestionMonitorDisplayRow(List<QuestionMonitorRow> group, QuestionMonitorRow fallback) {
        return group.stream()
                .min(Comparator
                        .comparingInt(this::questionMonitorDisplayPriority)
                        .thenComparing(
                                QuestionMonitorRow::rankPosition,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(
                                QuestionMonitorRow::completedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(
                                QuestionMonitorRow::pollResultId,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                .orElse(fallback);
    }

    private int questionMonitorDisplayPriority(QuestionMonitorRow row) {
        if (row.mentioned() && row.rowJudgeReady()
                && Boolean.TRUE.equals(row.recommended())
                && Boolean.TRUE.equals(row.firstRecommend())) {
            return 0;
        }
        if (row.mentioned() && row.rowJudgeReady()
                && Boolean.TRUE.equals(row.recommended())
                && row.rankPosition() != null) {
            return 1;
        }
        if (row.mentioned() && row.rowJudgeReady() && Boolean.TRUE.equals(row.recommended())) {
            return 2;
        }
        if (row.mentioned() && StringUtils.hasText(row.platformCode()) && StringUtils.hasText(row.responseText())) {
            return 3;
        }
        if (row.mentioned() && StringUtils.hasText(row.platformCode())) {
            return 4;
        }
        if (Boolean.TRUE.equals(row.searchTriggered())) {
            return 5;
        }
        return row.pollResultId() != null ? 6 : 7;
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

    private void enrichQuestionMonitorItems(List<MobileDashboardAggregateVO.QuestionMonitorItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, PlatformLogoMeta> metaMap = loadAiPlatformLogoMeta();
        for (MobileDashboardAggregateVO.QuestionMonitorItem item : items) {
            if (item == null || !StringUtils.hasText(item.getPlatformCode())) {
                continue;
            }
            PlatformLogoMeta meta = metaMap.get(normalizeAiPlatformCode(item.getPlatformCode()));
            if (meta == null) {
                continue;
            }
            item.setPlatformId(meta.id());
            item.setPlatformLogoUrl(meta.logoUrl());
            item.setPlatformLogoObjectKey(meta.logoObjectKey());
        }
    }

    private List<MobileDashboardAggregateVO.PlatformMetric> enrichPlatformMetrics(List<MobileDashboardAggregateVO.PlatformMetric> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        Map<String, PlatformLogoMeta> metaMap = loadAiPlatformLogoMeta();
        for (MobileDashboardAggregateVO.PlatformMetric row : rows) {
            if (row == null || !StringUtils.hasText(row.getCode())) {
                continue;
            }
            PlatformLogoMeta meta = metaMap.get(normalizeAiPlatformCode(row.getCode()));
            if (meta == null) {
                continue;
            }
            row.setPlatformId(meta.id());
            row.setPlatformLogoUrl(meta.logoUrl());
            row.setPlatformLogoObjectKey(meta.logoObjectKey());
        }
        return rows;
    }

    private Map<String, PlatformLogoMeta> loadAiPlatformLogoMeta() {
        List<PlatformLogoMeta> rows = jdbcTemplate.query("""
                SELECT id,
                       platform_code,
                       platform_logo_url,
                       platform_logo_object_key
                  FROM ai_platform_config
                 WHERE platform_code IS NOT NULL
                 ORDER BY CASE WHEN enabled = 1 THEN 0 ELSE 1 END, id ASC
                """, (rs, rowNum) -> new PlatformLogoMeta(
                rs.getLong("id"),
                normalizeAiPlatformCode(rs.getString("platform_code")),
                rs.getString("platform_logo_url"),
                rs.getString("platform_logo_object_key")
        ));
        Map<String, PlatformLogoMeta> metaMap = new HashMap<>();
        for (PlatformLogoMeta row : rows) {
            if (!StringUtils.hasText(row.code())) {
                continue;
            }
            metaMap.putIfAbsent(row.code(), row);
        }
        return metaMap;
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
                .filter(MobileDashboardContentChannelCatalog.canonicalCodes()::contains)
                .distinct()
                .sorted(Comparator.comparingInt(MobileDashboardContentChannelCatalog.canonicalCodes()::indexOf))
                .toList();
        return channels.isEmpty() ? MobileDashboardContentChannelCatalog.canonicalCodes() : channels;
    }

    private List<String> loadBoundContentChannels(Long projectId, List<String> configuredChannels) {
        if (projectId == null || configuredChannels == null || configuredChannels.isEmpty()) {
            return List.of();
        }
        Set<String> boundChannels = jdbcTemplate.query("""
                SELECT DISTINCT sma.platform
                  FROM project p
                  JOIN self_media_account sma ON sma.brand_id = p.brand_id
                 WHERE p.id = ?
                   AND sma.status = 'active'
                   AND sma.deleted_at IS NULL
                """, (rs, rowNum) -> normalizeSelfMediaAccountChannel(rs.getString("platform")), projectId).stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        return configuredChannels.stream()
                .filter(boundChannels::contains)
                .toList();
    }

    private String normalizeSelfMediaAccountChannel(String platform) {
        String quotaPlatform = ArticlePromptChannels.normalizeSelfMediaQuotaPlatform(platform);
        return normalizeContentChannelCode(StringUtils.hasText(quotaPlatform) ? quotaPlatform : platform);
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
        return loadOwnedPublish(facts, MobileDashboardContentChannelCatalog.canonicalCodes());
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
        return loadContentTaskList(projectId, range, 1, 4);
    }

    private MobileDashboardAggregateVO.TaskList loadContentTaskList(Long projectId, DateRange range, Integer page, Integer size) {
        int resolvedSize = Math.max(1, Math.min(size == null ? 4 : size, 20));
        int requestedPage = Math.max(1, page == null ? 1 : page);
        String publicationAggregateSql = contentTaskPublicationAggregateSql();
        Long totalValue = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT ad.id)
                  FROM article_draft ad
                  LEFT JOIN (%s) pub ON pub.article_id = ad.id
                 WHERE ad.project_id = ?
                   AND DATE(COALESCE(pub.latest_publish_at, ad.updated_at, ad.created_at)) BETWEEN ? AND ?
                   AND (
                        COALESCE(pub.visible_count, 0) > 0
                        OR (
                            %s
                            AND COALESCE(ad.target_channel, '') IN (%s)
                        )
                   )
                """.formatted(publicationAggregateSql, deliveryDraftPredicate("ad"), contentDetailChannelSql()),
                Long.class, projectId, projectId, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()));
        int total = totalValue == null ? 0 : Math.toIntExact(totalValue);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) resolvedSize);
        int resolvedPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
        int offset = total == 0 ? 0 : (resolvedPage - 1) * resolvedSize;

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
                  LEFT JOIN (%s) pub ON pub.article_id = ad.id
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
                 LIMIT ? OFFSET ?
                """.formatted(publicationAggregateSql, deliveryDraftPredicate("ad"), contentDetailChannelSql()), (rs, rowNum) -> {
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
                }, projectId, projectId, projectId, Date.valueOf(range.start()), Date.valueOf(range.end()), resolvedSize, offset);
        MobileDashboardAggregateVO.TaskList list = new MobileDashboardAggregateVO.TaskList();
        list.setPage(resolvedPage);
        list.setSize(resolvedSize);
        list.setTotal(total);
        list.setTotalPages(totalPages);
        list.setItems(items);
        list.setAvailable(!items.isEmpty());
        if (items.isEmpty()) {
            list.setReason("暂无内容任务数据");
        }
        return list;
    }

    private String contentTaskPublicationAggregateSql() {
        return """
                SELECT article_id,
                       GROUP_CONCAT(DISTINCT COALESCE(target_channel, target_kind, '') ORDER BY COALESCE(target_channel, target_kind, '') SEPARATOR ',') AS platform_codes,
                       COUNT(DISTINCT source_key) AS visible_count,
                       COUNT(DISTINCT CASE
                           WHEN COALESCE(target_channel, target_kind, '') IN (%s)
                            AND (
                                COALESCE(target_channel, target_kind, '') IN (%s)
                                OR verified_at IS NOT NULL
                            )
                           THEN source_key END) AS indexed_count,
                       MAX(COALESCE(published_at, verified_at, created_at)) AS latest_publish_at,
                       SUBSTRING_INDEX(
                           GROUP_CONCAT(
                               %s
                               ORDER BY COALESCE(published_at, verified_at, created_at) DESC, source_key DESC
                               SEPARATOR '\n'
                           ),
                           '\n',
                           1
                       ) AS publish_url
                  FROM (
                        SELECT CONCAT('record:', id) AS source_key,
                               article_id,
                               target_kind,
                               target_channel,
                               published_url,
                               url_quality,
                               published_at,
                               verified_at,
                               created_at
                          FROM article_publish_record
                         WHERE project_id = ?
                           AND publish_status IN (%s)
                           AND COALESCE(target_channel, target_kind, '') IN (%s)
                        UNION ALL
                        SELECT CONCAT('self_media_schedule:', s.id) AS source_key,
                               s.article_id,
                               'self_media' AS target_kind,
                               s.platform AS target_channel,
                               NULLIF(TRIM(s.platform_published_url), '') AS published_url,
                               CASE
                                 WHEN NULLIF(TRIM(s.platform_published_url), '') REGEXP '^https?://' THEN 'public_url'
                                 WHEN NULLIF(TRIM(s.platform_published_url), '') IS NOT NULL THEN 'manage_url'
                                 ELSE 'missing'
                               END AS url_quality,
                               COALESCE(s.published_confirmed_at, s.updated_at, s.created_at) AS published_at,
                               COALESCE(s.published_confirmed_at, s.updated_at, s.created_at) AS verified_at,
                               s.created_at
                          FROM self_media_publish_schedule s
                          JOIN article_draft sad ON sad.id = s.article_id
                         WHERE sad.project_id = ?
                           AND s.status = 'published_confirmed'
                           AND s.platform IN (%s)
                       ) publication
                 GROUP BY article_id
                """.formatted(quoted(MEASURABLE_INDEX_CHANNELS), SELF_INDEX_CHANNEL_SQL, PUBLIC_CONTENT_PUBLISH_URL_SQL,
                CURRENT_VISIBLE_PUBLISH_STATUS_SQL, contentDetailChannelSql(), contentDetailChannelSql());
    }

    private List<MobileDashboardAggregateVO.ContentTaskItem> loadRelatedBuildingContentTasks(Long projectId, Long keywordResultId) {
        if (keywordResultId == null || keywordResultId <= 0) {
            return List.of();
        }
        String questionScene = jdbcTemplate.query("""
                SELECT scene_code
                  FROM keyword_group_result
                 WHERE id = ?
                """, rs -> rs.next() ? normalizeSceneCode(rs.getString("scene_code")) : "", keywordResultId);
        if (!StringUtils.hasText(questionScene)) {
            return List.of();
        }

        List<RelatedContentTaskCandidate> candidates = jdbcTemplate.query("""
                SELECT ad.id,
                       ad.title,
                       ad.topic,
                       ad.topic_as_question,
                       ad.target_channel,
                       ad.updated_at,
                       ad.created_at,
                       t.question_scene_code
                  FROM article_draft ad
                  JOIN article_prompt_template t ON t.id = ad.prompt_template_id
                 WHERE ad.project_id = ?
                   AND t.question_scene_code IS NOT NULL
                   AND t.question_scene_code <> ''
                   AND %s
                   AND COALESCE(ad.target_channel, '') IN (%s)
                   AND NOT EXISTS (
                        SELECT 1
                          FROM article_publish_record pr
                         WHERE pr.article_id = ad.id
                           AND pr.project_id = ad.project_id
                           AND pr.publish_status IN (%s)
                   )
                 ORDER BY COALESCE(ad.updated_at, ad.created_at) DESC, ad.id DESC
                 LIMIT 12
                """.formatted(deliveryDraftPredicate("ad"), contentDetailChannelSql(), CURRENT_VISIBLE_PUBLISH_STATUS_SQL), (rs, rowNum) -> {
                    MobileDashboardAggregateVO.ContentTaskItem item = new MobileDashboardAggregateVO.ContentTaskItem();
                    item.setDraftId(rs.getLong("id"));
                    item.setTitle(rs.getString("title"));
                    item.setKeywords(taskKeywords(rs.getString("topic_as_question"), rs.getString("topic")));
                    item.setPlatformCodes(taskPlatformCodes(null, rs.getString("target_channel")));
                    item.setStatus("building");
                    LocalDateTime date = nullableDateTime(rs, "updated_at");
                    if (date == null) {
                        date = nullableDateTime(rs, "created_at");
                    }
                    item.setDate(date);
                    return new RelatedContentTaskCandidate(normalizeSceneCode(rs.getString("question_scene_code")), item);
                }, projectId);
        return candidates.stream()
                .filter(candidate -> questionScene.equals(candidate.sceneCode()))
                .map(RelatedContentTaskCandidate::item)
                .filter(item -> item.getPlatformCodes() != null && !item.getPlatformCodes().isEmpty())
                .limit(3)
                .toList();
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
                                       %2$s AS hit_flag,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY pr.project_id, pr.question_tier, pr.keyword_result_id, %3$s
                                           ORDER BY pr.batch_date DESC, pr.updated_at DESC, pr.id DESC
                                       ) AS rn
                                  FROM poll_results pr
                                 WHERE pr.status = 'completed'
                                   AND pr.keyword_result_id IS NOT NULL
                                   AND %4$s
                                   AND %3$s IN (%1$s)
                          ) latest
                         WHERE rn = 1
                         GROUP BY project_id, question_tier, keyword_result_id
                  ) lbq ON lbq.project_id = rel.project_id
                       AND lbq.question_tier = r.question_tier
                       AND lbq.keyword_result_id = r.id
                 WHERE rel.project_id = ?
                   AND COALESCE(kg.deleted, 0) = 0
                   AND r.question_tier = ?
                   AND r.polling_enabled = 1
                """.formatted(supportedAiPlatformAliasSql(), WEB_SEARCH_MENTION_SQL, POLL_CHANNEL_SQL,
                EFFECTIVE_WEB_SEARCH_RESULT_SQL),
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
        vo.setMonthNew(MobileDashboardMetricVO.available(facts.monthContent()));
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
        return MobileDashboardMetricVO.available(percent(coverage.recommendedCount(), coverage.expectedCount()), "%");
    }

    private MobileDashboardAggregateVO.MeasurementMeta toMeasurementMeta(
            MentionAggregate mention,
            MobileDashboardEntityJudgeService.JudgeCoverage coverage) {
        MobileDashboardAggregateVO.MeasurementMeta meta = new MobileDashboardAggregateVO.MeasurementMeta();
        meta.setRequestedCount(mention.requested());
        meta.setTriggeredCount(mention.completed());
        meta.setMentionCount(mention.mentions());
        meta.setSearchTriggerRate(rateMetric(mention.completed(), mention.requested()));
        long expected = coverage == null ? 0 : coverage.expectedCount();
        long success = coverage == null ? 0 : coverage.successCount();
        meta.setJudgeExpectedCount(expected);
        meta.setJudgeSuccessCount(success);
        meta.setJudgeCoverageRate(rateMetric(success, expected));
        meta.setLatestBatchDate(mention.latestBatchDate());
        meta.setUpdatedAt(mention.latestUpdatedAt());
        return meta;
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

    private String safePublicSourceUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!("http".equals(scheme) || "https".equals(scheme))
                    || !StringUtils.hasText(host)
                    || StringUtils.hasText(uri.getUserInfo())
                    || !isPublicSourceHost(host)) {
                return null;
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String sourceDomain(String url) {
        try {
            String host = URI.create(url).getHost();
            return boundedText(host == null ? null : host.replaceFirst("(?i)^www\\.", ""), 120);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String sourceUrlDedupKey(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
        String query = canonicalSourceQuery(uri.getRawQuery());
        try {
            return new URI(scheme, null, host, port, path, query, null).normalize().toString();
        } catch (Exception ex) {
            return value;
        }
    }

    private String canonicalSourceQuery(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return null;
        }
        String query = Arrays.stream(rawQuery.split("&"))
                .filter(StringUtils::hasText)
                .filter(parameter -> !isTrackingQueryParameter(parameter))
                .sorted()
                .collect(java.util.stream.Collectors.joining("&"));
        return StringUtils.hasText(query) ? query : null;
    }

    private boolean isTrackingQueryParameter(String parameter) {
        String name = parameter.split("=", 2)[0].toLowerCase(Locale.ROOT);
        return name.startsWith("utm_")
                || Set.of("spm", "gclid", "fbclid", "msclkid", "mc_cid", "mc_eid", "_hsenc", "_hsmi")
                .contains(name);
    }

    private boolean isPublicSourceHost(String value) {
        String host = value.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
            return false;
        }
        if (host.contains(":")) {
            return !("::".equals(host)
                    || "::1".equals(host)
                    || host.startsWith("fe8")
                    || host.startsWith("fe9")
                    || host.startsWith("fea")
                    || host.startsWith("feb")
                    || host.startsWith("fc")
                    || host.startsWith("fd"));
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4 || Arrays.stream(parts).anyMatch(part -> !part.matches("\\d{1,3}"))) {
            return true;
        }
        int[] octets;
        try {
            octets = Arrays.stream(parts).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException ex) {
            return false;
        }
        if (Arrays.stream(octets).anyMatch(octet -> octet < 0 || octet > 255)) {
            return false;
        }
        return octets[0] != 0
                && octets[0] != 10
                && octets[0] != 127
                && !(octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127)
                && !(octets[0] == 169 && octets[1] == 254)
                && !(octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                && !(octets[0] == 192 && octets[1] == 168)
                && !(octets[0] == 198 && (octets[1] == 18 || octets[1] == 19))
                && octets[0] < 224;
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
        return "ernie".equals(value) ? "wenxin" : aiPlatformCatalog.canonicalCode(value);
    }

    private String normalizeContentChannelCode(String code) {
        return MobileDashboardContentChannelCatalog.normalize(code);
    }

    private String contentDetailChannelSql() {
        return MobileDashboardContentChannelCatalog.quotedSql(MobileDashboardContentChannelCatalog.detailChannelCodesWithAliases());
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
        return aiPlatformCatalog.order(code);
    }

    private int metricValue(MobileDashboardMetricVO<Integer> metric) {
        return metric != null && metric.isAvailable() && metric.getValue() != null ? metric.getValue() : -1;
    }

    private String aliasSql(String normalizedCode) {
        if ("wenxin".equals(normalizedCode)) {
            return "'wenxin','ernie'";
        }
        return aiPlatformCatalog.aliasSql(normalizedCode);
    }

    private String quoted(Collection<String> values) {
        return values.stream().map(v -> "'" + v.replace("'", "''") + "'").reduce((a, b) -> a + "," + b).orElse("''");
    }

    private String supportedAiPlatformAliasSql() {
        return aiPlatformCatalog.scope().aliasSql();
    }

    private String aiPlatformSqlCase(String expression) {
        return aiPlatformCatalog.canonicalSql(expression);
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

    private record MentionAggregate(long requested,
                                    long completed,
                                    long mentions,
                                    long coveredPlatformCount,
                                    LocalDate latestBatchDate,
                                    LocalDateTime latestUpdatedAt) {
    }

    private record MentionSnapshotKey(long keywordResultId, String platformCode) {
    }

    private record MentionSnapshotRow(long keywordResultId,
                                      String platformCode,
                                      LocalDate batchDate,
                                      boolean mentioned) {
    }

    private record QuestionCoverage(long covered, long total) {
    }

    private record SceneRow(String code, long covered, long total) {
    }

    private record QuestionSceneCoverageRow(Long keywordResultId, String sceneCode, boolean covered) {
    }

    private record RelatedContentTaskCandidate(String sceneCode, MobileDashboardAggregateVO.ContentTaskItem item) {
    }

    private record PlatformLogoMeta(Long id, String code, String logoUrl, String logoObjectKey) {
    }

    private record QuestionMonitorRow(Long keywordResultId,
                                      Long pollResultId,
                                      String platformCode,
                                      String questionTitle,
                                      LocalDateTime completedAt,
                                      boolean mentioned,
                                      Boolean searchTriggered,
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
