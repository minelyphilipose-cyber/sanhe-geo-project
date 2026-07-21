package com.huanjing.geo.module.mobiledashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.common.llm.LlmCallResult;
import com.huanjing.geo.common.llm.LlmModelConfig;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.common.llm.measurement.LlmObservationScope;
import com.huanjing.geo.common.llm.router.LlmFeature;
import com.huanjing.geo.common.llm.router.LlmPlatformCodeFilters;
import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.router.LlmRouteResult;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeRunRequest;
import com.huanjing.geo.module.mobiledashboard.dto.EntityJudgeRunVO;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobileDashboardEntityJudgeService {
    static final String PROMPT_VERSION = "mobile_entity_judge_v1";
    private static final int COVERAGE_THRESHOLD_PERCENT = 80;
    private static final String MOBILE_QUESTION_TIER = "A";
    private static final String FOCUS_BRAND = "focus_brand";
    private static final String COMPETITOR = "competitor";
    private static final String EFFECTIVE_WEB_SEARCH_REQUEST_SQL = """
            pr.execution_finalized = 1
            AND pr.effective_attempt_id IS NOT NULL
            AND pr.search_requested = 1
            """;
    private static final String EFFECTIVE_WEB_SEARCH_RESULT_SQL = EFFECTIVE_WEB_SEARCH_REQUEST_SQL + """
            AND pr.search_triggered = 1
            """;
    private static final String POLL_CHANNEL_SQL = "COALESCE(NULLIF(TRIM(pr.channel_code), ''), pr.platform_code)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LlmCallFacade llmCallFacade;
    private final AiPlatformConfigMapper aiPlatformConfigMapper;
    private final ProjectCompetitorConfigService competitorConfigService;
    private final MobileEntityMentionMatcher mentionMatcher;
    private final CurrentUserService currentUserService;
    private final MobileEntityJudgeRuntimeConfig judgeRuntimeConfig;

    public EntityJudgeRunVO runOnce(EntityJudgeRunRequest request) {
        currentUserService.ensurePermission("project.competitor.manage");
        int limit = request == null || request.getLimit() == null ? 50 : Math.max(1, Math.min(request.getLimit(), 200));
        Long projectId = request == null ? null : request.getProjectId();
        LocalDate startDate = request == null ? null : request.getStartDate();
        LocalDate endDate = request == null ? null : request.getEndDate();
        List<PollCandidate> candidates = loadCandidates(projectId, startDate, endDate, limit);
        return judgeCandidates(candidates);
    }

    @Scheduled(fixedDelayString = "${geo.mobile-dashboard.entity-judge.worker-ms:60000}")
    public void scheduledRun() {
        if (!judgeRuntimeConfig.isEnabled()) {
            return;
        }
        try {
            LocalDate startDate = LocalDate.now().minusDays(2);
            LocalDate endDate = LocalDate.now();
            List<Long> projectIds = loadPendingProjectIds(startDate, endDate, Math.max(1, judgeRuntimeConfig.getMaxProjectsPerRun()));
            int limit = Math.max(1, judgeRuntimeConfig.getPerProjectLimit());
            for (Long projectId : projectIds) {
                judgeCandidates(loadCandidates(projectId, startDate, endDate, limit));
            }
        } catch (Exception ex) {
            log.warn("mobile dashboard entity judge scheduled run failed: {}", ex.getMessage(), ex);
        }
    }

    public JudgeCoverage focusCoverage(Long projectId, LocalDate startDate, LocalDate endDate) {
        return coverage(projectId, startDate, endDate, FOCUS_BRAND, 0L);
    }

    public JudgeCoverage focusCoverage(Long projectId, LocalDate startDate, LocalDate endDate, String platformCode) {
        return coverage(projectId, startDate, endDate, FOCUS_BRAND, 0L, platformCode);
    }

    public JudgeCoverage latestFocusCoverage(Long projectId) {
        return latestFocusCoverage(projectId, null);
    }

    public JudgeCoverage latestFocusCoverage(Long projectId, String platformCode) {
        return latestCoverage(projectId, FOCUS_BRAND, 0L, platformCode);
    }

    public JudgeCoverage coverage(Long projectId, LocalDate startDate, LocalDate endDate, String entityType, Long entityRefId) {
        return coverage(projectId, startDate, endDate, entityType, entityRefId, null);
    }

    private JudgeCoverage coverage(Long projectId, LocalDate startDate, LocalDate endDate, String entityType, Long entityRefId, String platformCode) {
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND platform_code IN (%s) ".formatted(platformAliasSql(platformCode));
        }
        JudgeCoverage row = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(expected_count), 0) AS expected_count,
                       COALESCE(SUM(success_count), 0) AS success_count,
                       COALESCE(SUM(recommended_count), 0) AS recommended_count,
                       COALESCE(SUM(first_recommend_count), 0) AS first_recommend_count
                  FROM poll_entity_judge_daily_summary
                 WHERE project_id = ?
                   AND batch_date BETWEEN ? AND ?
                   AND question_tier = ?
                   AND entity_type = ?
                   AND entity_ref_id = ?
                   AND judge_prompt_version = ?
                """ + platformClause, (rs, rowNum) -> new JudgeCoverage(
                rs.getLong("expected_count"),
                rs.getLong("success_count"),
                rs.getLong("recommended_count"),
                rs.getLong("first_recommend_count")
        ), projectId, Date.valueOf(startDate), Date.valueOf(endDate), MOBILE_QUESTION_TIER, entityType, entityRefId == null ? 0L : entityRefId, PROMPT_VERSION);
        return row == null ? new JudgeCoverage(0, 0, 0, 0) : row;
    }

    public List<CompetitorSummary> competitorSummaries(Long projectId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query("""
                SELECT c.id AS entity_ref_id,
                       c.competitor_name,
                       c.display_order,
                       c.qa_status,
                       COALESCE(SUM(s.expected_count), 0) AS expected_count,
                       COALESCE(SUM(s.success_count), 0) AS success_count,
                       COALESCE(SUM(s.recommended_count), 0) AS recommended_count,
                       COALESCE(SUM(s.first_recommend_count), 0) AS first_recommend_count
                  FROM project_competitor_config c
                  LEFT JOIN poll_entity_judge_daily_summary s
                    ON s.project_id = c.project_id
                   AND s.entity_type = 'competitor'
                   AND s.entity_ref_id = c.id
                   AND s.entity_config_version = c.config_version
                   AND s.batch_date BETWEEN ? AND ?
                   AND s.question_tier = ?
                   AND s.judge_prompt_version = ?
                 WHERE c.project_id = ?
                   AND c.status = 'active'
                 GROUP BY c.id, c.competitor_name, c.display_order, c.qa_status
                 ORDER BY c.display_order ASC, c.id ASC
                """, (rs, rowNum) -> new CompetitorSummary(
                rs.getLong("entity_ref_id"),
                rs.getString("competitor_name"),
                rs.getInt("display_order"),
                rs.getString("qa_status"),
                new JudgeCoverage(
                        rs.getLong("expected_count"),
                        rs.getLong("success_count"),
                        rs.getLong("recommended_count"),
                        rs.getLong("first_recommend_count")
                )
        ), Date.valueOf(startDate), Date.valueOf(endDate), MOBILE_QUESTION_TIER, PROMPT_VERSION, projectId);
    }

    public List<CompetitorSummary> latestCompetitorSummaries(Long projectId) {
        return jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                WITH latest AS (
                    SELECT pr.id,
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
                       AND %2$s
                ),
                latest_count AS (
                    SELECT COUNT(*) AS expected_count
                      FROM latest
                     WHERE rn = 1
                       AND search_triggered = 1
                )
                SELECT c.id AS entity_ref_id,
                       c.competitor_name,
                       c.display_order,
                       c.qa_status,
                       COALESCE(MAX(lc.expected_count), 0) AS expected_count,
                       COALESCE(SUM(CASE WHEN j.judge_status = 'success' THEN 1 ELSE 0 END), 0) AS success_count,
                       COALESCE(SUM(CASE WHEN j.judge_status = 'success' AND j.recommended = 1 THEN 1 ELSE 0 END), 0) AS recommended_count,
                       COALESCE(SUM(CASE WHEN j.judge_status = 'success' AND j.first_recommend = 1 THEN 1 ELSE 0 END), 0) AS first_recommend_count
                  FROM project_competitor_config c
                  CROSS JOIN latest_count lc
                  LEFT JOIN latest l
                    ON l.rn = 1
                   AND l.search_triggered = 1
                  LEFT JOIN poll_result_entity_judge j
                    ON j.poll_result_id = l.id
                   AND j.entity_type = 'competitor'
                   AND j.entity_ref_id = c.id
                   AND j.entity_config_version = c.config_version
                   AND j.judge_prompt_version = ?
                 WHERE c.project_id = ?
                   AND c.status = 'active'
                 GROUP BY c.id, c.competitor_name, c.display_order, c.qa_status
                 ORDER BY c.display_order ASC, c.id ASC
                """.formatted(canonicalPlatformSql(POLL_CHANNEL_SQL), EFFECTIVE_WEB_SEARCH_REQUEST_SQL), "pr"), (rs, rowNum) -> new CompetitorSummary(
                rs.getLong("entity_ref_id"),
                rs.getString("competitor_name"),
                rs.getInt("display_order"),
                rs.getString("qa_status"),
                new JudgeCoverage(
                        rs.getLong("expected_count"),
                        rs.getLong("success_count"),
                        rs.getLong("recommended_count"),
                        rs.getLong("first_recommend_count")
                )
        ), projectId, MOBILE_QUESTION_TIER, PROMPT_VERSION, projectId);
    }

    private JudgeCoverage latestCoverage(Long projectId, String entityType, Long entityRefId, String platformCode) {
        String platformClause = "";
        if (StringUtils.hasText(platformCode)) {
            platformClause = " AND %s IN (%s) ".formatted(POLL_CHANNEL_SQL, platformAliasSql(platformCode));
        }
        JudgeCoverage row = jdbcTemplate.queryForObject(MobileDashboardQuestionScopeSql.apply("""
                WITH latest AS (
                    SELECT pr.id,
                           pr.effective_hit,
                           pr.brand_in_answer,
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
                       AND %2$s
                       %3$s
                )
                SELECT COUNT(*) AS expected_count,
                       COALESCE(SUM(CASE WHEN j.judge_status = 'success' THEN 1 ELSE 0 END), 0) AS success_count,
                       COALESCE(SUM(CASE WHEN (l.effective_hit = 1 OR (l.effective_hit IS NULL AND l.brand_in_answer = 1)) AND j.judge_status = 'success' AND j.recommended = 1 THEN 1 ELSE 0 END), 0) AS recommended_count,
                       COALESCE(SUM(CASE WHEN (l.effective_hit = 1 OR (l.effective_hit IS NULL AND l.brand_in_answer = 1)) AND j.judge_status = 'success' AND j.recommended = 1 AND j.first_recommend = 1 THEN 1 ELSE 0 END), 0) AS first_recommend_count
                  FROM latest l
                  LEFT JOIN poll_result_entity_judge j
                    ON j.poll_result_id = l.id
                   AND j.entity_type = ?
                   AND j.entity_ref_id = ?
                   AND j.judge_prompt_version = ?
                 WHERE l.rn = 1
                   AND l.search_triggered = 1
                """.formatted(canonicalPlatformSql(POLL_CHANNEL_SQL), EFFECTIVE_WEB_SEARCH_REQUEST_SQL, platformClause), "pr"), (rs, rowNum) -> new JudgeCoverage(
                rs.getLong("expected_count"),
                rs.getLong("success_count"),
                rs.getLong("recommended_count"),
                rs.getLong("first_recommend_count")
        ), projectId, MOBILE_QUESTION_TIER, entityType, entityRefId == null ? 0L : entityRefId, PROMPT_VERSION);
        return row == null ? new JudgeCoverage(0, 0, 0, 0) : row;
    }

    private EntityJudgeRunVO judgeCandidates(List<PollCandidate> candidates) {
        int judged = 0;
        int skipped = 0;
        int failed = 0;
        for (PollCandidate candidate : candidates) {
            if (!StringUtils.hasText(candidate.responseText())) {
                skipped++;
                continue;
            }
            try {
                judgeOne(candidate);
                judged++;
            } catch (Exception ex) {
                failed++;
                markFocusFailed(candidate, ex.getMessage());
                log.warn("mobile entity judge failed pollResultId={} msg={}", candidate.id(), ex.getMessage());
            }
        }
        return new EntityJudgeRunVO(candidates.size(), judged, skipped, failed, 0, null);
    }

    private List<Long> loadPendingProjectIds(LocalDate startDate, LocalDate endDate, int limit) {
        return jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT pr.project_id
                 FROM poll_results pr
                 WHERE pr.status = 'completed'
                   AND pr.batch_date BETWEEN ? AND ?
                   AND pr.question_tier = ?
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                   AND %s
                   AND JSON_EXTRACT(pr.detail_json, '$.platform_response') IS NOT NULL
                   AND NOT EXISTS (
                         SELECT 1
                           FROM poll_result_entity_judge j
                          WHERE j.poll_result_id = pr.id
                            AND j.entity_type = 'focus_brand'
                            AND j.entity_ref_id = 0
                            AND j.judge_prompt_version = ?
                            AND j.entity_config_version = 1
                            AND j.judge_status = 'success'
                   )
                 GROUP BY pr.project_id
                 ORDER BY MIN(pr.batch_date) ASC, MIN(pr.id) ASC
                 LIMIT ?
                """.formatted(EFFECTIVE_WEB_SEARCH_RESULT_SQL), "pr"), (rs, rowNum) -> rs.getLong("project_id"),
                Date.valueOf(startDate), Date.valueOf(endDate), MOBILE_QUESTION_TIER, PROMPT_VERSION, limit);
    }

    private List<PollCandidate> loadCandidates(Long projectId, LocalDate startDate, LocalDate endDate, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(MobileDashboardQuestionScopeSql.apply("""
                WHERE pr.status = 'completed'
                  AND ENABLED_MONITORING_QUESTION_SCOPE
                  AND %s
                  AND JSON_EXTRACT(pr.detail_json, '$.platform_response') IS NOT NULL
                  AND NOT EXISTS (
                        SELECT 1
                          FROM poll_result_entity_judge j
                         WHERE j.poll_result_id = pr.id
                           AND j.entity_type = 'focus_brand'
                           AND j.entity_ref_id = 0
                           AND j.judge_prompt_version = ?
                           AND j.entity_config_version = 1
                           AND j.judge_status = 'success'
                  )
                """.formatted(EFFECTIVE_WEB_SEARCH_RESULT_SQL), "pr"));
        args.add(PROMPT_VERSION);
        if (projectId != null) {
            where.append(" AND pr.project_id = ? ");
            args.add(projectId);
        }
        where.append(" AND pr.question_tier = ? ");
        args.add(MOBILE_QUESTION_TIER);
        if (startDate != null) {
            where.append(" AND pr.batch_date >= ? ");
            args.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            where.append(" AND pr.batch_date <= ? ");
            args.add(Date.valueOf(endDate));
        }
        args.add(limit);
        return jdbcTemplate.query("""
                SELECT pr.id,
                       pr.project_id,
                       pr.keyword_result_id,
                       pr.keyword_text_snapshot,
                       pr.batch_date,
                       pr.question_tier,
                       pr.platform_id,
                       %s AS platform_code,
                       CASE WHEN pr.effective_hit = 1 OR (pr.effective_hit IS NULL AND pr.brand_in_answer = 1)
                            THEN 1 ELSE 0 END AS effective_hit,
                       p.brand_name,
                       p.project_aliases,
                       JSON_UNQUOTE(JSON_EXTRACT(pr.detail_json, '$.platform_response')) AS response_text
                  FROM poll_results pr
                  JOIN project p ON p.id = pr.project_id
                """.formatted(POLL_CHANNEL_SQL) + where + """
                 ORDER BY pr.batch_date DESC, pr.id DESC
                 LIMIT ?
                """, (rs, rowNum) -> mapCandidate(rs), args.toArray());
    }

    private PollCandidate mapCandidate(ResultSet rs) throws SQLException {
        return new PollCandidate(
                rs.getLong("id"),
                rs.getLong("project_id"),
                nullableLong(rs, "keyword_result_id"),
                rs.getString("keyword_text_snapshot"),
                rs.getDate("batch_date").toLocalDate(),
                rs.getString("question_tier"),
                nullableLong(rs, "platform_id"),
                rs.getString("platform_code"),
                nullableBoolean(rs, "effective_hit"),
                rs.getString("brand_name"),
                rs.getString("project_aliases"),
                rs.getString("response_text")
        );
    }

    @Transactional
    void judgeOne(PollCandidate candidate) throws Exception {
        List<ProjectCompetitorConfigService.CompetitorEntity> competitors = competitorConfigService.activeCompetitors(candidate.projectId());
        MobileEntityMentionMatcher.MatchResult matchResult = mentionMatcher.match(candidate.responseText(), projectAliases(candidate), competitors);
        if (!Boolean.TRUE.equals(candidate.effectiveHit()) && !matchResult.anyMatched()) {
            upsertDeterministicNoEntityHit(candidate, competitors);
            recomputeDaily(candidate.projectId(), candidate.batchDate(), candidate.questionTier(), candidate.platformCode());
            return;
        }
        JudgePayload payload = invokeJudge(candidate, competitors, matchResult);
        String model = payload.model();
        upsertFocus(candidate, payload.focus(), model, payload.rawJson());
        Map<Long, EntityResult> byCompetitorId = new LinkedHashMap<>();
        for (EntityResult result : payload.competitors()) {
            if (result.entityRefId() != null) {
                byCompetitorId.put(result.entityRefId(), result);
            }
        }
        for (ProjectCompetitorConfigService.CompetitorEntity competitor : competitors) {
            EntityResult result = byCompetitorId.getOrDefault(competitor.id(), EntityResult.empty(competitor.id()));
            upsertCompetitor(candidate, competitor, result, model, payload.rawJson());
        }
        recomputeDaily(candidate.projectId(), candidate.batchDate(), candidate.questionTier(), candidate.platformCode());
    }

    private JudgePayload invokeJudge(PollCandidate candidate,
                                     List<ProjectCompetitorConfigService.CompetitorEntity> competitors,
                                     MobileEntityMentionMatcher.MatchResult matchResult) throws Exception {
        List<AiPlatformConfig> judgePlatforms = loadJudgePlatforms().stream()
                .map(this::useLowModel)
                .toList();
        if (judgePlatforms.isEmpty()) {
            throw new BizException(503, "No judge model configured");
        }
        String prompt = buildPrompt(candidate, competitors);
        LlmCallRequest callRequest = LlmCallRequest.routed(new LlmRouteRequest(
                LlmFeature.MOBILE_JUDGE,
                "你是严格的 GEO 移动数据看板实体裁判。只输出合法 JSON,不要输出 markdown 或解释。",
                prompt,
                0D,
                10_000,
                45_000,
                LlmModelConfig.LONG_FORM_MAX_REQUEST_TIMEOUT_MS,
                0,
                1200,
                true,
                1,
                0,
                judgePlatforms,
                true
        )).withMeasurementContext(new LlmCallMeasurementContext(
                MobileEntityJudgeBudgetService.RUN_ID_PREFIX + candidate.projectId() + ":" + candidate.batchDate(),
                null,
                candidate.projectId(),
                LlmObservationScope.PROJECT,
                sha256(prompt)
        ));
        LlmCallResult callResult = llmCallFacade.execute(callRequest);
        LlmRouteResult routeResult = callResult.routeResult();
        JsonNode root = objectMapper.readTree(routeResult.responseText());
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("ENTITY_JUDGE_RESPONSE_NOT_OBJECT");
        }
        EntityResult focus = parseFocus(root.path("focus_brand"), candidate, matchResult.focusMatched());
        List<EntityResult> competitorResults = parseCompetitors(root.path("competitors"), competitors);
        return new JudgePayload(focus, competitorResults, routeResult.modelId(), routeResult.responseText());
    }

    private List<AiPlatformConfig> loadJudgePlatforms() {
        Set<String> codes = judgeRuntimeConfig.platformCodeSet();
        if (codes.isEmpty()) {
            return List.of();
        }
        List<AiPlatformConfig> platforms = aiPlatformConfigMapper.selectList(
                new LambdaQueryWrapper<AiPlatformConfig>()
                        .eq(AiPlatformConfig::getEnabled, true)
                        .in(AiPlatformConfig::getPlatformCode, codes)
        );
        Map<String, AiPlatformConfig> byCode = platforms.stream()
                .filter(platform -> platform != null && StringUtils.hasText(platform.getPlatformCode()))
                .collect(Collectors.toMap(
                        platform -> LlmPlatformCodeFilters.normalize(platform.getPlatformCode()),
                        Function.identity(),
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
        return codes.stream()
                .map(byCode::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private AiPlatformConfig useLowModel(AiPlatformConfig platform) {
        if (platform != null && StringUtils.hasText(platform.getLowModelId())) {
            platform.setModelId(platform.getLowModelId().trim());
        }
        return platform;
    }

    private String buildPrompt(PollCandidate candidate,
                               List<ProjectCompetitorConfigService.CompetitorEntity> competitors) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("question", candidate.keywordText());
        input.put("answer", candidate.responseText());
        input.put("focus_brand", Map.of(
                "canonical_name", safe(candidate.brandName()),
                "aliases", projectAliases(candidate)
        ));
        input.put("competitors", competitors.stream()
                .map(item -> Map.of(
                        "id", item.id(),
                        "canonical_name", item.name(),
                        "aliases", item.aliases()
                ))
                .toList());
        return """
                请从 AI 回答中抽取焦点品牌和已配置竞品的推荐与首推信息。

                输入:
                %s

                只输出 JSON:
                {
                  "focus_brand": {
                    "recommended": true|false,
                    "rank_position": number|null,
                    "first_recommend": true|false,
                    "evidence": string|null,
                    "matched_alias": string|null,
                    "confidence": 0.0-1.0
                  },
                  "competitors": [
                    {
                      "id": number,
                      "recommended": true|false,
                      "rank_position": number|null,
                      "first_recommend": true|false,
                      "evidence": string|null,
                      "matched_alias": string|null,
                      "confidence": 0.0-1.0
                    }
                  ]
                }

                规则:
                1. focus_brand 是否被提及不由你判定;系统已有 effective_hit 作为唯一提及口径。你只判断它是否被主动推荐和是否首推。
                2. recommended 只在回答主动建议、优先推荐、明确推荐选择/购买/咨询该实体时为 true;仅列入候选名单、罗列品牌名或中性比较不算推荐。
                3. first_recommend 只在该实体是明确推荐列表第一位或唯一首选时为 true;否则为 false。
                4. rank_position 是回答中可识别的推荐位次,不能识别填 null。
                5. competitors 只能返回输入 competitors 中的 id,不能发明新竞品;未推荐的竞品可省略,系统会按 false 补齐。
                6. evidence/matched_alias 必须来自回答原文或输入名称/别名。
                """.formatted(objectMapper.writeValueAsString(input));
    }

    private EntityResult parseFocus(JsonNode node, PollCandidate candidate, boolean focusMatchedByLocalMatcher) {
        EntityResult parsed = parseEntity(node, 0L);
        if (!Boolean.TRUE.equals(candidate.effectiveHit()) && !focusMatchedByLocalMatcher) {
            return new EntityResult(0L, false, false, null, parsed.evidence(), parsed.matchedAlias(), parsed.confidence());
        }
        return parsed;
    }

    private List<EntityResult> parseCompetitors(JsonNode nodes, List<ProjectCompetitorConfigService.CompetitorEntity> competitors) {
        if (nodes == null || !nodes.isArray()) {
            return List.of();
        }
        List<Long> allowedIds = competitors.stream().map(ProjectCompetitorConfigService.CompetitorEntity::id).toList();
        List<EntityResult> results = new ArrayList<>();
        for (JsonNode node : nodes) {
            Long id = node.path("id").canConvertToLong() ? node.path("id").asLong() : null;
            if (id == null || !allowedIds.contains(id)) {
                continue;
            }
            results.add(parseEntity(node, id));
        }
        return results;
    }

    private EntityResult parseEntity(JsonNode node, Long entityRefId) {
        if (node == null || !node.isObject()) {
            return EntityResult.empty(entityRefId);
        }
        boolean recommended = node.path("recommended").asBoolean(false);
        Integer rank = node.path("rank_position").isNumber() ? Math.max(1, node.path("rank_position").asInt()) : null;
        boolean first = node.path("first_recommend").asBoolean(false) || (recommended && rank != null && rank == 1);
        if (!recommended) {
            first = false;
            rank = null;
        }
        return new EntityResult(
                entityRefId,
                recommended,
                first,
                rank,
                trimTo(node.path("evidence").asText(null), 500),
                trimTo(node.path("matched_alias").asText(null), 128),
                node.path("confidence").isNumber() ? Math.max(0D, Math.min(1D, node.path("confidence").asDouble())) : null
        );
    }

    private void upsertFocus(PollCandidate candidate, EntityResult result, String model, String rawJson) {
        upsertJudgeRow(candidate, FOCUS_BRAND, 0L, 1, result, model, rawJson, "success", null);
    }

    private void upsertCompetitor(PollCandidate candidate,
                                  ProjectCompetitorConfigService.CompetitorEntity competitor,
                                  EntityResult result,
                                  String model,
                                  String rawJson) {
        upsertJudgeRow(candidate, COMPETITOR, competitor.id(), competitor.configVersion(), result, model, rawJson, "success", null);
    }

    private void upsertDeterministicNoEntityHit(PollCandidate candidate,
                                                List<ProjectCompetitorConfigService.CompetitorEntity> competitors) {
        EntityResult focus = deterministicNoEntityHit(0L);
        String rawJson = """
                {"reason":"no_tracked_entity_matched","source":"local_matcher"}
                """;
        upsertFocus(candidate, focus, "deterministic_no_entity_hit", rawJson);
        if (competitors != null) {
            for (ProjectCompetitorConfigService.CompetitorEntity competitor : competitors) {
                upsertCompetitor(candidate, competitor, deterministicNoEntityHit(competitor.id()),
                        "deterministic_no_entity_hit", rawJson);
            }
        }
    }

    private EntityResult deterministicNoEntityHit(Long entityRefId) {
        return new EntityResult(entityRefId, false, false, null, "no_tracked_entity_matched", null, 1D);
    }

    private void upsertJudgeRow(PollCandidate candidate,
                                String entityType,
                                Long entityRefId,
                                int configVersion,
                                EntityResult result,
                                String model,
                                String rawJson,
                                String status,
                                String error) {
        jdbcTemplate.update("""
                INSERT INTO poll_result_entity_judge (
                  poll_result_id, project_id, keyword_result_id, batch_date, question_tier, platform_id, platform_code,
                  entity_type, entity_ref_id, entity_config_version, judge_prompt_version, judge_model, judge_status,
                  recommended, first_recommend, rank_position, evidence, matched_alias, confidence,
                  raw_response_json, judge_error, judged_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  judge_model = VALUES(judge_model),
                  judge_status = VALUES(judge_status),
                  recommended = VALUES(recommended),
                  first_recommend = VALUES(first_recommend),
                  rank_position = VALUES(rank_position),
                  evidence = VALUES(evidence),
                  matched_alias = VALUES(matched_alias),
                  confidence = VALUES(confidence),
                  raw_response_json = VALUES(raw_response_json),
                  judge_error = VALUES(judge_error),
                  judged_at = CURRENT_TIMESTAMP
                """,
                candidate.id(), candidate.projectId(), candidate.keywordResultId(), Date.valueOf(candidate.batchDate()),
                safeTier(candidate.questionTier()), candidate.platformId(), candidate.platformCode(), entityType,
                entityRefId == null ? 0L : entityRefId, configVersion, PROMPT_VERSION, model, status,
                result.recommended(), result.firstRecommend(), result.rankPosition(), result.evidence(),
                result.matchedAlias(), result.confidence(), rawJson, error);
    }

    private void markFocusFailed(PollCandidate candidate, String error) {
        upsertJudgeRow(candidate, FOCUS_BRAND, 0L, 1, EntityResult.empty(0L), null, "{}", "failed", trimTo(error, 500));
    }

    void recomputeDaily(Long projectId, LocalDate batchDate, String questionTier, String platformCode) {
        String tier = safeTier(questionTier);
        String platform = StringUtils.hasText(platformCode) ? platformCode.trim() : "";
        int expected = countExpected(projectId, batchDate, tier, platform);
        List<SummaryRow> rows = jdbcTemplate.query(MobileDashboardQuestionScopeSql.apply("""
                SELECT j.entity_type,
                       j.entity_ref_id,
                       j.entity_config_version,
                       COUNT(*) AS success_count,
                       SUM(CASE WHEN j.recommended = 1 THEN 1 ELSE 0 END) AS recommended_count,
                       SUM(CASE WHEN j.first_recommend = 1 THEN 1 ELSE 0 END) AS first_recommend_count,
                       GROUP_CONCAT(j.id ORDER BY j.id SEPARATOR ',') AS ids
                  FROM poll_result_entity_judge j
                  JOIN poll_results pr ON pr.id = j.poll_result_id
                 WHERE j.project_id = ?
                   AND j.batch_date = ?
                   AND j.question_tier = ?
                   AND COALESCE(j.platform_code, '') = ?
                   AND j.judge_prompt_version = ?
                   AND j.judge_status = 'success'
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                 GROUP BY j.entity_type, j.entity_ref_id, j.entity_config_version
                """, "pr"), (rs, rowNum) -> new SummaryRow(
                rs.getString("entity_type"),
                rs.getLong("entity_ref_id"),
                rs.getInt("entity_config_version"),
                rs.getInt("success_count"),
                rs.getInt("recommended_count"),
                rs.getInt("first_recommend_count"),
                rs.getString("ids")
        ), projectId, Date.valueOf(batchDate), tier, platform, PROMPT_VERSION);
        for (SummaryRow row : rows) {
            upsertSummary(projectId, batchDate, tier, platform, expected, row);
        }
    }

    private int countExpected(Long projectId, LocalDate batchDate, String questionTier, String platformCode) {
        Integer value = jdbcTemplate.queryForObject(MobileDashboardQuestionScopeSql.apply("""
                SELECT COUNT(1)
                  FROM poll_results pr
                 WHERE pr.project_id = ?
                   AND pr.batch_date = ?
                   AND pr.question_tier = ?
                   AND COALESCE(pr.platform_code, '') = ?
                   AND pr.status = 'completed'
                   AND ENABLED_MONITORING_QUESTION_SCOPE
                """, "pr"), Integer.class, projectId, Date.valueOf(batchDate), questionTier, platformCode);
        return value == null ? 0 : value;
    }

    private void upsertSummary(Long projectId, LocalDate batchDate, String tier, String platform, int expected, SummaryRow row) {
        jdbcTemplate.update("""
                INSERT INTO poll_entity_judge_daily_summary (
                  project_id, batch_date, question_tier, platform_code, entity_type, entity_ref_id,
                  entity_config_version, judge_prompt_version, expected_count, success_count,
                  recommended_count, first_recommend_count, source_checksum, recomputed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  expected_count = VALUES(expected_count),
                  success_count = VALUES(success_count),
                  recommended_count = VALUES(recommended_count),
                  first_recommend_count = VALUES(first_recommend_count),
                  source_checksum = VALUES(source_checksum),
                  recomputed_at = CURRENT_TIMESTAMP
                """, projectId, Date.valueOf(batchDate), tier, platform, row.entityType(), row.entityRefId(),
                row.entityConfigVersion(), PROMPT_VERSION, expected, row.successCount(), row.recommendedCount(),
                row.firstRecommendCount(), sha256(row.ids()));
    }

    public boolean coverageReady(JudgeCoverage coverage) {
        return coverage != null
                && coverage.expectedCount() > 0
                && coverage.successCount() * 100 >= coverage.expectedCount() * COVERAGE_THRESHOLD_PERCENT;
    }

    public int coverageThresholdPercent() {
        return COVERAGE_THRESHOLD_PERCENT;
    }

    private List<String> projectAliases(PollCandidate candidate) {
        List<String> aliases = new ArrayList<>();
        if (StringUtils.hasText(candidate.brandName())) {
            aliases.add(candidate.brandName().trim());
        }
        if (StringUtils.hasText(candidate.projectAliases())) {
            for (String alias : candidate.projectAliases().split("[,，;；\\n]")) {
                if (StringUtils.hasText(alias)) {
                    aliases.add(alias.trim());
                }
            }
        }
        return aliases.stream().distinct().toList();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static String safeTier(String tier) {
        return StringUtils.hasText(tier) ? tier.trim().toUpperCase(Locale.ROOT) : "A";
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String platformAliasSql(String code) {
        String normalized = StringUtils.hasText(code) ? code.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalized) {
            case "doubao", "doubao_web" -> "'doubao','doubao_web'";
            case "deepseek", "deepseek_ark_web" -> "'deepseek','deepseek_ark_web'";
            case "tongyi", "qwen", "qwen_web" -> "'tongyi','qwen','qwen_web'";
            case "wenxin", "ernie" -> "'wenxin','ernie'";
            case "yuanbao", "hunyuan", "tencent_search_web" -> "'yuanbao','hunyuan','tencent_search_web'";
            default -> "'" + normalized.replace("'", "''") + "'";
        };
    }

    private static String canonicalPlatformSql(String expression) {
        return """
                CASE
                    WHEN %1$s IN ('doubao', 'doubao_web') THEN 'doubao'
                    WHEN %1$s IN ('deepseek', 'deepseek_ark_web') THEN 'deepseek'
                    WHEN %1$s IN ('tongyi', 'qwen', 'qwen_web') THEN 'tongyi'
                    WHEN %1$s IN ('yuanbao', 'hunyuan', 'tencent_search_web') THEN 'yuanbao'
                    ELSE %1$s
                END
                """.formatted(expression);
    }

    private static String trimTo(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record PollCandidate(Long id,
                                 Long projectId,
                                 Long keywordResultId,
                                 String keywordText,
                                 LocalDate batchDate,
                                 String questionTier,
                                 Long platformId,
                                 String platformCode,
                                 Boolean effectiveHit,
                                 String brandName,
                                 String projectAliases,
                                 String responseText) {
    }

    private record EntityResult(Long entityRefId,
                                boolean recommended,
                                boolean firstRecommend,
                                Integer rankPosition,
                                String evidence,
                                String matchedAlias,
                                Double confidence) {
        static EntityResult empty(Long entityRefId) {
            return new EntityResult(entityRefId, false, false, null, null, null, null);
        }
    }

    private record JudgePayload(EntityResult focus, List<EntityResult> competitors, String model, String rawJson) {
    }

    public record JudgeCoverage(long expectedCount,
                                long successCount,
                                long recommendedCount,
                                long firstRecommendCount) {
    }

    public record CompetitorSummary(Long entityRefId,
                                    String competitorName,
                                    int displayOrder,
                                    String qaStatus,
                                    JudgeCoverage coverage) {
    }

    private record SummaryRow(String entityType,
                              Long entityRefId,
                              int entityConfigVersion,
                              int successCount,
                              int recommendedCount,
                              int firstRecommendCount,
                              String ids) {
    }
}
