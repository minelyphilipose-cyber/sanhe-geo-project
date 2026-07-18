package com.huanjing.geo.module.system.modeldiagnostic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryQuery;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticRunSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface AiModelDiagnosticRunMapper extends BaseMapper<AiModelDiagnosticRun> {

    @Delete("""
            DELETE FROM ai_model_diagnostic_runs
            WHERE created_at < #{cutoff}
            ORDER BY created_at ASC, id ASC
            LIMIT #{limit}
            """)
    int deleteExpiredBatch(@Param("cutoff") LocalDateTime cutoff,
                           @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM ai_model_diagnostic_runs
            WHERE operator_id = #{operatorId}
              AND client_request_id = #{clientRequestId}
            """)
    AiModelDiagnosticRun selectByIdempotencyKey(@Param("operatorId") Long operatorId,
                                                @Param("clientRequestId") String clientRequestId);

    @Select("""
            SELECT *
            FROM ai_model_diagnostic_runs
            WHERE operator_id = #{operatorId}
              AND client_request_id = #{clientRequestId}
            FOR UPDATE
            """)
    AiModelDiagnosticRun selectByIdempotencyKeyForUpdate(
            @Param("operatorId") Long operatorId,
            @Param("clientRequestId") String clientRequestId);

    @Select("""
            SELECT id, session_record_id, session_id, turn_no, operator_id,
                   client_request_id,
                   platform_config_id, platform_code, channel_code, platform_name,
                   usage_scene, integration_type, config_version,
                   diagnostic_mode, test_mode, response_mode,
                   probe_code, probe_version, template_version,
                   status, conclusion, conclusion_reason,
                   authentication_status, generation_status, web_search_status,
                   source_parsing_status, citation_parsing_status, evaluator_version,
                   user_message, system_prompt, request_messages_json, answer,
                   provider_request_id, requested_model_id, response_model_id, http_status,
                   search_status, search_evidence_json, sources_json, citations_json, usage_json,
                   prompt_tokens, completion_tokens, total_tokens, web_search_call_count,
                   source_count, valid_source_count, citation_count, valid_citation_count,
                   sanitized_request, sanitized_response,
                   error_category, error_code, error_message,
                   deadline_at, started_at, completed_at, duration_ms,
                   version, created_at, updated_at
            FROM ai_model_diagnostic_runs
            WHERE id = #{id}
              AND operator_id = #{operatorId}
            """)
    AiModelDiagnosticRun selectOwnedRun(@Param("id") Long id,
                                        @Param("operatorId") Long operatorId);

    @Select("""
            SELECT id, session_record_id, session_id, turn_no, operator_id,
                   client_request_id,
                   platform_config_id, platform_code, channel_code, platform_name,
                   usage_scene, integration_type, config_version,
                   diagnostic_mode, test_mode, response_mode,
                   probe_code, probe_version, template_version,
                   status, conclusion, conclusion_reason,
                   authentication_status, generation_status, web_search_status,
                   source_parsing_status, citation_parsing_status, evaluator_version,
                   user_message, system_prompt, request_messages_json, answer,
                   provider_request_id, requested_model_id, response_model_id, http_status,
                   search_status, search_evidence_json, sources_json, citations_json, usage_json,
                   prompt_tokens, completion_tokens, total_tokens, web_search_call_count,
                   source_count, valid_source_count, citation_count, valid_citation_count,
                   sanitized_request, sanitized_response,
                   error_category, error_code, error_message,
                   deadline_at, started_at, completed_at, duration_ms,
                   version, created_at, updated_at
            FROM ai_model_diagnostic_runs
            WHERE operator_id = #{operatorId}
              AND session_id = #{sessionId}
            ORDER BY turn_no ASC, id ASC
            """)
    List<AiModelDiagnosticRun> selectOwnedSessionRuns(
            @Param("operatorId") Long operatorId,
            @Param("sessionId") String sessionId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_model_diagnostic_runs
            WHERE operator_id = #{operatorId}
            <if test="query.platformConfigId != null">
              AND platform_config_id = #{query.platformConfigId}
            </if>
            <if test="query.requestedModelId != null">
              AND requested_model_id = #{query.requestedModelId}
            </if>
            <if test="query.diagnosticMode != null">
              AND diagnostic_mode = #{query.diagnosticMode}
            </if>
            <if test="query.status != null">
              AND status = #{query.status}
            </if>
            <if test="query.conclusion != null">
              AND conclusion = #{query.conclusion}
            </if>
            <if test="query.createdFrom != null">
              AND created_at &gt;= #{query.createdFrom}
            </if>
            <if test="query.createdTo != null">
              AND created_at &lt;= #{query.createdTo}
            </if>
            </script>
            """)
    long countOwnedHistory(@Param("operatorId") Long operatorId,
                           @Param("query") ModelDiagnosticHistoryQuery query);

    @Select("""
            <script>
            SELECT id, session_id, turn_no, operator_id,
                   platform_config_id, platform_code, channel_code, platform_name,
                   requested_model_id, response_model_id,
                   diagnostic_mode, test_mode, status, conclusion,
                   error_category, error_code, duration_ms,
                   source_count, valid_source_count, citation_count, valid_citation_count,
                   completed_at, created_at
            FROM ai_model_diagnostic_runs
            WHERE operator_id = #{operatorId}
            <if test="query.platformConfigId != null">
              AND platform_config_id = #{query.platformConfigId}
            </if>
            <if test="query.requestedModelId != null">
              AND requested_model_id = #{query.requestedModelId}
            </if>
            <if test="query.diagnosticMode != null">
              AND diagnostic_mode = #{query.diagnosticMode}
            </if>
            <if test="query.status != null">
              AND status = #{query.status}
            </if>
            <if test="query.conclusion != null">
              AND conclusion = #{query.conclusion}
            </if>
            <if test="query.createdFrom != null">
              AND created_at &gt;= #{query.createdFrom}
            </if>
            <if test="query.createdTo != null">
              AND created_at &lt;= #{query.createdTo}
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ModelDiagnosticRunSummary> selectOwnedHistory(
            @Param("operatorId") Long operatorId,
            @Param("query") ModelDiagnosticHistoryQuery query,
            @Param("offset") long offset,
            @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM ai_model_diagnostic_runs
            WHERE id = #{id}
            FOR UPDATE
            """)
    AiModelDiagnosticRun selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id, user_message, answer, turn_no
            FROM ai_model_diagnostic_runs
            WHERE session_record_id = #{sessionRecordId}
              AND test_mode = 'FREE_CHAT'
              AND status = 'SUCCEEDED'
              AND generation_status = 'PASS'
              AND answer IS NOT NULL
              AND TRIM(answer) <> ''
              AND turn_no < #{beforeTurnNo}
            ORDER BY turn_no DESC
            LIMIT 9
            """)
    List<AiModelDiagnosticRun> selectRecentSuccessfulFreeChatContext(
            @Param("sessionRecordId") Long sessionRecordId,
            @Param("beforeTurnNo") Integer beforeTurnNo);

    @Update("""
            UPDATE ai_model_diagnostic_runs
            SET request_messages_json = #{requestMessagesJson},
                version = version + 1
            WHERE id = #{id}
              AND status = 'RUNNING'
              AND deadline_at > NOW(3)
            """)
    int freezeRequestMessagesIfRunning(@Param("id") Long id,
                                       @Param("requestMessagesJson") String requestMessagesJson);

    @Select("""
            SELECT id
            FROM ai_model_diagnostic_runs
            WHERE status = 'RUNNING'
              AND deadline_at <= NOW(3)
            ORDER BY deadline_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> selectExpiredRunningIds(@Param("limit") int limit);

    @Update("""
            UPDATE ai_model_diagnostic_runs
            SET status = #{run.status},
                conclusion = #{run.conclusion},
                conclusion_reason = #{run.conclusionReason},
                authentication_status = #{run.authenticationStatus},
                generation_status = #{run.generationStatus},
                web_search_status = #{run.webSearchStatus},
                source_parsing_status = #{run.sourceParsingStatus},
                citation_parsing_status = #{run.citationParsingStatus},
                evaluator_version = #{run.evaluatorVersion},
                answer = #{run.answer},
                provider_request_id = #{run.providerRequestId},
                response_model_id = #{run.responseModelId},
                http_status = #{run.httpStatus},
                search_status = #{run.searchStatus},
                search_evidence_json = #{run.searchEvidenceJson},
                sources_json = #{run.sourcesJson},
                citations_json = #{run.citationsJson},
                usage_json = #{run.usageJson},
                prompt_tokens = #{run.promptTokens},
                completion_tokens = #{run.completionTokens},
                total_tokens = #{run.totalTokens},
                web_search_call_count = #{run.webSearchCallCount},
                source_count = #{run.sourceCount},
                valid_source_count = #{run.validSourceCount},
                citation_count = #{run.citationCount},
                valid_citation_count = #{run.validCitationCount},
                sanitized_request = #{run.sanitizedRequest},
                sanitized_response = #{run.sanitizedResponse},
                error_category = #{run.errorCategory},
                error_code = #{run.errorCode},
                error_message = #{run.errorMessage},
                completed_at = NOW(3),
                duration_ms = CASE
                    WHEN started_at IS NULL THEN NULL
                    ELSE GREATEST(0,
                        TIMESTAMPDIFF(MICROSECOND, started_at, NOW(3)) DIV 1000)
                END,
                version = version + 1
            WHERE id = #{run.id}
              AND status = 'RUNNING'
              AND deadline_at > NOW(3)
            """)
    int finishRunning(@Param("run") AiModelDiagnosticRun run);

    @Update("""
            UPDATE ai_model_diagnostic_runs
            SET status = 'REJECTED',
                conclusion = NULL,
                error_category = #{errorCategory},
                error_code = #{errorCode},
                error_message = #{errorMessage},
                completed_at = NOW(3),
                duration_ms = CASE
                    WHEN started_at IS NULL THEN NULL
                    ELSE GREATEST(0,
                        TIMESTAMPDIFF(MICROSECOND, started_at, NOW(3)) DIV 1000)
                END,
                version = version + 1
            WHERE id = #{id}
              AND status = 'RUNNING'
              AND deadline_at > NOW(3)
            """)
    int rejectRunningBeforeExecution(@Param("id") Long id,
                                     @Param("errorCategory") String errorCategory,
                                     @Param("errorCode") String errorCode,
                                     @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE ai_model_diagnostic_runs
            SET status = 'ABANDONED',
                conclusion = NULL,
                completed_at = NOW(3),
                duration_ms = CASE
                    WHEN started_at IS NULL THEN NULL
                    ELSE GREATEST(0,
                        TIMESTAMPDIFF(MICROSECOND, started_at, NOW(3)) DIV 1000)
                END,
                version = version + 1
            WHERE id = #{id}
              AND status = 'RUNNING'
              AND deadline_at <= NOW(3)
            """)
    int markAbandonedIfExpired(@Param("id") Long id);
}
