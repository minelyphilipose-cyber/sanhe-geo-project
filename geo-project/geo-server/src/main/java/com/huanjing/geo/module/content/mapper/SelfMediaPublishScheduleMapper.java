package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
              AND (
                (#{queueKind} != 'publish_result_check' AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now}))
                OR (#{queueKind} = 'publish_result_check' AND (
                  next_attempt_at &lt;= #{now}
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'publish_unknown'))
                  OR (next_attempt_at IS NULL AND status = 'scheduled' AND COALESCE(platform_scheduled_at, planned_publish_at) &lt;= #{now})
                ))
              )
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY queue_priority ASC, COALESCE(next_attempt_at, platform_scheduled_at, planned_publish_at), id ASC
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
              AND platform IN
              <foreach collection="platforms" item="platform" open="(" separator="," close=")">
                #{platform}
              </foreach>
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (
                (#{queueKind} != 'publish_result_check' AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now}))
                OR (#{queueKind} = 'publish_result_check' AND (
                  next_attempt_at &lt;= #{now}
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'publish_unknown'))
                  OR (next_attempt_at IS NULL AND status = 'scheduled' AND COALESCE(platform_scheduled_at, planned_publish_at) &lt;= #{now})
                ))
              )
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY queue_priority ASC, COALESCE(next_attempt_at, platform_scheduled_at, planned_publish_at), id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectDueQueueCandidatesByPlatforms(@Param("queueKind") String queueKind,
                                                                       @Param("statuses") List<String> statuses,
                                                                       @Param("now") LocalDateTime now,
                                                                       @Param("limit") int limit,
                                                                       @Param("platforms") Set<String> platforms);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE queue_kind = #{queueKind}
              AND created_by = #{operatorId}
              <if test="platform != null and platform != ''">
                AND platform = #{platform}
              </if>
              <if test="platforms != null and platforms.size() > 0">
                AND platform IN
                <foreach collection="platforms" item="allowedPlatform" open="(" separator="," close=")">
                  #{allowedPlatform}
                </foreach>
              </if>
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (
                (#{queueKind} != 'publish_result_check' AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now}))
                OR (#{queueKind} = 'publish_result_check' AND (
                  next_attempt_at &lt;= #{now}
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'publish_unknown'))
                  OR (next_attempt_at IS NULL AND status = 'scheduled' AND COALESCE(platform_scheduled_at, planned_publish_at) &lt;= #{now})
                ))
              )
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY queue_priority ASC, COALESCE(next_attempt_at, platform_scheduled_at, planned_publish_at), id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectDueQueueCandidatesForOperator(@Param("queueKind") String queueKind,
                                                                       @Param("statuses") List<String> statuses,
                                                                       @Param("now") LocalDateTime now,
                                                                       @Param("limit") int limit,
                                                                       @Param("operatorId") Long operatorId,
                                                                       @Param("platform") String platform,
                                                                       @Param("platforms") Set<String> platforms);

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

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (
                (next_attempt_at IS NOT NULL AND next_attempt_at &lt;= #{monitorUntil})
                OR (locked_until IS NOT NULL AND locked_until &lt; #{now})
                OR (platform_scheduled_at IS NOT NULL AND platform_scheduled_at &lt;= #{monitorUntil})
                OR status IN ('manual_required', 'schedule_failed', 'publish_failed', 'publish_unknown')
                OR (status = 'published_confirmed' AND (platform_published_url IS NULL OR platform_published_url = ''))
              )
            ORDER BY COALESCE(next_attempt_at, platform_scheduled_at, planned_publish_at, updated_at) ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectMonitorCandidates(@Param("statuses") List<String> statuses,
                                                           @Param("now") LocalDateTime now,
                                                           @Param("monitorUntil") LocalDateTime monitorUntil,
                                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND locked_until IS NOT NULL
              AND locked_until &lt; #{now}
            ORDER BY locked_until ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectTimedOutRunning(@Param("statuses") List<String> statuses,
                                                         @Param("now") LocalDateTime now,
                                                         @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND platform = #{platform}
              AND planned_publish_at &gt;= #{periodStart}
              AND planned_publish_at &lt; #{periodEnd}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    long countActiveByBrandPlatformAndPeriod(@Param("brandId") Long brandId,
                                             @Param("platform") String platform,
                                             @Param("periodStart") LocalDateTime periodStart,
                                             @Param("periodEnd") LocalDateTime periodEnd,
                                             @Param("statuses") List<String> statuses);

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

    @Update("""
            <script>
            UPDATE self_media_publish_schedule
            SET locked_until = #{lockedUntil},
                updated_at = #{now}
            WHERE id = #{scheduleId}
              AND created_by = #{operatorId}
              AND status IN
              <foreach collection="runningStatuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    int renewLocalAgentLock(@Param("scheduleId") Long scheduleId,
                            @Param("operatorId") Long operatorId,
                            @Param("runningStatuses") List<String> runningStatuses,
                            @Param("lockedUntil") LocalDateTime lockedUntil,
                            @Param("now") LocalDateTime now);
}
