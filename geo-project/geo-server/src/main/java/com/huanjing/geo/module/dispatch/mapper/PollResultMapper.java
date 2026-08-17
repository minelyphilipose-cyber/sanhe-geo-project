package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PollResultMapper extends BaseMapper<PollResult> {

    @Select("""
            SELECT *
            FROM poll_results
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollResult selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE poll_results
               SET latest_attempt_id = #{value.latestAttemptId},
                   effective_attempt_id = #{value.effectiveAttemptId},
                   latest_attempt_status = #{value.latestAttemptStatus},
                   execution_finalized = #{value.executionFinalized},
                   retry_chain_status = #{value.retryChainStatus},
                   result_code = #{value.resultCode},
                   search_requested = #{value.searchRequested},
                   search_triggered = #{value.searchTriggered},
                   search_status = #{value.searchStatus},
                   brand_in_search = #{value.brandInSearch},
                   brand_in_answer = #{value.brandInAnswer},
                   citation_confidence = #{value.citationConfidence},
                   confirmed_citation_exposure = #{value.confirmedCitationExposure},
                   version = #{value.version}, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{value.id}
            """)
    int updateExecutionProjection(@Param("value") PollResult value);

    @Update("""
            UPDATE poll_results
               SET status = #{value.status}, request_count = #{value.requestCount},
                   response_time_ms = #{value.responseTimeMs}, is_hit = #{value.isHit},
                   effective_hit = #{value.effectiveHit}, match_type = #{value.matchType},
                   site_mentioned = #{value.siteMentioned}, contact_mentioned = #{value.contactMentioned},
                   contact_mention_count = #{value.contactMentionCount}, judge_status = #{value.judgeStatus},
                   hit_level = #{value.hitLevel}, hit_sentiment = #{value.hitSentiment},
                   mention_type = #{value.mentionType}, judge_evidence = #{value.judgeEvidence},
                   judge_risk_reason = #{value.judgeRiskReason}, judge_model = #{value.judgeModel},
                   judge_at = #{value.judgeAt}, judge_error = #{value.judgeError},
                   record_type = #{value.recordType}, detail_json = #{value.detailJson},
                   latest_attempt_id = #{value.latestAttemptId}, effective_attempt_id = #{value.effectiveAttemptId},
                   latest_attempt_status = #{value.latestAttemptStatus},
                   execution_finalized = #{value.executionFinalized}, retry_chain_status = #{value.retryChainStatus},
                   result_code = #{value.resultCode}, search_requested = #{value.searchRequested},
                   search_triggered = #{value.searchTriggered}, search_status = #{value.searchStatus},
                   brand_in_search = #{value.brandInSearch}, brand_in_answer = #{value.brandInAnswer},
                   citation_confidence = #{value.citationConfidence},
                   confirmed_citation_exposure = #{value.confirmedCitationExposure},
                   version = #{value.version}, deleted_at = #{value.deletedAt}, deleted_by = #{value.deletedBy},
                   delete_reason = #{value.deleteReason}, updated_at = CURRENT_TIMESTAMP
             WHERE id = #{value.id}
            """)
    int updateBusinessProjection(@Param("value") PollResult value);

    @Select("""
            SELECT keyword_text_snapshot AS keywordText,
                   COALESCE(SUM(hit_count), 0) AS hitCount
              FROM poll_keyword_daily_summary
             WHERE project_id = #{projectId}
               AND keyword_text_snapshot IS NOT NULL
               AND keyword_text_snapshot <> ''
             GROUP BY keyword_text_snapshot
            """)
    List<Map<String, Object>> selectKeywordFrequencySummary(@Param("projectId") Long projectId);
}
