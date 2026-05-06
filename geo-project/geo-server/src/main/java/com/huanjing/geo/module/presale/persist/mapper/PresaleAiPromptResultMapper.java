package com.huanjing.geo.module.presale.persist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.presale.generate.PlatformIntentJudgeAggregateRow;
import com.huanjing.geo.module.presale.generate.PlatformIntentSampleRow;
import com.huanjing.geo.module.presale.generate.PresaleJudgeCandidateRow;
import com.huanjing.geo.module.presale.generate.PromptTemplateIntentStatRow;
import com.huanjing.geo.module.presale.dto.request.PresalePromptTraceQueryRequest;
import com.huanjing.geo.module.presale.persist.entity.PresaleAiPromptResult;
import com.huanjing.geo.module.presale.service.PresalePromptTraceRow;
import com.huanjing.geo.module.presale.service.PromptTraceFilterOptionRow;
import com.huanjing.geo.module.presale.service.VersionPromptTraceCountRow;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PresaleAiPromptResultMapper extends BaseMapper<PresaleAiPromptResult> {

    String PROMPT_TRACE_SELECT = "SELECT " +
            "r.id AS promptResultId, " +
            "v.report_id AS reportId, " +
            "r.version_id AS versionId, " +
            "v.version_no AS versionNo, " +
            "r.batch_no AS batchNo, " +
            "r.platform_code AS platformCode, " +
            "COALESCE(qc.platform_name_snapshot, p.platform_name, r.platform_code) AS platformName, " +
            "pt.category AS category, " +
            "r.competitor_name AS competitorName, " +
            "r.request_prompt_content AS requestPromptContent, " +
            "r.is_mentioned AS isMentioned, " +
            "r.ranking AS ranking, " +
            "r.sentiment AS sentiment, " +
            "r.mentioned_competitors AS mentionedCompetitors, " +
            "r.scene_advantages AS sceneAdvantages, " +
            "r.top_keywords_json AS topKeywordsJson, " +
            "r.negative_evidence_json AS negativeEvidenceJson, " +
            "qc.request_prompt_content AS queryPromptContent, " +
            "qc.raw_response AS queryRawResponse, " +
            "qc.call_status AS queryCallStatus, " +
            "qc.failure_reason AS queryFailureReason, " +
            "qc.duration_ms AS queryDurationMs, " +
            "CASE WHEN qc.id IS NULL THEN NULL ELSE COALESCE(qc.model_id_snapshot, p.low_model_id, p.model_id) END AS queryModelName, " +
            "CASE WHEN qc.id IS NOT NULL AND qc.model_id_snapshot IS NULL THEN 1 ELSE 0 END AS queryModelSnapshotInferred, " +
            "ac.request_prompt_content AS analyzePromptContent, " +
            "ac.raw_response AS analyzeRawResponse, " +
            "ac.call_status AS analyzeCallStatus, " +
            "ac.failure_reason AS analyzeFailureReason, " +
            "ac.duration_ms AS analyzeDurationMs, " +
            "CASE WHEN ac.id IS NULL THEN NULL ELSE COALESCE(ac.model_id_snapshot, p.low_model_id, p.model_id) END AS analyzeModelName, " +
            "CASE WHEN ac.id IS NOT NULL AND ac.model_id_snapshot IS NULL THEN 1 ELSE 0 END AS analyzeModelSnapshotInferred ";

    String PROMPT_TRACE_FROM = "FROM presale_ai_prompt_result r " +
            "INNER JOIN presale_report_version v ON v.id = r.version_id " +
            "LEFT JOIN presale_report_version_prompt_template pt ON pt.id = r.prompt_template_id " +
            "LEFT JOIN presale_ai_call qc ON qc.id = r.query_call_id AND qc.stage = 'QUERY' " +
            "LEFT JOIN presale_ai_call ac ON ac.id = r.analyze_call_id AND ac.stage = 'ANALYZE' " +
            "LEFT JOIN ai_platform_config p ON p.platform_code = r.platform_code ";

    String PROMPT_TRACE_STATUS_FILTER = "<if test=\"req.status != null and req.status != ''\">" +
            "  <choose>" +
            "    <when test=\"req.status == 'SUCCESS'\">" +
            "      AND qc.call_status = 'SUCCESS' AND ac.call_status = 'SUCCESS' AND r.is_mentioned IS NOT NULL " +
            "    </when>" +
            "    <when test=\"req.status == 'ANALYZE_FAILED'\">" +
            "      AND qc.call_status = 'SUCCESS' AND (ac.id IS NULL OR ac.call_status &lt;&gt; 'SUCCESS' OR r.is_mentioned IS NULL) " +
            "    </when>" +
            "    <when test=\"req.status == 'QUERY_FAILED'\">" +
            "      AND (qc.id IS NULL OR qc.call_status &lt;&gt; 'SUCCESS') " +
            "    </when>" +
            "  </choose>" +
            "</if>";

    @Select({"<script>",
            PROMPT_TRACE_SELECT,
            PROMPT_TRACE_FROM,
            "WHERE v.report_id = #{reportId} ",
            "AND v.version_no = #{versionNo} ",
            "AND r.batch_no IN (1, 2) ",
            "<if test=\"req.platformCode != null and req.platformCode != ''\">",
            "  AND r.platform_code = #{req.platformCode} ",
            "</if>",
            "<if test=\"req.batchNo != null\">",
            "  AND r.batch_no = #{req.batchNo} ",
            "</if>",
            "<if test=\"req.category != null and req.category != ''\">",
            "  AND pt.category = #{req.category} ",
            "</if>",
            "<if test=\"req.keyword != null and req.keyword != ''\">",
            "  AND (r.request_prompt_content LIKE CONCAT('%', #{req.keyword}, '%') ",
            "    OR qc.raw_response LIKE CONCAT('%', #{req.keyword}, '%') ",
            "    OR qc.failure_reason LIKE CONCAT('%', #{req.keyword}, '%') ",
            "    OR ac.failure_reason LIKE CONCAT('%', #{req.keyword}, '%')) ",
            "</if>",
            PROMPT_TRACE_STATUS_FILTER,
            "ORDER BY r.batch_no ASC, r.platform_code ASC, r.prompt_template_id ASC, r.competitor_name ASC",
            "</script>"})
    Page<PresalePromptTraceRow> selectPromptTracePage(Page<PresalePromptTraceRow> page,
                                                      @Param("reportId") Long reportId,
                                                      @Param("versionNo") Integer versionNo,
                                                      @Param("req") PresalePromptTraceQueryRequest req);

    @Select({"<script>",
            PROMPT_TRACE_SELECT,
            PROMPT_TRACE_FROM,
            "WHERE v.report_id = #{reportId} ",
            "AND v.version_no = #{versionNo} ",
            "AND r.id = #{promptResultId} ",
            "AND r.batch_no IN (1, 2) ",
            "LIMIT 1",
            "</script>"})
    PresalePromptTraceRow selectPromptTraceDetail(@Param("reportId") Long reportId,
                                                  @Param("versionNo") Integer versionNo,
                                                  @Param("promptResultId") Long promptResultId);

    @Select("SELECT DISTINCT " +
            "r.platform_code AS platformCode, " +
            "COALESCE(qc.platform_name_snapshot, p.platform_name, r.platform_code) AS platformName, " +
            "pt.category AS category " +
            PROMPT_TRACE_FROM +
            "WHERE r.version_id = #{versionId} " +
            "AND r.batch_no IN (1, 2) " +
            "ORDER BY r.platform_code ASC, pt.category ASC")
    List<PromptTraceFilterOptionRow> selectPromptTraceFilterOptions(@Param("versionId") Long versionId);

    @Select({"<script>",
            "SELECT version_id AS versionId, COUNT(*) AS promptTraceCount ",
            "FROM presale_ai_prompt_result ",
            "WHERE batch_no IN (1, 2) ",
            "AND version_id IN ",
            "<foreach collection=\"versionIds\" item=\"versionId\" open=\"(\" separator=\",\" close=\")\">",
            "  #{versionId}",
            "</foreach>",
            "GROUP BY version_id",
            "</script>"})
    List<VersionPromptTraceCountRow> selectPromptTraceCountsByVersionIds(@Param("versionIds") List<Long> versionIds);

    // NOTE: 本 SQL 依赖 Prompt 快照 category 使用中文字面值('对比型')。
    //       若未来将模板 category 迁移为英文枚举,本方法和
    //       PresaleJudgeService.CATEGORY_COMPARISON 常量需同步修改。
    //       相关 category 字面值分布在 3 处:此 SQL、V79 种子 SQL、
    //       PresaleJudgeService 常量定义。
    @Select("SELECT " +
            "r.platform_code AS platformCode, " +
            "pt.category AS intentLabel, " +
            "CASE WHEN r.is_mentioned IS NULL THEN 'FAILED' ELSE 'SUCCESS' END AS callStatus, " +
            "0 AS isExcluded, " +
            "r.is_mentioned AS isMentioned " +
            "FROM presale_ai_prompt_result r " +
            "INNER JOIN presale_report_version_prompt_template pt ON pt.id = r.prompt_template_id " +
            "WHERE r.version_id = #{versionId} " +
            "AND ( " +
            "  (pt.category = '对比型' AND r.batch_no = 2) " +
            "  OR (pt.category <> '对比型' AND r.batch_no = 1) " +
            ")")
    List<PlatformIntentSampleRow> selectIntentSamplesByVersionId(@Param("versionId") Long versionId);

    @Select("SELECT " +
            "category AS intentLabel, " +
            "has_competitor_var AS hasCompetitorVar, " +
            "COUNT(*) AS templateCount " +
            "FROM presale_prompt_template " +
            "WHERE enabled = 1 " +
            "AND template_version = #{templateVersion} " +
            "GROUP BY category, has_competitor_var")
    List<PromptTemplateIntentStatRow> selectTemplateIntentStats(@Param("templateVersion") String templateVersion);

    @Select("SELECT " +
            "r.id AS promptResultId, " +
            "r.version_id AS versionId, " +
            "r.batch_no AS batchNo, " +
            "r.platform_code AS platformCode, " +
            "r.prompt_template_id AS promptTemplateId, " +
            "pt.category AS category, " +
            "r.competitor_name AS competitorName, " +
            "r.request_prompt_content AS requestPromptContent, " +
            "qc.raw_response AS queryAnswer " +
            "FROM presale_ai_prompt_result r " +
            "INNER JOIN presale_report_version_prompt_template pt ON pt.id = r.prompt_template_id " +
            "LEFT JOIN presale_ai_call qc ON qc.id = r.query_call_id " +
            "WHERE r.version_id = #{versionId} " +
            "AND r.batch_no = #{batchNo} " +
            "AND pt.category = #{category} " +
            "ORDER BY r.platform_code ASC, r.prompt_template_id ASC, r.competitor_name ASC")
    List<PresaleJudgeCandidateRow> selectJudgeCandidatesByVersionAndCategory(@Param("versionId") Long versionId,
                                                                              @Param("batchNo") Integer batchNo,
                                                                              @Param("category") String category);

    @Select("SELECT " +
            "agg.platform_code AS platformCode, " +
            "agg.category AS category, " +
            "agg.cell_score AS cellScore, " +
            "agg.stance AS stance, " +
            "agg.sample_count AS sampleCount " +
            "FROM (" +
            "  SELECT " +
            "    j.platform_code AS platform_code, " +
            "    'COGNITIVE' AS category, " +
            "    CASE WHEN SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.attribute_hit_rate IS NOT NULL AND j.sentiment_score IS NOT NULL THEN 1 ELSE 0 END) < 3 " +
            "         THEN NULL " +
            "         ELSE ROUND( " +
            "           AVG(CASE WHEN j.judge_status = 'SUCCESS' AND j.attribute_hit_rate IS NOT NULL AND j.sentiment_score IS NOT NULL THEN j.attribute_hit_rate END) " +
            "           * (AVG(CASE WHEN j.judge_status = 'SUCCESS' AND j.attribute_hit_rate IS NOT NULL AND j.sentiment_score IS NOT NULL THEN j.sentiment_score END) + 1) / 2 * 100, " +
            "           2 " +
            "         ) " +
            "    END AS cell_score, " +
            "    NULL AS stance, " +
            "    SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.attribute_hit_rate IS NOT NULL AND j.sentiment_score IS NOT NULL THEN 1 ELSE 0 END) AS sample_count " +
            "  FROM presale_ai_prompt_judge_result j " +
            "  WHERE j.version_id = #{versionId} " +
            "    AND j.category = 'COGNITIVE' " +
            "  GROUP BY j.platform_code " +
            "  UNION ALL " +
            "  SELECT " +
            "    c.platform_code AS platform_code, " +
            "    'COMPARISON' AS category, " +
            "    CASE WHEN c.denom = 0 THEN NULL ELSE ROUND(((c.target_cnt - c.competitor_cnt) * 50.0 / c.denom) + 50, 2) END AS cell_score, " +
            // stance 平局处理规则(参考补充纪要 2.3 节 + 未明确的边界):
            //   target=competitor 且 >=tie      → tie   (纪要明确)
            //   target=tie 且 >competitor       → target(纪要明确,倾向性优先)
            //   competitor=tie 且 >target       → competitor(纪要明确,倾向性优先)
            //   target>其余两者                  → target
            //   competitor>其余两者              → competitor
            //   三方并列(target=competitor=tie) → tie   (纪要未明确,默认中立)
            //   tie 独大(tie>target 且 tie>competitor) → tie (纪要未明确,默认中立)
            "    CASE " +
            "      WHEN c.denom = 0 THEN NULL " +
            "      WHEN c.target_cnt = c.competitor_cnt AND c.target_cnt >= c.tie_cnt THEN 'tie' " +
            "      WHEN c.target_cnt = c.tie_cnt AND c.target_cnt > c.competitor_cnt THEN 'target' " +
            "      WHEN c.competitor_cnt = c.tie_cnt AND c.competitor_cnt > c.target_cnt THEN 'competitor' " +
            "      WHEN c.target_cnt > c.competitor_cnt AND c.target_cnt > c.tie_cnt THEN 'target' " +
            "      WHEN c.competitor_cnt > c.target_cnt AND c.competitor_cnt > c.tie_cnt THEN 'competitor' " +
            "      ELSE 'tie' " +
            "    END AS stance, " +
            "    c.denom AS sample_count " +
            "  FROM (" +
            "    SELECT " +
            "      j.platform_code AS platform_code, " +
            "      SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.preferred_brand = 'target' THEN 1 ELSE 0 END) AS numer, " +
            "      SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.preferred_brand IN ('target', 'competitor', 'tie') THEN 1 ELSE 0 END) AS denom, " +
            "      SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.preferred_brand = 'target' THEN 1 ELSE 0 END) AS target_cnt, " +
            "      SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.preferred_brand = 'competitor' THEN 1 ELSE 0 END) AS competitor_cnt, " +
            "      SUM(CASE WHEN j.judge_status = 'SUCCESS' AND j.preferred_brand = 'tie' THEN 1 ELSE 0 END) AS tie_cnt " +
            "    FROM presale_ai_prompt_judge_result j " +
            "    WHERE j.version_id = #{versionId} " +
            "      AND j.category = 'COMPARISON' " +
            "    GROUP BY j.platform_code " +
            "  ) c " +
            ") agg " +
            "ORDER BY agg.platform_code ASC, agg.category ASC")
    List<PlatformIntentJudgeAggregateRow> selectJudgeAggregatesByVersionId(@Param("versionId") Long versionId);
}
