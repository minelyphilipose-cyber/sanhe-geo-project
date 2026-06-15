package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
            SELECT status AS name, COUNT(1) AS total
            FROM self_media_publish_schedule
            GROUP BY status
            ORDER BY total DESC
            """)
    List<Map<String, Object>> countGroupedByStatus();

    @Select("""
            <script>
            SELECT platform AS name,
                   SUM(CASE WHEN status IN
                     <foreach collection="activeStatuses" item="status" open="(" separator="," close=")">
                       #{status}
                     </foreach>
                     THEN 1 ELSE 0 END) AS active_total,
                   SUM(CASE WHEN status IN ('schedule_failed', 'publish_failed', 'manual_required') THEN 1 ELSE 0 END) AS failed_total,
                   SUM(CASE WHEN (
                     status = 'pending' AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
                   ) OR (
                     status IN ('publish_due', 'publish_unknown') AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
                   ) OR (
                     status = 'scheduled' AND next_attempt_at IS NULL AND COALESCE(platform_scheduled_at, planned_publish_at) &lt;= #{now}
                   ) THEN 1 ELSE 0 END) AS due_total
            FROM self_media_publish_schedule
            WHERE (
              status IN
                <foreach collection="activeStatuses" item="status" open="(" separator="," close=")">
                  #{status}
                </foreach>
            ) OR status IN ('schedule_failed', 'publish_failed', 'manual_required')
            GROUP BY platform
            ORDER BY active_total DESC, failed_total DESC
            </script>
            """)
    List<Map<String, Object>> countGroupedByPlatform(@Param("activeStatuses") List<String> activeStatuses,
                                                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT failure_code AS name, COUNT(1) AS total
            FROM self_media_publish_schedule
            WHERE failure_code IS NOT NULL
              AND failure_code != ''
              AND status IN ('schedule_failed', 'publish_failed', 'manual_required', 'publish_unknown', 'pending')
            GROUP BY failure_code
            ORDER BY total DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> countGroupedByFailureCode(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
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
            </script>
            """)
    long countDueByQueue(@Param("queueKind") String queueKind,
                         @Param("statuses") List<String> statuses,
                         @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    long countByStatuses(@Param("statuses") List<String> statuses);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND locked_until IS NOT NULL
              AND locked_until &gt; #{now}
            </script>
            """)
    long countLockedByStatuses(@Param("statuses") List<String> statuses,
                               @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE created_by = #{operatorId}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND locked_until IS NOT NULL
              AND locked_until &gt; #{now}
            </script>
            """)
    long countLockedByOperatorAndStatuses(@Param("operatorId") Long operatorId,
                                          @Param("statuses") List<String> statuses,
                                          @Param("now") LocalDateTime now);

    @Select("""
            SELECT *
            FROM self_media_publish_schedule
            WHERE article_id = #{articleId}
              AND self_media_account_id = #{selfMediaAccountId}
              AND platform = #{platform}
            ORDER BY id DESC
            LIMIT 1
            """)
    SelfMediaPublishSchedule selectLatestByArticleAccountAndPlatform(@Param("articleId") Long articleId,
                                                                     @Param("selfMediaAccountId") Long selfMediaAccountId,
                                                                     @Param("platform") String platform);

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
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE article_id = #{articleId}
              <if test="excludedScheduleId != null">
                AND id != #{excludedScheduleId}
              </if>
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    long countActiveByArticleId(@Param("articleId") Long articleId,
                                @Param("excludedScheduleId") Long excludedScheduleId,
                                @Param("statuses") List<String> statuses);

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

    @Select("""
            SELECT *
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND platform = #{platform}
              AND planned_publish_at &gt;= #{periodStart}
              AND planned_publish_at &lt; #{periodEnd}
              AND status = 'pending'
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY planned_publish_at ASC, id ASC
            LIMIT 1
            """)
    SelfMediaPublishSchedule selectNextReplaceablePendingByBrandPlatformAndPeriod(@Param("brandId") Long brandId,
                                                                                  @Param("platform") String platform,
                                                                                  @Param("periodStart") LocalDateTime periodStart,
                                                                                  @Param("periodEnd") LocalDateTime periodEnd,
                                                                                  @Param("now") LocalDateTime now);

    @Select("""
            SELECT *
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND platform = #{platform}
              AND planned_publish_at >= #{periodStart}
              AND planned_publish_at < #{periodEnd}
              AND status = 'pending'
              AND (locked_until IS NULL OR locked_until < #{now})
              AND COALESCE(next_attempt_at, planned_publish_at) >= #{replaceAfter}
              AND platform_schedule_id IS NULL
              AND platform_publish_id IS NULL
              AND (platform_published_url IS NULL OR platform_published_url = '')
            ORDER BY COALESCE(next_attempt_at, planned_publish_at) ASC, planned_publish_at ASC, id ASC
            LIMIT 1
            """)
    SelfMediaPublishSchedule selectSafeReplaceablePendingByBrandPlatformAndPeriod(@Param("brandId") Long brandId,
                                                                                  @Param("platform") String platform,
                                                                                  @Param("periodStart") LocalDateTime periodStart,
                                                                                  @Param("periodEnd") LocalDateTime periodEnd,
                                                                                  @Param("now") LocalDateTime now,
                                                                                  @Param("replaceAfter") LocalDateTime replaceAfter);

    @Update("""
            UPDATE self_media_publish_schedule
            SET status = 'cancelled',
                cancelled_at = #{now},
                failure_code = 'REPLACED_BY_OPERATOR_QUICK_SCHEDULE',
                failure_message = '运营确认后由平台快速排期替换',
                locked_until = NULL,
                next_attempt_at = NULL,
                updated_at = #{now}
            WHERE id = #{scheduleId}
              AND brand_id = #{brandId}
              AND platform = #{platform}
              AND planned_publish_at &gt;= #{periodStart}
              AND planned_publish_at &lt; #{periodEnd}
              AND status = 'pending'
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            """)
    int cancelReplaceablePendingSchedule(@Param("scheduleId") Long scheduleId,
                                         @Param("brandId") Long brandId,
                                         @Param("platform") String platform,
                                         @Param("periodStart") LocalDateTime periodStart,
                                         @Param("periodEnd") LocalDateTime periodEnd,
                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE self_media_publish_schedule
            SET status = 'cancelled',
                cancelled_at = #{now},
                failure_code = 'REPLACED_BY_OPERATOR_QUICK_DISPATCH',
                failure_message = '运营点击平台快速分发时安全替换',
                locked_until = NULL,
                next_attempt_at = NULL,
                updated_at = #{now}
            WHERE id = #{scheduleId}
              AND brand_id = #{brandId}
              AND platform = #{platform}
              AND planned_publish_at >= #{periodStart}
              AND planned_publish_at < #{periodEnd}
              AND status = 'pending'
              AND (locked_until IS NULL OR locked_until < #{now})
              AND COALESCE(next_attempt_at, planned_publish_at) >= #{replaceAfter}
              AND platform_schedule_id IS NULL
              AND platform_publish_id IS NULL
              AND (platform_published_url IS NULL OR platform_published_url = '')
            """)
    int cancelSafeReplaceablePendingSchedule(@Param("scheduleId") Long scheduleId,
                                             @Param("brandId") Long brandId,
                                             @Param("platform") String platform,
                                             @Param("periodStart") LocalDateTime periodStart,
                                             @Param("periodEnd") LocalDateTime periodEnd,
                                             @Param("now") LocalDateTime now,
                                             @Param("replaceAfter") LocalDateTime replaceAfter);

    @Select("""
            <script>
            SELECT *
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (
                (next_attempt_at IS NOT NULL AND next_attempt_at &gt;= #{from} AND next_attempt_at &lt; #{to})
                OR (next_attempt_at IS NULL AND planned_publish_at IS NOT NULL AND planned_publish_at &gt;= #{from} AND planned_publish_at &lt; #{to})
              )
            ORDER BY COALESCE(next_attempt_at, planned_publish_at), id ASC
            </script>
            """)
    List<SelfMediaPublishSchedule> selectBrandActiveScheduleSlots(@Param("brandId") Long brandId,
                                                                  @Param("from") LocalDateTime from,
                                                                  @Param("to") LocalDateTime to,
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
