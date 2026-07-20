package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardAggregateVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MobileDashboardAggregateServiceTest {

    @Test
    void judgeRecommendationRateUsesExpectedWebSamplesAsDenominator() throws Exception {
        MobileDashboardEntityJudgeService judgeService = mock(MobileDashboardEntityJudgeService.class);
        MobileDashboardEntityJudgeService.JudgeCoverage coverage =
                new MobileDashboardEntityJudgeService.JudgeCoverage(10, 8, 4, 2);
        when(judgeService.coverageReady(coverage)).thenReturn(true);
        MobileDashboardAggregateService service = new MobileDashboardAggregateService(mock(JdbcTemplate.class), judgeService);

        @SuppressWarnings("unchecked")
        com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardMetricVO<Integer> metric =
                (com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardMetricVO<Integer>)
                        invoke(service, "judgeRateMetric", coverage);

        assertThat(metric.getValue()).isEqualTo(40);
    }

    @Test
    void mentionAggregateUsesTriggeredWebEffectiveHitInsteadOfExactBrandMatch() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, search_triggered, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao_web', 'doubao', DATE '2026-06-10', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-10 09:00:00'),
                    (2, 1, 1002, 'deepseek_ark_web', 'deepseek', DATE '2026-06-10', 'A', 'completed', 0, 1, 1, TIMESTAMP '2026-06-10 09:01:00'),
                    (3, 1, 1003, 'qwen_web', 'tongyi', DATE '2026-06-20', 'A', 'completed', 1, 0, 1, TIMESTAMP '2026-06-20 09:00:00'),
                    (4, 1, 1004, 'qwen_web', 'tongyi', DATE '2026-06-20', 'B', 'completed', 1, 1, 1, TIMESTAMP '2026-06-20 09:01:00')
                """);

        Object aggregate = invoke(newService(jdbcTemplate), "loadMentionAggregate",
                1L, dateRange(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 21)), null);

        assertThat(recordValue(aggregate, "completed")).isEqualTo(2L);
        assertThat(recordValue(aggregate, "mentions")).isEqualTo(1L);
        assertThat(recordValue(aggregate, "coveredPlatformCount")).isEqualTo(1L);
    }

    @Test
    void mentionTrendUsesQueryCountAsDenominatorAndNullForMissingDays() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, search_triggered, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao_web', 'doubao', DATE '2026-06-10', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-10 09:00:00'),
                    (2, 1, 1002, 'deepseek_ark_web', 'deepseek', DATE '2026-06-10', 'A', 'completed', 0, 1, 1, TIMESTAMP '2026-06-10 09:01:00'),
                    (3, 1, 1003, 'qwen_web', 'tongyi', DATE '2026-06-12', 'A', 'completed', 1, 0, 1, TIMESTAMP '2026-06-12 09:00:00'),
                    (4, 1, 1004, 'qwen_web', 'tongyi', DATE '2026-06-13', 'A', 'completed', 0, 1, 1, TIMESTAMP '2026-06-13 09:00:00')
                """);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.TrendPoint> points =
                (List<MobileDashboardAggregateVO.TrendPoint>) invoke(newService(jdbcTemplate), "loadMentionTrend",
                        1L, dateRange(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 13)));

        assertThat(points).extracting(MobileDashboardAggregateVO.TrendPoint::getValue)
                .containsExactly(50, null, 0, 0);
    }

    @Test
    void publishedAndIndexedCountsExcludeOfflineAndOnlyCountMeasuredIndexedChannels() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createPublishRecordTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (id, project_id, article_id, publish_status, target_channel, target_kind, published_at, verified_at, created_at)
                VALUES
                    (1, 1, 101, 'published', 'official_site', NULL, TIMESTAMP '2026-06-03 09:00:00', NULL, TIMESTAMP '2026-06-03 09:00:00'),
                    (2, 1, 102, 'offline', 'official_site', NULL, TIMESTAMP '2026-06-04 09:00:00', TIMESTAMP '2026-06-04 09:30:00', TIMESTAMP '2026-06-04 09:00:00'),
                    (3, 1, 103, 'published_confirmed', 'douyin', NULL, TIMESTAMP '2026-06-05 09:00:00', NULL, TIMESTAMP '2026-06-05 09:00:00'),
                    (4, 1, 104, 'distributed', 'douyin', NULL, TIMESTAMP '2026-06-06 09:00:00', TIMESTAMP '2026-06-06 10:00:00', TIMESTAMP '2026-06-06 09:00:00'),
                    (5, 1, 105, 'distributed', 'unmeasured_channel', NULL, TIMESTAMP '2026-06-07 09:00:00', TIMESTAMP '2026-06-07 10:00:00', TIMESTAMP '2026-06-07 09:00:00')
                """);
        Object range = dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        assertThat(invoke(service, "countPublished", 1L, range)).isEqualTo(4L);
        assertThat(invoke(service, "countIndexed", 1L, range)).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        Map<String, Long> publishedByChannel = (Map<String, Long>) invoke(service, "loadPublishedByChannel", 1L, range);
        @SuppressWarnings("unchecked")
        Map<String, Long> indexedByChannel = (Map<String, Long>) invoke(service, "loadIndexedByChannel", 1L, range);

        assertThat(publishedByChannel).containsEntry("official_site", 1L).containsEntry("douyin", 2L);
        assertThat(indexedByChannel).containsEntry("official_site", 1L).containsEntry("douyin", 1L);
        assertThat(publishedByChannel).doesNotContainEntry("official_site", 2L);
    }

    @Test
    void platformCompletionKeepsDisplayablePublishedCountWhenMonthlyChannelQuotaIsMissing() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createProjectChannelAllocationTable(jdbcTemplate);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.PlatformCompletion> rows =
                (List<MobileDashboardAggregateVO.PlatformCompletion>) ReflectionTestUtils.invokeMethod(
                        service, "loadPlatformCompletion", 1L, Map.of("douyin", 5L, "xiaohongshu", 3L));

        assertThat(rows).extracting(MobileDashboardAggregateVO.PlatformCompletion::getCode)
                .containsExactly("douyin", "xiaohongshu");
        MobileDashboardAggregateVO.PlatformCompletion xiaohongshu = rows.get(1);
        assertThat(xiaohongshu.getCode()).isEqualTo("xiaohongshu");
        assertThat(xiaohongshu.getPublished()).isEqualTo(3L);
        assertThat(xiaohongshu.getQuota()).isZero();
        assertThat(xiaohongshu.getCompletionRate().isAvailable()).isFalse();
        assertThat(xiaohongshu.getCompletionRate().getReason()).contains("暂无逐渠道月度配额");
    }

    @Test
    void contentChannelsComeFromProjectPackageAllocationWhenConfigured() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createProjectChannelAllocationTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO project_channel_allocation
                    (project_id, channel_code, period_type_snapshot, allocated_count)
                VALUES
                    (1, 'self_media:zhihu', 'month', 4),
                    (1, 'self_media:toutiao', 'month', 2),
                    (1, 'self_media:xiaohongshu', 'week', 9),
                    (1, 'self_media:baijiahao', 'month', 0),
                    (2, 'self_media:wechat', 'month', 3)
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) ReflectionTestUtils.invokeMethod(service,
                "loadConfiguredContentChannels", 1L);
        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.PlatformCompletion> completion =
                (List<MobileDashboardAggregateVO.PlatformCompletion>) ReflectionTestUtils.invokeMethod(
                        service, "loadPlatformCompletion", 1L,
                        Map.of("zhihu", 1L, "toutiao", 2L, "xiaohongshu", 7L),
                        channels);

        assertThat(channels).containsExactly("toutiao", "zhihu");
        assertThat(completion).extracting(MobileDashboardAggregateVO.PlatformCompletion::getCode)
                .containsExactly("toutiao", "zhihu");
        assertThat(completion.get(0).getQuota()).isEqualTo(2L);
        assertThat(completion.get(0).getCompletionRate().getValue()).isEqualTo(100);
    }

    @Test
    void visibleContentChannelsAreIntersectionOfPackageAndActiveBrandAccounts() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createProjectAndSelfMediaAccountTables(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO project (id, brand_id) VALUES (1, 10), (2, 20)");
        jdbcTemplate.update("""
                INSERT INTO self_media_account (id, brand_id, platform, status, deleted_at)
                VALUES
                    (1, 10, 'douyin_image_text', 'active', NULL),
                    (2, 10, 'wechat', 'active', NULL),
                    (3, 10, 'toutiao', 'disabled', NULL),
                    (4, 10, 'zhihu', 'active', TIMESTAMP '2026-07-01 00:00:00'),
                    (5, 20, 'baijiahao', 'active', NULL),
                    (6, 10, 'xiaohongshu', 'active', NULL)
                """);

        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) ReflectionTestUtils.invokeMethod(newService(jdbcTemplate),
                "loadBoundContentChannels", 1L,
                List.of("douyin", "wechat_mp", "toutiao", "baijiahao", "zhihu"));

        assertThat(channels).containsExactly("douyin", "wechat_mp");
    }

    @Test
    void monthContentCountsOnlyDeliveryFlowDraftsAndVisiblePublishedContent() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT,
                    project_id BIGINT,
                    status VARCHAR(32),
                    allocation_mode VARCHAR(16),
                    created_at TIMESTAMP
                )
                """);
        createPublishRecordTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO article_draft (id, project_id, status, allocation_mode, created_at)
                VALUES
                    (101, 1, 'pending_review', NULL, TIMESTAMP '2026-06-03 09:00:00'),
                    (102, 1, 'approved', 'auto', TIMESTAMP '2026-06-04 09:00:00'),
                    (103, 1, 'rejected', NULL, TIMESTAMP '2026-06-05 09:00:00'),
                    (104, 1, 'approved', NULL, TIMESTAMP '2026-05-30 09:00:00'),
                    (105, 2, 'approved', NULL, TIMESTAMP '2026-06-06 09:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (id, project_id, article_id, publish_status, target_channel, target_kind, published_at, verified_at, created_at)
                VALUES
                    (1, 1, 104, 'published_confirmed', 'official_site', NULL, TIMESTAMP '2026-06-08 09:00:00', NULL, TIMESTAMP '2026-06-08 09:00:00')
                """);

        Object count = invoke(newService(jdbcTemplate), "countMonthContent", 1L,
                dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void ecoAssetsMonthNewUsesMonthContentInsteadOfMonthPublished() throws Exception {
        MobileDashboardAggregateService service = newService(jdbcTemplate());
        Object facts = contentFacts(24L, 24L, 46L, 22L, 24L, 24L);

        MobileDashboardAggregateVO.EcoAssets ecoAssets =
                (MobileDashboardAggregateVO.EcoAssets) invoke(service, "toEcoAssets", facts, 12L);

        assertThat(ecoAssets.getTotalAssets().getValue()).isEqualTo(24L);
        assertThat(ecoAssets.getMonthNew().getValue()).isEqualTo(46L);
        assertThat(ecoAssets.getIndexed().getValue()).isEqualTo(24L);
    }

    @Test
    void cumulativeQuestionCoverageUsesOnlyTierAAndDoesNotDriftWithPageWindow() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES
                    (1001, 10, 'brand_awareness', 'A'),
                    (1002, 10, 'qa', 'A'),
                    (1003, 10, 'qa', 'B')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_keyword_daily_summary
                    (project_id, keyword_result_id, batch_date, question_tier, hit_count, effective_hit_count)
                VALUES
                    (1, 1001, DATE '2026-05-20', 'A', 1, 0),
                    (1, 1002, DATE '2026-06-20', 'A', 0, 1),
                    (1, 1003, DATE '2026-06-20', 'B', 1, 0)
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        Object cumulative = invoke(service, "loadCumulativeQuestionCoverage", 1L);
        Object juneWindow = invoke(service, "loadQuestionCoverage", 1L,
                dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        assertThat(recordValue(cumulative, "total")).isEqualTo(2L);
        assertThat(recordValue(cumulative, "covered")).isEqualTo(2L);
        assertThat(recordValue(juneWindow, "total")).isEqualTo(2L);
        assertThat(recordValue(juneWindow, "covered")).isEqualTo(1L);
    }

    @Test
    void completeBatchCoverageIgnoresNewerPartialPollRefresh() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES
                    (1001, 10, 'brand_awareness', 'A'),
                    (1002, 10, 'qa', 'A')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_keyword_daily_summary
                    (project_id, keyword_result_id, batch_date, question_tier, completed_count, hit_count, effective_hit_count)
                VALUES
                    (1, 1001, DATE '2026-06-20', 'A', 1, 0, 1),
                    (1, 1002, DATE '2026-06-20', 'A', 1, 1, 0),
                    (1, 1001, DATE '2026-06-21', 'A', 1, 0, 0)
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        Object completeBatchDate = invoke(service, "loadLatestCompletePollBatchDate", 1L);
        Object coverage = invoke(service, "loadCompleteBatchQuestionCoverage", 1L, completeBatchDate);

        assertThat(completeBatchDate).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(recordValue(coverage, "total")).isEqualTo(2L);
        assertThat(recordValue(coverage, "covered")).isEqualTo(2L);
    }

    @Test
    void completeBatchCoveragePrefersPollResultEffectiveHitOverSummaryRawHit() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES
                    (1001, 10, 'brand_awareness', 'A'),
                    (1002, 10, 'qa', 'A')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_keyword_daily_summary
                    (project_id, keyword_result_id, batch_date, question_tier, completed_count, hit_count, effective_hit_count)
                VALUES
                    (1, 1001, DATE '2026-06-20', 'A', 1, 1, 0),
                    (1, 1002, DATE '2026-06-20', 'A', 1, 1, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, keyword_text_snapshot, platform_code, batch_date, question_tier, status, effective_hit, updated_at)
                VALUES
                    (1, 1, 1001, 'q1', 'doubao', DATE '2026-06-20', 'A', 'completed', 0, TIMESTAMP '2026-06-20 09:00:00'),
                    (2, 1, 1002, 'q2', 'doubao', DATE '2026-06-20', 'A', 'completed', 1, TIMESTAMP '2026-06-20 09:00:00')
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        LocalDate completeBatchDate = LocalDate.of(2026, 6, 20);
        assertThat(invoke(service, "hasCompleteBatchPollResults", 1L, completeBatchDate)).isEqualTo(true);
        Object coverage = invoke(service, "loadCompleteBatchQuestionCoverage", 1L, completeBatchDate);
        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.SceneMetric> scenes =
                (List<MobileDashboardAggregateVO.SceneMetric>) invoke(service, "loadCompleteBatchSceneCoverage",
                        1L, completeBatchDate);

        assertThat(recordValue(coverage, "total")).isEqualTo(2L);
        assertThat(recordValue(coverage, "covered")).isEqualTo(1L);
        assertThat(scenes).filteredOn(row -> "brand_awareness".equals(row.getCode()))
                .singleElement()
                .satisfies(row -> assertThat(row.getCovered().getValue()).isEqualTo(0L));
        assertThat(scenes).filteredOn(row -> "qa".equals(row.getCode()))
                .singleElement()
                .satisfies(row -> assertThat(row.getCovered().getValue()).isEqualTo(1L));
    }

    @Test
    void completeBatchMentionAggregateUsesTriggeredWebResultsFromRequestedDate() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createAiPlatformConfigTable(jdbcTemplate);
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, search_triggered, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-20 09:00:00'),
                    (2, 1, 1002, 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 0, 1, 1, TIMESTAMP '2026-06-20 09:01:00'),
                    (3, 1, 1003, 'deepseek_ark_web', 'deepseek', DATE '2026-06-20', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-20 09:02:00'),
                    (4, 1, 1004, 'deepseek_ark_web', 'deepseek', DATE '2026-06-21', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-21 09:00:00')
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        Object aggregate = invoke(service, "loadCompleteBatchMentionAggregate", 1L, LocalDate.of(2026, 6, 20), null);
        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.PlatformMetric> rows =
                (List<MobileDashboardAggregateVO.PlatformMetric>) invoke(service, "loadCompleteBatchPlatformPerformance",
                        1L, LocalDate.of(2026, 6, 20));

        assertThat(recordValue(aggregate, "completed")).isEqualTo(3L);
        assertThat(recordValue(aggregate, "mentions")).isEqualTo(2L);
        assertThat(rows).extracting(MobileDashboardAggregateVO.PlatformMetric::getCode)
                .containsExactly("deepseek", "doubao");
        assertThat(rows.get(0).getRate().getValue()).isEqualTo(100);
        assertThat(rows.get(1).getRate().getValue()).isEqualTo(50);
    }

    @Test
    void platformPerformanceSortsByRateDescendingThenStableAiPlatformOrder() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createAiPlatformConfigTable(jdbcTemplate);
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, search_triggered, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 0, 1, 0, TIMESTAMP '2026-06-20 09:00:00'),
                    (2, 1, 1002, 'deepseek_ark_web', 'deepseek', DATE '2026-06-20', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-20 09:01:00'),
                    (3, 1, 1003, 'qwen_web', 'tongyi', DATE '2026-06-20', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-20 09:02:00'),
                    (4, 1, 1004, 'wenxin_web', 'wenxin', DATE '2026-06-20', 'A', 'completed', 1, 1, 0, TIMESTAMP '2026-06-20 09:03:00')
                """);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.PlatformMetric> rows =
                (List<MobileDashboardAggregateVO.PlatformMetric>) invoke(newService(jdbcTemplate), "loadPlatformPerformance",
                        1L, dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        assertThat(rows).extracting(MobileDashboardAggregateVO.PlatformMetric::getCode)
                .containsExactly("deepseek", "tongyi", "doubao");
    }

    @Test
    void latestMentionAggregateUsesOnlyNewestCompletedResultPerQuestionAndPlatform() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, keyword_text_snapshot, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, 'q1', 'doubao_web', 'doubao', DATE '2026-06-10', 'A', 'completed', 1, 1, TIMESTAMP '2026-06-10 09:00:00'),
                    (2, 1, 1001, 'q1', 'doubao_web', 'doubao', DATE '2026-06-17', 'A', 'completed', 0, 0, TIMESTAMP '2026-06-17 09:00:00'),
                    (3, 1, 1001, 'q1', 'qwen_web', 'tongyi', DATE '2026-06-17', 'A', 'completed', 1, 1, TIMESTAMP '2026-06-17 09:00:00'),
                    (4, 1, 1002, 'q2', 'tencent_search_web', 'yuanbao', DATE '2026-06-17', 'A', 'completed', 1, 0, TIMESTAMP '2026-06-17 09:00:00'),
                    (5, 1, 1002, 'q2', 'ernie', 'wenxin', DATE '2026-06-17', 'A', 'completed', 1, 1, TIMESTAMP '2026-06-17 09:00:00')
                """);

        Object aggregate = invoke(newService(jdbcTemplate), "loadLatestMentionAggregate", 1L, null);

        assertThat(recordValue(aggregate, "completed")).isEqualTo(3L);
        assertThat(recordValue(aggregate, "mentions")).isEqualTo(2L);
        assertThat(recordValue(aggregate, "coveredPlatformCount")).isEqualTo(2L);
    }

    @Test
    void latestMentionAggregateDoesNotFallBackToOlderTriggeredResult() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, search_requested, search_triggered, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao_web', NULL, DATE '2026-06-10', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-10 09:00:00'),
                    (2, 1, 1001, 'doubao_web', NULL, DATE '2026-06-17', 'A', 'completed', 0, 1, 0, TIMESTAMP '2026-06-17 09:00:00'),
                    (3, 1, 1002, 'tencent_search_web', NULL, DATE '2026-06-17', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-17 09:01:00')
                """);

        Object aggregate = invoke(newService(jdbcTemplate), "loadLatestMentionAggregate", 1L, null);

        assertThat(recordValue(aggregate, "requested")).isEqualTo(2L);
        assertThat(recordValue(aggregate, "completed")).isEqualTo(1L);
        assertThat(recordValue(aggregate, "mentions")).isEqualTo(1L);
        assertThat(recordValue(aggregate, "coveredPlatformCount")).isEqualTo(1L);
    }

    @Test
    void latestCoverageAndScenesRespectSelectedPlatform() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES (1001, 10, 'brand_awareness', 'A'), (1002, 10, 'qa', 'A')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, channel_code, batch_date, question_tier,
                     status, effective_hit, search_requested, search_triggered, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao_web', NULL, DATE '2026-06-17', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-17 09:00:00'),
                    (2, 1, 1002, 'tencent_search_web', NULL, DATE '2026-06-17', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-17 09:01:00')
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        Object coverage = invoke(service, "loadLatestQuestionCoverage", 1L, "doubao");
        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.SceneMetric> scenes =
                (List<MobileDashboardAggregateVO.SceneMetric>) invoke(service, "loadLatestSceneCoverage", 1L, "doubao");

        assertThat(recordValue(coverage, "total")).isEqualTo(2L);
        assertThat(recordValue(coverage, "covered")).isEqualTo(1L);
        assertThat(scenes).filteredOn(row -> "brand_awareness".equals(row.getCode()))
                .singleElement().satisfies(row -> assertThat(row.getCovered().getValue()).isEqualTo(1L));
        assertThat(scenes).filteredOn(row -> "qa".equals(row.getCode()))
                .singleElement().satisfies(row -> assertThat(row.getCovered().getValue()).isZero());
    }

    @Test
    void questionSearchSourcesUseEffectiveAttemptDeduplicateAndRejectUnsafeUrls() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.execute("""
                CREATE TABLE poll_search_sources (
                    id BIGINT,
                    attempt_id BIGINT,
                    rank_no INT,
                    title VARCHAR(255),
                    original_url VARCHAR(1000),
                    normalized_url VARCHAR(1000),
                    domain VARCHAR(255),
                    snippet VARCHAR(1000),
                    publish_time TIMESTAMP,
                    brand_matched INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE poll_citations (
                    id BIGINT,
                    attempt_id BIGINT,
                    source_id BIGINT,
                    citation_index INT,
                    confidence VARCHAR(32)
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, platform_code, channel_code, batch_date, question_tier, status,
                     execution_finalized, effective_attempt_id, search_requested, search_triggered)
                VALUES
                    (10, 1, 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 1, 99, 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_search_sources
                    (id, attempt_id, rank_no, title, original_url, normalized_url, domain, snippet, publish_time, brand_matched)
                VALUES
                    (1, 99, 2, '可信来源', 'https://example.com/a?utm_source=test', 'https://example.com/a?utm_source=test', 'fake.example.net', '来源摘要', NULL, 1),
                    (2, 99, 3, '重复来源', 'https://example.com/a#section', 'https://example.com/a#section', 'example.com', NULL, NULL, 0),
                    (3, 99, 1, '不安全来源', 'javascript:alert(1)', 'javascript:alert(1)', NULL, NULL, NULL, 1),
                    (4, 99, 4, '第二来源', 'https://news.example.org/b', NULL, 'trusted.example.com', NULL, NULL, 0),
                    (5, 100, 1, '其他尝试', 'https://other.example.net/c', NULL, NULL, NULL, NULL, 1),
                    (6, 99, 5, '本机地址', 'http://127.0.0.1/private', NULL, NULL, NULL, NULL, 0),
                    (7, 99, 6, '伪装地址', 'https://trusted.example.com@evil.example.net/a', NULL, NULL, NULL, NULL, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_citations (id, attempt_id, source_id, citation_index, confidence)
                VALUES (1, 99, 1, 7, 'CONFIRMED')
                """);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.QuestionSearchSource> sources =
                (List<MobileDashboardAggregateVO.QuestionSearchSource>) invoke(
                        newService(jdbcTemplate), "loadQuestionSearchSources", 1L, 10L);

        assertThat(sources).extracting(MobileDashboardAggregateVO.QuestionSearchSource::getSourceId)
                .containsExactly(1L, 4L);
        assertThat(sources.get(0).getCited()).isTrue();
        assertThat(sources.get(0).getCitationIndex()).isEqualTo(7);
        assertThat(sources.get(0).getDomain()).isEqualTo("example.com");
        assertThat(sources.get(1).getDomain()).isEqualTo("news.example.org");
    }

    @Test
    void latestQuestionCoverageKeepsSevenDayStaggeredPollResults() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        createPollResultsTable(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES
                    (1001, 10, 'brand_awareness', 'A'),
                    (1002, 10, 'qa', 'A')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_keyword_daily_summary
                    (project_id, keyword_result_id, batch_date, question_tier, completed_count, hit_count, effective_hit_count)
                VALUES
                    (1, 1001, DATE '2026-06-20', 'A', 1, 0, 1),
                    (1, 1002, DATE '2026-06-26', 'A', 1, 1, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, keyword_text_snapshot, platform_code, channel_code, batch_date,
                     question_tier, status, is_hit, effective_hit, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, 'q1', 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 1, NULL, 1, TIMESTAMP '2026-06-20 09:00:00'),
                    (2, 1, 1002, 'q2', 'doubao_web', 'doubao', DATE '2026-06-26', 'A', 'completed', 1, NULL, 1, TIMESTAMP '2026-06-26 09:00:00')
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        Object completeBatchDate = invoke(service, "loadLatestCompletePollBatchDate", 1L);
        Object latestCoverage = invoke(service, "loadLatestQuestionCoverage", 1L);
        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.SceneMetric> latestScenes =
                (List<MobileDashboardAggregateVO.SceneMetric>) invoke(service, "loadLatestSceneCoverage", 1L);

        assertThat(completeBatchDate).isNull();
        assertThat(recordValue(latestCoverage, "total")).isEqualTo(2L);
        assertThat(recordValue(latestCoverage, "covered")).isEqualTo(2L);
        assertThat(latestScenes).filteredOn(row -> "brand_awareness".equals(row.getCode()))
                .singleElement()
                .satisfies(row -> assertThat(row.getCovered().getValue()).isEqualTo(1L));
        assertThat(latestScenes).filteredOn(row -> "qa".equals(row.getCode()))
                .singleElement()
                .satisfies(row -> assertThat(row.getCovered().getValue()).isEqualTo(1L));
    }

    @Test
    void buildingContentExcludesDraftsThatAlreadyHaveCurrentVisiblePublishRecords() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT,
                    project_id BIGINT,
                    status VARCHAR(32),
                    allocation_mode VARCHAR(16),
                    created_at TIMESTAMP
                )
                """);
        createPublishRecordTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO article_draft (id, project_id, status, allocation_mode, created_at)
                VALUES
                    (101, 1, 'pending_review', NULL, TIMESTAMP '2026-06-03 09:00:00'),
                    (102, 1, 'distributing', NULL, TIMESTAMP '2026-06-04 09:00:00'),
                    (103, 1, 'rejected', NULL, TIMESTAMP '2026-06-05 09:00:00'),
                    (104, 1, 'approved', NULL, TIMESTAMP '2026-06-06 09:00:00'),
                    (105, 1, 'approved', 'auto', TIMESTAMP '2026-06-07 09:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (id, project_id, article_id, publish_status, target_channel, target_kind, published_at, verified_at, created_at)
                VALUES
                    (1, 1, 102, 'distributed', 'douyin', NULL, TIMESTAMP '2026-06-04 10:00:00', NULL, TIMESTAMP '2026-06-04 10:00:00'),
                    (2, 1, 104, 'offline', 'douyin', NULL, TIMESTAMP '2026-06-06 10:00:00', NULL, TIMESTAMP '2026-06-06 10:00:00')
                """);

        Object count = invoke(newService(jdbcTemplate), "countBuildingContent", 1L,
                dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void buildingQuestionCoverageCountsUncoveredQuestionsWithBuildingContentInSameScene() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        createPollResultsTable(jdbcTemplate);
        createPublishRecordTable(jdbcTemplate);
        jdbcTemplate.execute("""
                CREATE TABLE article_prompt_template (
                    id BIGINT,
                    question_scene_code VARCHAR(32)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT,
                    project_id BIGINT,
                    prompt_template_id BIGINT,
                    status VARCHAR(32),
                    allocation_mode VARCHAR(16)
                )
                """);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES
                    (1001, 10, 'brand', 'A'),
                    (1002, 10, 'brand', 'A'),
                    (1003, 10, 'deal', 'A'),
                    (1004, 10, 'qa', 'A')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, keyword_text_snapshot, platform_code, channel_code, batch_date,
                     question_tier, status, is_hit, effective_hit, brand_in_answer, updated_at)
                VALUES
                    (1, 1, 1001, '问题1', 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 0, 0, 0, TIMESTAMP '2026-06-20 10:00:00'),
                    (2, 1, 1002, '问题2', 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-20 10:00:00'),
                    (3, 1, 1004, '问题4', 'doubao_web', 'doubao', DATE '2026-06-20', 'A', 'completed', 1, 1, 1, TIMESTAMP '2026-06-20 10:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_prompt_template (id, question_scene_code)
                VALUES
                    (201, 'brand'),
                    (202, 'deal'),
                    (203, 'qa')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_draft (id, project_id, prompt_template_id, status, allocation_mode)
                VALUES
                    (301, 1, 201, 'approved', NULL),
                    (302, 1, 202, 'approved', NULL),
                    (303, 1, 203, 'approved', NULL),
                    (304, 1, 202, 'approved', NULL)
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (id, project_id, article_id, publish_status, target_channel, target_kind, published_at, verified_at, created_at)
                VALUES
                    (1, 1, 304, 'published', 'zhihu', NULL, TIMESTAMP '2026-06-21 10:00:00', NULL, TIMESTAMP '2026-06-21 10:00:00')
                """);

        Object count = invoke(newService(jdbcTemplate), "countBuildingQuestionCoverage", 1L);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void relatedBuildingContentTasksUseNormalizedQuestionSceneAndChannelCatalog() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE keyword_group_result (
                    id BIGINT,
                    scene_code VARCHAR(64)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_prompt_template (
                    id BIGINT,
                    question_scene_code VARCHAR(32)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT,
                    project_id BIGINT,
                    prompt_template_id BIGINT,
                    title VARCHAR(255),
                    topic VARCHAR(255),
                    topic_as_question VARCHAR(255),
                    target_channel VARCHAR(64),
                    status VARCHAR(32),
                    allocation_mode VARCHAR(16),
                    updated_at TIMESTAMP,
                    created_at TIMESTAMP
                )
                """);
        createPublishRecordTable(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group_result (id, scene_code) VALUES (1001, 'brand_awareness')");
        jdbcTemplate.update("""
                INSERT INTO article_prompt_template (id, question_scene_code)
                VALUES
                    (201, 'brand'),
                    (202, 'deal')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_draft
                    (id, project_id, prompt_template_id, title, topic, topic_as_question, target_channel, status, allocation_mode, updated_at, created_at)
                VALUES
                    (301, 1, 201, '品牌认知建设稿', '品牌问题', '品牌怎么选', 'self_media:douyin', 'approved', NULL, TIMESTAMP '2026-06-22 10:00:00', TIMESTAMP '2026-06-21 10:00:00'),
                    (302, 1, 202, '交易场景稿', '购买问题', '怎么买', 'self_media:zhihu', 'approved', NULL, TIMESTAMP '2026-06-22 09:00:00', TIMESTAMP '2026-06-21 09:00:00'),
                    (303, 1, 201, '已发布品牌稿', '品牌问题', '品牌已发布', 'self_media:zhihu', 'approved', NULL, TIMESTAMP '2026-06-22 08:00:00', TIMESTAMP '2026-06-21 08:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (id, project_id, article_id, publish_status, target_channel, target_kind, published_at, verified_at, created_at)
                VALUES
                    (1, 1, 303, 'published', 'zhihu', NULL, TIMESTAMP '2026-06-23 10:00:00', NULL, TIMESTAMP '2026-06-23 10:00:00')
                """);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.ContentTaskItem> tasks =
                (List<MobileDashboardAggregateVO.ContentTaskItem>) invoke(newService(jdbcTemplate),
                        "loadRelatedBuildingContentTasks", 1L, 1001L);

        assertThat(tasks).singleElement().satisfies(task -> {
            assertThat(task.getDraftId()).isEqualTo(301L);
            assertThat(task.getPlatformCodes()).containsExactly("douyin");
            assertThat(task.getStatus()).isEqualTo("building");
        });
    }

    @Test
    void mergedQuestionMonitorItemDisplaysRowThatContributesTopStatus() throws Exception {
        MobileDashboardAggregateService service = newService(jdbcTemplate());
        Object mentionedOnly = questionMonitorRow(
                1001L,
                2001L,
                "deepseek",
                "阜阳颍州区全屋智能哪家好",
                LocalDateTime.of(2026, 7, 4, 10, 0),
                true,
                true,
                true,
                false,
                false,
                null,
                null,
                "DeepSeek answer"
        );
        Object firstRecommend = questionMonitorRow(
                1001L,
                2002L,
                "doubao",
                "阜阳颍州区全屋智能哪家好",
                LocalDateTime.of(2026, 7, 4, 9, 0),
                true,
                true,
                true,
                true,
                true,
                1,
                "首推证据",
                "Doubao answer"
        );

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items =
                (List<MobileDashboardAggregateVO.QuestionMonitorItem>) invoke(service, "mergeQuestionMonitorRows",
                        List.of(mentionedOnly, firstRecommend), true, "ready");

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getPollResultId()).isEqualTo(2002L);
            assertThat(item.getPlatformCode()).isEqualTo("doubao");
            assertThat(item.getResponseText()).isEqualTo("Doubao answer");
            assertThat(item.getFirstRecommend().getValue()).isTrue();
            assertThat(item.getTags()).contains("mentioned", "recommended", "first_recommend");
        });
    }

    @Test
    void completedResultWithoutActualSearchIsExposedAsSearchNotTriggered() throws Exception {
        MobileDashboardAggregateService service = newService(jdbcTemplate());
        Object row = questionMonitorRow(
                1001L,
                2001L,
                "doubao",
                "联网未触发的问题",
                LocalDateTime.of(2026, 7, 4, 10, 0),
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                "模型直接回答"
        );

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items =
                (List<MobileDashboardAggregateVO.QuestionMonitorItem>) invoke(service, "mergeQuestionMonitorRows",
                        List.of(row), true, "ready");

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getMonitorStatus()).isEqualTo("search_not_triggered");
            assertThat(item.getMentioned()).isFalse();
            assertThat(item.getPollResultId()).isEqualTo(2001L);
            assertThat(item.getRecommended().isAvailable()).isFalse();
        });
    }

    @Test
    void recommendationAndFirstRecommendAreSuppressedWhenFocusBrandWasNotMentioned() throws Exception {
        MobileDashboardAggregateService service = newService(jdbcTemplate());
        Object row = questionMonitorRow(
                1001L,
                2001L,
                "doubao",
                "未提及品牌的问题",
                LocalDateTime.of(2026, 7, 4, 10, 0),
                false,
                true,
                true,
                true,
                true,
                1,
                "错误的历史裁判结果",
                "回答没有提及目标品牌"
        );

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.QuestionMonitorItem> items =
                (List<MobileDashboardAggregateVO.QuestionMonitorItem>) invoke(service, "mergeQuestionMonitorRows",
                        List.of(row), true, "ready");

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getMonitorStatus()).isEqualTo("not_mentioned");
            assertThat(item.getRecommended().getValue()).isFalse();
            assertThat(item.getFirstRecommend().getValue()).isFalse();
            assertThat(item.getRankPosition().isAvailable()).isFalse();
            assertThat(item.getTags()).doesNotContain("recommended", "first_recommend");
        });
    }

    @Test
    void contentTaskListIncludesConfirmedSelfMediaSchedulesWithoutPublishRecord() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE ALIAS IF NOT EXISTS SUBSTRING_INDEX FOR
                "com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateServiceTest.substringIndex"
                """);
        jdbcTemplate.execute("""
                CREATE TABLE article_draft (
                    id BIGINT,
                    project_id BIGINT,
                    title VARCHAR(255),
                    topic VARCHAR(255),
                    topic_as_question VARCHAR(255),
                    target_channel VARCHAR(64),
                    status VARCHAR(32),
                    allocation_mode VARCHAR(16),
                    updated_at TIMESTAMP,
                    created_at TIMESTAMP
                )
                """);
        createPublishRecordTable(jdbcTemplate);
        createSelfMediaPublishScheduleTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO article_draft
                    (id, project_id, title, topic, topic_as_question, target_channel, status, allocation_mode, updated_at, created_at)
                VALUES
                    (301, 1, '知乎已确认发布稿', '阜阳全屋智能', '阜阳全屋智能哪家好', NULL, 'published', NULL, TIMESTAMP '2026-06-30 16:56:00', TIMESTAMP '2026-06-30 16:50:00'),
                    (302, 1, '官网稿', '官网问题', '官网怎么选', 'official_site', 'approved', NULL, TIMESTAMP '2026-06-30 15:00:00', TIMESTAMP '2026-06-30 15:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO self_media_publish_schedule
                    (id, article_id, platform, status, platform_published_url, published_confirmed_at, updated_at, created_at)
                VALUES
                    (222, 301, 'zhihu', 'published_confirmed', 'https://zhuanlan.zhihu.com/p/2055333874897511162', TIMESTAMP '2026-06-30 16:56:30', TIMESTAMP '2026-06-30 16:56:26', TIMESTAMP '2026-06-30 16:50:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO article_publish_record
                    (id, project_id, article_id, publish_status, target_channel, target_kind, published_url, url_quality, published_at, verified_at, created_at)
                VALUES
                    (1, 1, 302, 'distributed', 'official_site', NULL, 'https://example.com/a', 'public_url', TIMESTAMP '2026-06-30 15:05:00', TIMESTAMP '2026-06-30 15:05:00', TIMESTAMP '2026-06-30 15:05:00')
                """);

        MobileDashboardAggregateVO.TaskList list =
                (MobileDashboardAggregateVO.TaskList) invoke(newService(jdbcTemplate), "loadContentTaskList",
                        1L, dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)), 1, 4);

        assertThat(list.getTotal()).isEqualTo(2);
        assertThat(list.getItems()).extracting(MobileDashboardAggregateVO.ContentTaskItem::getDraftId)
                .containsExactly(301L, 302L);
        MobileDashboardAggregateVO.ContentTaskItem selfMedia = list.getItems().get(0);
        assertThat(selfMedia.getStatus()).isEqualTo("indexed");
        assertThat(selfMedia.getPlatformCodes()).containsExactly("zhihu");
        assertThat(selfMedia.getPublishUrl()).isEqualTo("https://zhuanlan.zhihu.com/p/2055333874897511162");
    }

    @Test
    void sceneCoverageShowsDealAsPurchaseConsultationInsteadOfHiddenConversion() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createQuestionCoverageTables(jdbcTemplate);
        jdbcTemplate.update("INSERT INTO keyword_group (id, deleted) VALUES (10, 0)");
        jdbcTemplate.update("INSERT INTO project_keyword_group_rel (project_id, keyword_group_id) VALUES (1, 10)");
        jdbcTemplate.update("""
                INSERT INTO keyword_group_result (id, group_id, scene_code, question_tier)
                VALUES
                    (1001, 10, 'deal', 'A'),
                    (1002, 10, 'conversion', 'A')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_keyword_daily_summary
                    (project_id, keyword_result_id, batch_date, question_tier, hit_count, effective_hit_count)
                VALUES
                    (1, 1001, DATE '2026-06-20', 'A', 1, 0),
                    (1, 1002, DATE '2026-06-20', 'A', 1, 0)
                """);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.SceneMetric> rows =
                (List<MobileDashboardAggregateVO.SceneMetric>) invoke(newService(jdbcTemplate), "loadSceneCoverage",
                        1L, dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        MobileDashboardAggregateVO.SceneMetric purchase = rows.stream()
                .filter(row -> "purchase_consultation".equals(row.getCode()))
                .findFirst()
                .orElseThrow();
        MobileDashboardAggregateVO.SceneMetric conversion = rows.stream()
                .filter(row -> "conversion".equals(row.getCode()))
                .findFirst()
                .orElseThrow();

        assertThat(purchase.isVisible()).isTrue();
        assertThat(purchase.getTotal().getValue()).isEqualTo(1L);
        assertThat(conversion.isVisible()).isFalse();
    }

    private static MobileDashboardAggregateService newService(JdbcTemplate jdbcTemplate) {
        return new MobileDashboardAggregateService(jdbcTemplate, mock(MobileDashboardEntityJudgeService.class));
    }

    private static JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return new JdbcTemplate(dataSource);
    }

    private static void createPublishRecordTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE article_publish_record (
                    id BIGINT,
                    project_id BIGINT,
                    article_id BIGINT,
                    publish_status VARCHAR(32),
                    target_channel VARCHAR(64),
                    target_kind VARCHAR(64),
                    published_url VARCHAR(1000),
                    url_quality VARCHAR(32),
                    published_at TIMESTAMP,
                    verified_at TIMESTAMP,
                    created_at TIMESTAMP
                )
                """);
    }

    private static void createSelfMediaPublishScheduleTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE self_media_publish_schedule (
                    id BIGINT,
                    article_id BIGINT,
                    platform VARCHAR(32),
                    status VARCHAR(32),
                    platform_published_url VARCHAR(1000),
                    published_confirmed_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    created_at TIMESTAMP
                )
                """);
    }

    private static void createProjectChannelAllocationTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE project_channel_allocation (
                    project_id BIGINT,
                    channel_code VARCHAR(64),
                    period_type_snapshot VARCHAR(32),
                    allocated_count BIGINT
                )
                """);
    }

    private static void createProjectAndSelfMediaAccountTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE project (
                    id BIGINT,
                    brand_id BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE self_media_account (
                    id BIGINT,
                    brand_id BIGINT,
                    platform VARCHAR(64),
                    status VARCHAR(32),
                    deleted_at TIMESTAMP
                )
                """);
    }

    private static void createAiPlatformConfigTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE ai_platform_config (
                    id BIGINT,
                    platform_code VARCHAR(32),
                    platform_logo_url VARCHAR(1000),
                    platform_logo_object_key VARCHAR(1000),
                    enabled INT
                )
                """);
    }

    private static void createQuestionCoverageTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE project_keyword_group_rel (
                    project_id BIGINT,
                    keyword_group_id BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE keyword_group (
                    id BIGINT,
                    deleted INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE keyword_group_result (
                    id BIGINT,
                    group_id BIGINT,
                    scene_code VARCHAR(64),
                    question_tier VARCHAR(8)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE poll_keyword_daily_summary (
                    project_id BIGINT,
                    keyword_result_id BIGINT,
                    batch_date DATE,
                    question_tier VARCHAR(8),
                    completed_count BIGINT,
                    hit_count BIGINT,
                    effective_hit_count BIGINT
                )
                """);
    }

    private static void createPollResultsTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE poll_results (
                    id BIGINT,
                    project_id BIGINT,
                    keyword_result_id BIGINT,
                    keyword_text_snapshot VARCHAR(255),
                    platform_code VARCHAR(32),
                    channel_code VARCHAR(32),
                    batch_date DATE,
                    question_tier VARCHAR(8),
                    status VARCHAR(32),
                    is_hit INT,
                    effective_hit INT,
                    execution_finalized INT DEFAULT 1,
                    effective_attempt_id BIGINT DEFAULT 1,
                    search_requested INT DEFAULT 1,
                    search_triggered INT DEFAULT 1,
                    brand_in_answer INT DEFAULT 0,
                    detail_json CLOB,
                    updated_at TIMESTAMP
                )
                """);
    }

    private static Object dateRange(LocalDate start, LocalDate end) throws Exception {
        Class<?> dateRangeClass = Class.forName(
                "com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateService$DateRange");
        Constructor<?> constructor = dateRangeClass.getDeclaredConstructor(LocalDate.class, LocalDate.class);
        constructor.setAccessible(true);
        return constructor.newInstance(start, end);
    }

    private static Object contentFacts(long totalPublished,
                                       long monthPublished,
                                       long monthContent,
                                       long monthBuilding,
                                       long totalIndexed,
                                       long monthIndexed) throws Exception {
        Class<?> contentFactsClass = Class.forName(
                "com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateService$ContentFacts");
        Constructor<?> constructor = contentFactsClass.getDeclaredConstructor(
                long.class,
                long.class,
                long.class,
                long.class,
                long.class,
                long.class,
                Map.class,
                Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(totalPublished, monthPublished, monthContent, monthBuilding,
                totalIndexed, monthIndexed, Map.of(), Map.of());
    }

    private static Object questionMonitorRow(Long keywordResultId,
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
                                             String responseText) throws Exception {
        Class<?> rowClass = Class.forName(
                "com.huanjing.geo.module.mobiledashboard.service.MobileDashboardAggregateService$QuestionMonitorRow");
        Constructor<?> constructor = rowClass.getDeclaredConstructor(
                Long.class,
                Long.class,
                String.class,
                String.class,
                LocalDateTime.class,
                boolean.class,
                Boolean.class,
                boolean.class,
                Boolean.class,
                Boolean.class,
                Integer.class,
                String.class,
                String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(keywordResultId, pollResultId, platformCode, questionTitle, completedAt,
                mentioned, searchTriggered, rowJudgeReady, recommended, firstRecommend, rankPosition, evidence, responseText);
    }

    public static String substringIndex(String value, String delimiter, int count) {
        if (value == null || delimiter == null || delimiter.isEmpty() || count == 0) {
            return "";
        }
        String[] parts = value.split(java.util.regex.Pattern.quote(delimiter), -1);
        if (count > 0) {
            int length = Math.min(count, parts.length);
            return String.join(delimiter, java.util.Arrays.copyOfRange(parts, 0, length));
        }
        int length = Math.min(-count, parts.length);
        return String.join(delimiter, java.util.Arrays.copyOfRange(parts, parts.length - length, parts.length));
    }

    private static Object invoke(Object target, String methodName, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, args.length);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String methodName, int argCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == argCount) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + methodName + " with " + argCount + " args");
    }

    private static long recordValue(Object record, String accessorName) throws Exception {
        Method method = record.getClass().getDeclaredMethod(accessorName);
        method.setAccessible(true);
        return (Long) method.invoke(record);
    }
}
