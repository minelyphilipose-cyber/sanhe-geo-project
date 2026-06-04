package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SelfMediaPublishScheduleMapper extends BaseMapper<SelfMediaPublishSchedule> {

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE request_id = #{requestId}
            ORDER BY id ASC
            </script>
            """)
    List<SelfMediaPublishSchedule> selectByRequestId(@Param("requestId") Long requestId);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE base_idempotency_key = #{baseIdempotencyKey}
              AND status IN
              <foreach collection="activeStatuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            ORDER BY id DESC
            LIMIT 1
            </script>
            """)
    SelfMediaPublishSchedule selectActiveByBaseIdempotencyKey(@Param("baseIdempotencyKey") String baseIdempotencyKey,
                                                              @Param("activeStatuses") List<String> activeStatuses);

    @Select("""
            SELECT COALESCE(MAX(generation_no), 0)
            FROM self_media_publish_schedule
            WHERE base_idempotency_key = #{baseIdempotencyKey}
            """)
    Integer selectMaxGenerationNo(@Param("baseIdempotencyKey") String baseIdempotencyKey);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE queue_kind = #{queueKind}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY queue_priority ASC, COALESCE(next_attempt_at, planned_publish_at), id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectDueQueueCandidates(@Param("queueKind") String queueKind,
                                                            @Param("statuses") List<String> statuses,
                                                            @Param("now") LocalDateTime now,
                                                            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE queue_kind = #{queueKind}
              AND created_by = #{operatorId}
              <if test="platform != null and platform != ''">
                AND platform = #{platform}
              </if>
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY queue_priority ASC, COALESCE(next_attempt_at, planned_publish_at), id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectDueQueueCandidatesForOperator(@Param("queueKind") String queueKind,
                                                                       @Param("statuses") List<String> statuses,
                                                                       @Param("now") LocalDateTime now,
                                                                       @Param("limit") int limit,
                                                                       @Param("operatorId") Long operatorId,
                                                                       @Param("platform") String platform);

    @Select("""
            SELECT *
            FROM self_media_publish_schedule
            WHERE distribution_task_id = #{distributionTaskId}
              AND status IN (
                'pending',
                'filling',
                'filled_verified',
                'scheduling',
                'scheduled',
                'publish_due',
                'checking_publish_result',
                'publish_unknown',
                'cancel_pending_platform'
              )
            ORDER BY id DESC
            LIMIT 1
            """)
    SelfMediaPublishSchedule selectActiveByDistributionTaskId(@Param("distributionTaskId") Long distributionTaskId);

    @Update("""
            <script>
            UPDATE self_media_publish_schedule
            SET status = #{targetStatus},
                locked_until = #{lockedUntil},
                last_attempt_at = #{now},
                attempt_count = COALESCE(attempt_count, 0) + 1,
                updated_at = #{now}
            WHERE id = #{scheduleId}
              AND queue_kind = #{queueKind}
              AND status IN
              <foreach collection="expectedStatuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            </script>
            """)
    int claimQueueSchedule(@Param("scheduleId") Long scheduleId,
                           @Param("queueKind") String queueKind,
                           @Param("expectedStatuses") List<String> expectedStatuses,
                           @Param("targetStatus") String targetStatus,
                           @Param("now") LocalDateTime now,
                           @Param("lockedUntil") LocalDateTime lockedUntil);
}
