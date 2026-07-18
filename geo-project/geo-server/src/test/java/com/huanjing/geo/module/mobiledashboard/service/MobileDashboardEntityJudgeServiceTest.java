package com.huanjing.geo.module.mobiledashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileDashboardEntityJudgeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void coverageReady_requiresAtLeastEightyPercentOfExpectedSamples() {
        MobileDashboardEntityJudgeService service = service(mock(JdbcTemplate.class));

        assertThat(service.coverageReady(new MobileDashboardEntityJudgeService.JudgeCoverage(0, 0, 0, 0))).isFalse();
        assertThat(service.coverageReady(new MobileDashboardEntityJudgeService.JudgeCoverage(100, 79, 20, 5))).isFalse();
        assertThat(service.coverageReady(new MobileDashboardEntityJudgeService.JudgeCoverage(100, 80, 20, 5))).isTrue();
    }

    @Test
    void focusCoverageReadsOnlyTierASummaryRows() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        jdbcTemplate.execute("""
                CREATE TABLE poll_entity_judge_daily_summary (
                    project_id BIGINT,
                    batch_date DATE,
                    question_tier VARCHAR(8),
                    platform_code VARCHAR(32),
                    entity_type VARCHAR(32),
                    entity_ref_id BIGINT,
                    judge_prompt_version VARCHAR(64),
                    expected_count BIGINT,
                    success_count BIGINT,
                    recommended_count BIGINT,
                    first_recommend_count BIGINT
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_entity_judge_daily_summary
                    (project_id, batch_date, question_tier, platform_code, entity_type, entity_ref_id, judge_prompt_version,
                     expected_count, success_count, recommended_count, first_recommend_count)
                VALUES
                    (1, DATE '2026-06-20', 'A', 'doubao', 'focus_brand', 0, ?, 10, 8, 3, 1),
                    (1, DATE '2026-06-20', 'B', 'doubao', 'focus_brand', 0, ?, 99, 99, 99, 99)
                """, MobileDashboardEntityJudgeService.PROMPT_VERSION, MobileDashboardEntityJudgeService.PROMPT_VERSION);

        MobileDashboardEntityJudgeService.JudgeCoverage coverage = service(jdbcTemplate)
                .focusCoverage(1L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(coverage.expectedCount()).isEqualTo(10);
        assertThat(coverage.successCount()).isEqualTo(8);
        assertThat(coverage.recommendedCount()).isEqualTo(3);
        assertThat(coverage.firstRecommendCount()).isEqualTo(1);
    }

    @Test
    void latestFocusCoverageUsesOnlyNewestCompletedResultPerQuestionAndPlatform() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createLatestCoverageTables(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO poll_results
                    (id, project_id, keyword_result_id, platform_code, batch_date, question_tier, status, updated_at)
                VALUES
                    (1, 1, 1001, 'doubao', DATE '2026-06-10', 'A', 'completed', TIMESTAMP '2026-06-10 09:00:00'),
                    (2, 1, 1001, 'doubao', DATE '2026-06-17', 'A', 'completed', TIMESTAMP '2026-06-17 09:00:00'),
                    (3, 1, 1001, 'qwen', DATE '2026-06-17', 'A', 'completed', TIMESTAMP '2026-06-17 09:00:00'),
                    (4, 1, 1002, 'doubao', DATE '2026-06-17', 'A', 'running', TIMESTAMP '2026-06-17 09:00:00'),
                    (5, 1, 1003, 'doubao', DATE '2026-06-17', 'B', 'completed', TIMESTAMP '2026-06-17 09:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO poll_result_entity_judge
                    (poll_result_id, entity_type, entity_ref_id, judge_prompt_version, judge_status, recommended, first_recommend)
                VALUES
                    (1, 'focus_brand', 0, ?, 'success', 1, 1),
                    (2, 'focus_brand', 0, ?, 'success', 0, 0),
                    (3, 'focus_brand', 0, ?, 'success', 1, 0),
                    (4, 'focus_brand', 0, ?, 'success', 1, 1),
                    (5, 'focus_brand', 0, ?, 'success', 1, 1)
                """, MobileDashboardEntityJudgeService.PROMPT_VERSION,
                MobileDashboardEntityJudgeService.PROMPT_VERSION,
                MobileDashboardEntityJudgeService.PROMPT_VERSION,
                MobileDashboardEntityJudgeService.PROMPT_VERSION,
                MobileDashboardEntityJudgeService.PROMPT_VERSION);

        MobileDashboardEntityJudgeService.JudgeCoverage coverage = service(jdbcTemplate).latestFocusCoverage(1L);

        assertThat(coverage.expectedCount()).isEqualTo(2);
        assertThat(coverage.successCount()).isEqualTo(2);
        assertThat(coverage.recommendedCount()).isEqualTo(1);
        assertThat(coverage.firstRecommendCount()).isZero();
    }

    @Test
    void parseFocus_forcesRecommendationAndFirstFalseWhenEffectiveHitIsNotTrue() throws Exception {
        MobileDashboardEntityJudgeService service = service(mock(JdbcTemplate.class));
        Object candidate = candidate(false);
        JsonNode node = objectMapper.readTree("""
                {"recommended":true,"first_recommend":true,"rank_position":1,"evidence":"月娇家居","matched_alias":"月娇家居","confidence":0.9}
                """);

        Object result = invokeParseFocus(service, node, candidate);

        assertThat((Boolean) invoke(result, "recommended")).isFalse();
        assertThat((Boolean) invoke(result, "firstRecommend")).isFalse();
        assertThat(invoke(result, "rankPosition")).isNull();
        assertThat((String) invoke(result, "evidence")).isEqualTo("月娇家居");
    }

    @Test
    void parseFocus_keepsRecommendationWhenLocalMatcherFindsFocus() throws Exception {
        MobileDashboardEntityJudgeService service = service(mock(JdbcTemplate.class));
        Object candidate = candidate(false);
        JsonNode node = objectMapper.readTree("""
                {"recommended":true,"first_recommend":true,"rank_position":1,"evidence":"月娇家居","matched_alias":"月娇家居","confidence":0.9}
                """);

        Object result = invokeParseFocus(service, node, candidate, true);

        assertThat((Boolean) invoke(result, "recommended")).isTrue();
        assertThat((Boolean) invoke(result, "firstRecommend")).isTrue();
        assertThat(invoke(result, "rankPosition")).isEqualTo(1);
    }

    @Test
    void parseEntity_clearsRankAndFirstWhenRecommendedIsFalse() throws Exception {
        MobileDashboardEntityJudgeService service = service(mock(JdbcTemplate.class));
        JsonNode node = objectMapper.readTree("""
                {"recommended":false,"first_recommend":true,"rank_position":1}
                """);

        Method method = MobileDashboardEntityJudgeService.class.getDeclaredMethod("parseEntity", JsonNode.class, Long.class);
        method.setAccessible(true);
        Object result = method.invoke(service, node, 0L);

        assertThat((Boolean) invoke(result, "recommended")).isFalse();
        assertThat((Boolean) invoke(result, "firstRecommend")).isFalse();
        assertThat(invoke(result, "rankPosition")).isNull();
    }

    @Test
    void judgeOne_writesJudgeRowsButDoesNotUpdatePollResultEffectiveHit() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
        ProjectCompetitorConfigService competitors = mock(ProjectCompetitorConfigService.class);
        when(competitors.activeCompetitors(11L)).thenReturn(List.of());
        when(platformMapper.selectList(any())).thenReturn(List.of(platform()));
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class))).thenReturn(List.of());
        com.huanjing.geo.common.llm.LlmCallFacade llm = mock(com.huanjing.geo.common.llm.LlmCallFacade.class);
        when(llm.execute(any(LlmCallRequest.class))).thenReturn(LlmCallResult.routed(new LlmRouteResult(
                "doubao",
                "豆包",
                "low",
                "low-model",
                "低成本模型",
                """
                        {"focus_brand":{"recommended":true,"first_recommend":true,"rank_position":1,"evidence":"月娇家居","confidence":0.9},"competitors":[]}
                        """,
                1200,
                1,
                null
        )));
        MobileDashboardEntityJudgeService service = new MobileDashboardEntityJudgeService(
                jdbcTemplate,
                objectMapper,
                llm,
                platformMapper,
                competitors,
                new MobileEntityMentionMatcher(),
                mock(CurrentUserService.class),
                new MobileEntityJudgeRuntimeConfig()
        );

        Method method = MobileDashboardEntityJudgeService.class.getDeclaredMethod("judgeOne", pollCandidateClass());
        method.setAccessible(true);
        method.invoke(service, candidate(true));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues())
                .noneMatch(sql -> sql.toLowerCase().contains("update poll_results"));

        ArgumentCaptor<LlmCallRequest> requestCaptor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(llm).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().routeRequest().feature()).isEqualTo(LlmFeature.MOBILE_JUDGE);
    }

    @Test
    void judgeOne_writesDeterministicFalseForEveryCurrentEntityWhenNoTrackedEntityMatched() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
        ProjectCompetitorConfigService competitors = mock(ProjectCompetitorConfigService.class);
        when(competitors.activeCompetitors(11L)).thenReturn(List.of(
                new ProjectCompetitorConfigService.CompetitorEntity(301L, "竞品A", List.of("A牌"), 1, 2, "passed"),
                new ProjectCompetitorConfigService.CompetitorEntity(302L, "竞品B", List.of(), 2, 3, "passed")
        ));
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class), any(Object[].class))).thenReturn(3);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class))).thenReturn(List.of());
        com.huanjing.geo.common.llm.LlmCallFacade llm = mock(com.huanjing.geo.common.llm.LlmCallFacade.class);
        MobileDashboardEntityJudgeService service = new MobileDashboardEntityJudgeService(
                jdbcTemplate,
                objectMapper,
                llm,
                platformMapper,
                competitors,
                new MobileEntityMentionMatcher(),
                mock(CurrentUserService.class),
                new MobileEntityJudgeRuntimeConfig()
        );

        Method method = MobileDashboardEntityJudgeService.class.getDeclaredMethod("judgeOne", pollCandidateClass());
        method.setAccessible(true);
        method.invoke(service, candidate(false, "这是一段没有任何品牌实体的回答"));

        verify(llm, never()).execute(any());
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeast(3)).update(anyString(), argsCaptor.capture());
        List<Object[]> judgeRows = argsCaptor.getAllValues().stream()
                .filter(args -> args.length > 12 && "deterministic_no_entity_hit".equals(args[11]))
                .toList();
        assertThat(judgeRows).hasSize(3);
        assertThat(judgeRows).extracting(args -> args[7]).containsExactlyInAnyOrder("focus_brand", "competitor", "competitor");
        assertThat(judgeRows).extracting(args -> args[8]).containsExactlyInAnyOrder(0L, 301L, 302L);
        assertThat(judgeRows).allSatisfy(args -> assertThat((String) args[19]).contains("no_tracked_entity_matched"));
    }

    private MobileDashboardEntityJudgeService service(JdbcTemplate jdbcTemplate) {
        return new MobileDashboardEntityJudgeService(
                jdbcTemplate,
                objectMapper,
                mock(com.huanjing.geo.common.llm.LlmCallFacade.class),
                mock(AiPlatformConfigMapper.class),
                mock(ProjectCompetitorConfigService.class),
                new MobileEntityMentionMatcher(),
                mock(CurrentUserService.class),
                new MobileEntityJudgeRuntimeConfig()
        );
    }

    private JdbcTemplate jdbcTemplate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return new JdbcTemplate(dataSource);
    }

    private void createLatestCoverageTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE poll_results (
                    id BIGINT,
                    project_id BIGINT,
                    keyword_result_id BIGINT,
                    platform_code VARCHAR(32),
                    channel_code VARCHAR(32),
                    batch_date DATE,
                    question_tier VARCHAR(8),
                    status VARCHAR(32),
                    execution_finalized INT DEFAULT 1,
                    effective_attempt_id BIGINT DEFAULT 1,
                    search_requested INT DEFAULT 1,
                    search_triggered INT DEFAULT 1,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE poll_result_entity_judge (
                    poll_result_id BIGINT,
                    entity_type VARCHAR(32),
                    entity_ref_id BIGINT,
                    entity_config_version INT,
                    judge_prompt_version VARCHAR(64),
                    judge_status VARCHAR(32),
                    recommended INT,
                    first_recommend INT
                )
                """);
    }

    private AiPlatformConfig platform() {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode("doubao");
        platform.setPlatformName("豆包");
        platform.setModelId("primary-model");
        platform.setLowModelId("low-model");
        platform.setTimeoutMs(LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS);
        return platform;
    }

    private Object invokeParseFocus(MobileDashboardEntityJudgeService service, JsonNode node, Object candidate) throws Exception {
        return invokeParseFocus(service, node, candidate, false);
    }

    private Object invokeParseFocus(MobileDashboardEntityJudgeService service, JsonNode node, Object candidate, boolean focusMatchedByLocalMatcher) throws Exception {
        Method method = MobileDashboardEntityJudgeService.class.getDeclaredMethod("parseFocus", JsonNode.class, pollCandidateClass(), boolean.class);
        method.setAccessible(true);
        return method.invoke(service, node, candidate, focusMatchedByLocalMatcher);
    }

    private Object candidate(Boolean effectiveHit) throws Exception {
        return candidate(effectiveHit, "优先推荐月娇家居");
    }

    private Object candidate(Boolean effectiveHit, String responseText) throws Exception {
        Constructor<?> constructor = pollCandidateClass().getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(
                101L,
                11L,
                201L,
                "全屋定制怎么选",
                LocalDate.of(2026, 6, 20),
                "A",
                1L,
                "doubao",
                effectiveHit,
                "月娇家居",
                null,
                responseText
        );
    }

    private Class<?> pollCandidateClass() {
        for (Class<?> nested : MobileDashboardEntityJudgeService.class.getDeclaredClasses()) {
            if ("PollCandidate".equals(nested.getSimpleName())) {
                return nested;
            }
        }
        throw new IllegalStateException("PollCandidate class not found");
    }

    private Object invoke(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }
}
