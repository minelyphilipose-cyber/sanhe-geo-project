package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.module.mobiledashboard.dto.MobileDashboardAggregateVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MobileDashboardAggregateServiceTest {

    @Test
    void mentionAggregateUsesRowLevelEffectiveHitFallbackAcrossTransitionWindow() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE poll_platform_daily_summary (
                    project_id BIGINT,
                    batch_date DATE,
                    platform_code VARCHAR(32),
                    question_tier VARCHAR(8),
                    completed_count BIGINT,
                    hit_count BIGINT,
                    effective_hit_count BIGINT
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_platform_daily_summary
                    (project_id, batch_date, platform_code, question_tier, completed_count, hit_count, effective_hit_count)
                VALUES
                    (1, DATE '2026-06-10', 'doubao', 'A', 10, 3, 0),
                    (1, DATE '2026-06-20', 'doubao', 'A', 10, 8, 4),
                    (1, DATE '2026-06-20', 'deepseek', 'B', 10, 10, 10)
                """);

        Object aggregate = invoke(newService(jdbcTemplate), "loadMentionAggregate",
                1L, dateRange(LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 21)), null);

        assertThat(recordValue(aggregate, "completed")).isEqualTo(20L);
        assertThat(recordValue(aggregate, "mentions")).isEqualTo(7L);
        assertThat(recordValue(aggregate, "coveredPlatformCount")).isEqualTo(1L);
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
    void platformCompletionKeepsPublishedCountWhenMonthlyChannelQuotaIsMissing() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE project_channel_allocation (
                    project_id BIGINT,
                    channel_code VARCHAR(32),
                    period_type_snapshot VARCHAR(32),
                    allocated_count BIGINT
                )
                """);
        MobileDashboardAggregateService service = newService(jdbcTemplate);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.PlatformCompletion> rows =
                (List<MobileDashboardAggregateVO.PlatformCompletion>) ReflectionTestUtils.invokeMethod(
                        service, "loadPlatformCompletion", 1L, Map.of("douyin", 5L));

        assertThat(rows).hasSize(1);
        MobileDashboardAggregateVO.PlatformCompletion douyin = rows.get(0);
        assertThat(douyin.getCode()).isEqualTo("douyin");
        assertThat(douyin.getPublished()).isEqualTo(5L);
        assertThat(douyin.getQuota()).isZero();
        assertThat(douyin.getCompletionRate().isAvailable()).isFalse();
        assertThat(douyin.getCompletionRate().getReason()).contains("暂无逐渠道月度配额");
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
    void platformPerformanceSortsByRateDescendingThenStableAiPlatformOrder() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE poll_platform_daily_summary (
                    project_id BIGINT,
                    batch_date DATE,
                    platform_code VARCHAR(32),
                    question_tier VARCHAR(8),
                    completed_count BIGINT,
                    hit_count BIGINT,
                    effective_hit_count BIGINT
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_platform_daily_summary
                    (project_id, batch_date, platform_code, question_tier, completed_count, hit_count, effective_hit_count)
                VALUES
                    (1, DATE '2026-06-20', 'doubao', 'A', 10, 0, 0),
                    (1, DATE '2026-06-20', 'deepseek', 'A', 10, 1, 0),
                    (1, DATE '2026-06-20', 'tongyi', 'A', 10, 0, 1),
                    (1, DATE '2026-06-20', 'wenxin', 'A', 10, 2, 0)
                """);

        @SuppressWarnings("unchecked")
        List<MobileDashboardAggregateVO.PlatformMetric> rows =
                (List<MobileDashboardAggregateVO.PlatformMetric>) invoke(newService(jdbcTemplate), "loadPlatformPerformance",
                        1L, dateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)));

        assertThat(rows).extracting(MobileDashboardAggregateVO.PlatformMetric::getCode)
                .containsExactly("wenxin", "deepseek", "tongyi", "doubao");
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
                    published_at TIMESTAMP,
                    verified_at TIMESTAMP,
                    created_at TIMESTAMP
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
                    hit_count BIGINT,
                    effective_hit_count BIGINT
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
