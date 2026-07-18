package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollInvocationAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PollInvocationAttemptMapper extends BaseMapper<PollInvocationAttempt> {

    @Select("""
            SELECT *
            FROM poll_invocation_attempts
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollInvocationAttempt selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT COALESCE(MAX(attempt_no), 0)
            FROM poll_invocation_attempts
            WHERE poll_result_id = #{pollResultId}
            """)
    int selectMaxAttemptNo(@Param("pollResultId") Long pollResultId);

    @Update("""
            UPDATE poll_invocation_attempts
            SET status = 'RUNNING',
                started_at = #{startedAt},
                last_heartbeat_at = #{startedAt}
            WHERE id = #{id}
              AND status = 'PENDING'
              AND attempt_deadline_at > #{startedAt}
            """)
    int markRunning(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);

    @Update("""
            UPDATE poll_invocation_attempts
            SET last_heartbeat_at = #{heartbeatAt}
            WHERE id = #{id}
              AND status = 'RUNNING'
              AND attempt_deadline_at > #{heartbeatAt}
            """)
    int touchHeartbeat(@Param("id") Long id, @Param("heartbeatAt") LocalDateTime heartbeatAt);

    @Update("""
            UPDATE poll_invocation_attempts
            SET status = #{targetStatus},
                completed_at = #{completedAt},
                latency_ms = CASE
                    WHEN started_at IS NULL THEN NULL
                    ELSE TIMESTAMPDIFF(MICROSECOND, started_at, #{completedAt}) DIV 1000
                END
            WHERE id = #{id}
              AND status = 'RUNNING'
              AND #{targetStatus} IN ('SUCCEEDED', 'FAILED', 'ABANDONED')
            """)
    int markTerminal(@Param("id") Long id,
                     @Param("targetStatus") String targetStatus,
                     @Param("completedAt") LocalDateTime completedAt);

    @Update("""
            UPDATE poll_invocation_attempts
            SET root_attempt_id = #{rootAttemptId}
            WHERE id = #{id}
              AND root_attempt_id IS NULL
            """)
    int setRootAttemptIdIfAbsent(@Param("id") Long id, @Param("rootAttemptId") Long rootAttemptId);

    @Update("""
            UPDATE poll_invocation_attempts
            SET finalized_at = #{finalizedAt}
            WHERE id = #{id}
              AND status IN ('SUCCEEDED', 'FAILED', 'ABANDONED')
              AND finalized_at IS NULL
            """)
    int markFinalized(@Param("id") Long id, @Param("finalizedAt") LocalDateTime finalizedAt);
}
