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
            SELECT id
            FROM self_media_account
            WHERE id = #{selfMediaAccountId}
            FOR UPDATE
            """)
    Long lockSelfMediaAccountForScheduling(@Param("selfMediaAccountId") Long selfMediaAccountId);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND self_media_account_id = #{selfMediaAccountId}
              AND planned_publish_at &gt;= #{dayStart}
              AND planned_publish_at &lt; #{dayEnd}
              AND status NOT IN
              <foreach collection="excludedStatuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    long countOccupiedByBrandAccountAndPublishDay(@Param("brandId") Long brandId,
                                                   @Param("selfMediaAccountId") Long selfMediaAccountId,
                                                   @Param("dayStart") LocalDateTime dayStart,
                                                   @Param("dayEnd") LocalDateTime dayEnd,
                                                   @Param("excludedStatuses") List<String> excludedStatuses);

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
                     status IN ('publish_due', 'published_url_pending', 'publish_unknown') AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
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
              AND status IN ('schedule_failed', 'publish_failed', 'manual_required', 'published_url_pending', 'publish_unknown', 'pending')
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
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'published_url_pending', 'publish_unknown'))
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
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE status = 'published_confirmed'
              AND platform_published_url IS NOT NULL
              AND platform_published_url != ''
            """)
    long countConfirmedWithPublishedUrl();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE failure_code IN
              <foreach collection="failureCodes" item="failureCode" open="(" separator="," close=")">
                #{failureCode}
              </foreach>
            </script>
            """)
    long countByFailureCodes(@Param("failureCodes") List<String> failureCodes);

    @Select("""
            SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, created_at, published_confirmed_at)), 0)
            FROM self_media_publish_schedule
            WHERE status = 'published_confirmed'
              AND created_at IS NOT NULL
              AND published_confirmed_at IS NOT NULL
            """)
    Double averagePublishedDurationSeconds();

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
            WHERE status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND locked_until IS NOT NULL
              AND locked_until &lt;= #{now}
            </script>
            """)
    long countTimedOutLockedByStatuses(@Param("statuses") List<String> statuses,
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
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'published_url_pending', 'publish_unknown'))
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
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'published_url_pending', 'publish_unknown'))
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
                  OR (next_attempt_at IS NULL AND status IN ('publish_due', 'published_url_pending', 'publish_unknown'))
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
            <script>
            SELECT schedule.*
            FROM self_media_publish_schedule schedule
            JOIN browser_environment_agent_binding agent_binding
              ON agent_binding.browser_environment_id = schedule.browser_environment_id
             AND agent_binding.status = 'active'
            JOIN local_agent_runtime_status helper_runtime
              ON helper_runtime.machine_id = agent_binding.machine_id
             AND helper_runtime.active_profile = agent_binding.active_profile
             AND helper_runtime.session_id = #{localAgentSessionId}
            WHERE schedule.queue_kind = #{queueKind}
              <if test="platform != null and platform != ''">
                AND schedule.platform = #{platform}
              </if>
              <if test="platforms != null and platforms.size() > 0">
                AND schedule.platform IN
                <foreach collection="platforms" item="allowedPlatform" open="(" separator="," close=")">
                  #{allowedPlatform}
                </foreach>
              </if>
              AND schedule.status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (
                (#{queueKind} != 'publish_result_check'
                  AND (schedule.next_attempt_at IS NULL OR schedule.next_attempt_at &lt;= #{now}))
                OR (#{queueKind} = 'publish_result_check' AND (
                  schedule.next_attempt_at &lt;= #{now}
                  OR (schedule.next_attempt_at IS NULL
                    AND schedule.status IN ('publish_due', 'published_url_pending', 'publish_unknown'))
                  OR (schedule.next_attempt_at IS NULL
                    AND schedule.status = 'scheduled'
                    AND COALESCE(schedule.platform_scheduled_at, schedule.planned_publish_at) &lt;= #{now})
                ))
              )
              AND (schedule.locked_until IS NULL OR schedule.locked_until &lt; #{now})
            ORDER BY schedule.queue_priority ASC,
                     COALESCE(schedule.next_attempt_at, schedule.platform_scheduled_at, schedule.planned_publish_at),
                     schedule.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectDueQueueCandidatesForLocalAgent(
            @Param("queueKind") String queueKind,
            @Param("statuses") List<String> statuses,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit,
            @Param("localAgentSessionId") Long localAgentSessionId,
            @Param("platform") String platform,
            @Param("platforms") Set<String> platforms);

    @Select("""
            SELECT COUNT(1) > 0
            FROM browser_environment_agent_binding agent_binding
            JOIN local_agent_runtime_status helper_runtime
              ON helper_runtime.machine_id = agent_binding.machine_id
             AND helper_runtime.active_profile = agent_binding.active_profile
             AND helper_runtime.session_id = #{localAgentSessionId}
            WHERE agent_binding.browser_environment_id = #{browserEnvironmentId}
              AND agent_binding.status = 'active'
            """)
    boolean isBrowserEnvironmentOwnedByLocalAgent(
            @Param("browserEnvironmentId") Long browserEnvironmentId,
            @Param("localAgentSessionId") Long localAgentSessionId);

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
                'published_url_pending',
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
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    long countActiveByBrandId(@Param("brandId") Long brandId,
                              @Param("statuses") List<String> statuses);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE brand_id = #{brandId}
              AND request_idempotency_key LIKE CONCAT(#{requestKeyPrefix}, '%')
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    long countActiveByBrandIdAndRequestKeyPrefix(@Param("brandId") Long brandId,
                                                 @Param("requestKeyPrefix") String requestKeyPrefix,
                                                 @Param("statuses") List<String> statuses);

    @Select("""
            <script>
            SELECT DISTINCT article_id
            FROM self_media_publish_schedule
            WHERE article_id IN
              <foreach collection="articleIds" item="articleId" open="(" separator="," close=")">
                #{articleId}
              </foreach>
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
            </script>
            """)
    List<Long> selectActiveArticleIds(@Param("articleIds") List<Long> articleIds,
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
                OR status IN ('manual_required', 'schedule_failed', 'publish_failed', 'published_url_pending', 'publish_unknown')
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
            WHERE request_idempotency_key LIKE 'project-auto-%'
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND failure_code IS NOT NULL
              AND failure_code != ''
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            ORDER BY updated_at ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<SelfMediaPublishSchedule> selectProjectAutoCompensationCandidates(@Param("statuses") List<String> statuses,
                                                                           @Param("now") LocalDateTime now,
                                                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE request_idempotency_key LIKE 'project-auto-%'
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND failure_code IS NOT NULL
              AND failure_code != ''
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            </script>
            """)
    long countProjectAutoCompensationCandidates(@Param("statuses") List<String> statuses,
                                                @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE request_idempotency_key LIKE 'project-auto-%'
              AND attempt_count > 0
              AND failure_code IS NOT NULL
              AND failure_code != ''
            """)
    long countProjectAutoCompensationTried();

    @Select("""
            SELECT MAX(last_attempt_at)
            FROM self_media_publish_schedule
            WHERE request_idempotency_key LIKE 'project-auto-%'
              AND attempt_count > 0
              AND last_attempt_at IS NOT NULL
            """)
    LocalDateTime selectProjectAutoLastCompensationTriedAt();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM self_media_publish_schedule
            WHERE created_by = #{operatorId}
              AND queue_kind = #{queueKind}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (next_attempt_at IS NULL OR next_attempt_at &lt;= #{now})
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            </script>
            """)
    long countDueByOperatorAndQueue(@Param("operatorId") Long operatorId,
                                    @Param("queueKind") String queueKind,
                                    @Param("statuses") List<String> statuses,
                                    @Param("now") LocalDateTime now);

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
              AND planned_publish_at >= #{periodStart}
              AND planned_publish_at < #{periodEnd}
              AND status = 'pending'
              AND (locked_until IS NULL OR locked_until < #{now})
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
              AND planned_publish_at >= #{periodStart}
              AND planned_publish_at < #{periodEnd}
              AND status = 'pending'
              AND (locked_until IS NULL OR locked_until < #{now})
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
                failure_message = '当前任务排期已由手动触发占用',
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
                runtime_stage = #{runtimeStage},
                runtime_stage_at = #{now},
                runtime_stage_message = #{queueKind},
                runtime_worker_id = #{operatorId},
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
                           @Param("lockedUntil") LocalDateTime lockedUntil,
                           @Param("operatorId") Long operatorId,
                           @Param("runtimeStage") String runtimeStage);

    default int claimQueueSchedule(Long scheduleId,
                                   String queueKind,
                                   List<String> expectedStatuses,
                                   String targetStatus,
                                   LocalDateTime now,
                                   LocalDateTime lockedUntil) {
        return claimQueueSchedule(scheduleId, queueKind, expectedStatuses, targetStatus, now, lockedUntil, null, null);
    }

    @Update("""
            UPDATE self_media_publish_schedule
            SET diagnostics_json = #{diagnosticsJson},
                runtime_stage = #{runtimeStage},
                runtime_stage_at = #{now},
                runtime_stage_message = #{runtimeStageMessage},
                updated_at = #{now}
            WHERE id = #{scheduleId}
            """)
    int updateClaimGateDiagnostics(@Param("scheduleId") Long scheduleId,
                                   @Param("diagnosticsJson") String diagnosticsJson,
                                   @Param("runtimeStage") String runtimeStage,
                                   @Param("runtimeStageMessage") String runtimeStageMessage,
                                   @Param("now") LocalDateTime now);

    @Update("""
            <script>
            UPDATE self_media_publish_schedule
            SET status = 'manual_required',
                locked_until = NULL,
                next_attempt_at = NULL,
                failure_code = #{failureCode},
                failure_message = #{failureMessage},
                diagnostics_json = #{diagnosticsJson},
                runtime_stage = 'manual_required',
                runtime_stage_at = #{now},
                runtime_stage_message = #{failureMessage},
                updated_at = #{now}
            WHERE id = #{scheduleId}
              AND queue_kind = #{queueKind}
              AND status IN
              <foreach collection="expectedStatuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              AND (locked_until IS NULL OR locked_until &lt; #{now})
            </script>
            """)
    int markClaimGateManualRequired(@Param("scheduleId") Long scheduleId,
                                    @Param("queueKind") String queueKind,
                                    @Param("expectedStatuses") List<String> expectedStatuses,
                                    @Param("failureCode") String failureCode,
                                    @Param("failureMessage") String failureMessage,
                                    @Param("diagnosticsJson") String diagnosticsJson,
                                    @Param("now") LocalDateTime now);

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
