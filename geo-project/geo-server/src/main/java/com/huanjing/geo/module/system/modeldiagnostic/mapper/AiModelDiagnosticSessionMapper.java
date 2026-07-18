package com.huanjing.geo.module.system.modeldiagnostic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiModelDiagnosticSessionMapper extends BaseMapper<AiModelDiagnosticSession> {

    @Delete("""
            DELETE FROM ai_model_diagnostic_sessions
            WHERE id IN (
                SELECT candidate.id
                FROM (
                    SELECT session_row.id
                    FROM ai_model_diagnostic_sessions session_row
                    WHERE COALESCE(session_row.last_run_at, session_row.created_at) < #{cutoff}
                      AND NOT EXISTS (
                          SELECT 1
                          FROM ai_model_diagnostic_runs run_row
                          WHERE run_row.session_record_id = session_row.id
                      )
                    ORDER BY COALESCE(session_row.last_run_at, session_row.created_at) ASC,
                             session_row.id ASC
                    LIMIT #{limit}
                ) candidate
            )
            """)
    int deleteEmptyExpiredBatch(@Param("cutoff") LocalDateTime cutoff,
                                @Param("limit") int limit);

    @Insert("""
            INSERT IGNORE INTO ai_model_diagnostic_sessions
                (session_id, operator_id, status, next_turn_no, created_at, updated_at)
            VALUES
                (#{sessionId}, #{operatorId}, 'ACTIVE', 1, #{createdAt}, #{createdAt})
            """)
    int insertActiveIfAbsent(@Param("operatorId") Long operatorId,
                             @Param("sessionId") String sessionId,
                             @Param("createdAt") LocalDateTime createdAt);

    @Select("""
            SELECT *
            FROM ai_model_diagnostic_sessions
            WHERE operator_id = #{operatorId}
              AND session_id = #{sessionId}
            FOR UPDATE
            """)
    AiModelDiagnosticSession selectOwnedForUpdate(@Param("operatorId") Long operatorId,
                                                  @Param("sessionId") String sessionId);

    @Select("""
            SELECT id, session_id, operator_id, status, next_turn_no,
                   last_run_at, created_at, updated_at
            FROM ai_model_diagnostic_sessions
            WHERE operator_id = #{operatorId}
              AND session_id = #{sessionId}
            """)
    AiModelDiagnosticSession selectOwned(@Param("operatorId") Long operatorId,
                                         @Param("sessionId") String sessionId);

    @Update("""
            UPDATE ai_model_diagnostic_sessions
            SET next_turn_no = next_turn_no + 1,
                last_run_at = #{runAt}
            WHERE id = #{id}
              AND status = 'ACTIVE'
              AND next_turn_no = #{expectedTurnNo}
            """)
    int consumeTurn(@Param("id") Long id,
                    @Param("expectedTurnNo") Integer expectedTurnNo,
                    @Param("runAt") LocalDateTime runAt);
}
