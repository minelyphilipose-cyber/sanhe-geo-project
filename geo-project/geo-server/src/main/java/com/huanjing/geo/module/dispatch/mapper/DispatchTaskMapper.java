package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.dto.DispatchDueTimeBucketRow;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DispatchTaskMapper extends BaseMapper<DispatchTask> {

    @Select("""
            SELECT *
            FROM dispatch_task
            WHERE id = #{taskId}
            FOR UPDATE
            """)
    DispatchTask selectByIdForUpdate(@Param("taskId") Long taskId);

    @Update("""
            UPDATE dispatch_task
            SET status = #{runningStatus},
                last_started_at = #{startedAt},
                first_started_at = COALESCE(first_started_at, #{startedAt}),
                timeout_at = #{timeoutAt},
                updated_at = NOW()
            WHERE id = #{taskId}
              AND (
                    status = #{pendingStatus}
                    OR (
                        status = #{retryPendingStatus}
                        AND (next_retry_at IS NULL OR next_retry_at <= #{startedAt})
                    )
                  )
            """)
    int claimRunnableTask(@Param("taskId") Long taskId,
                          @Param("pendingStatus") String pendingStatus,
                          @Param("retryPendingStatus") String retryPendingStatus,
                          @Param("runningStatus") String runningStatus,
                          @Param("startedAt") LocalDateTime startedAt,
                          @Param("timeoutAt") LocalDateTime timeoutAt);

    @Select("""
            SELECT generation_slot_no
            FROM dispatch_task
            WHERE project_id = #{projectId}
              AND task_type = #{taskType}
              AND target_channel = #{targetChannel}
              AND window_start = #{windowStart}
              AND window_end = #{windowEnd}
              AND status <> 'cancelled'
              AND generation_slot_no IS NOT NULL
            FOR UPDATE
            """)
    List<Integer> selectOccupiedGenerationSlotsForUpdate(@Param("projectId") Long projectId,
                                                         @Param("taskType") String taskType,
                                                         @Param("targetChannel") String targetChannel,
                                                         @Param("windowStart") LocalDate windowStart,
                                                         @Param("windowEnd") LocalDate windowEnd);

    @Update("""
            UPDATE dispatch_task
            SET status = #{targetStatus},
                finished_at = #{finishedAt},
                last_error = #{lastError},
                error_context = #{errorContext},
                updated_at = NOW()
            WHERE id = #{taskId}
              AND status = #{expectedStatus}
              AND timeout_at IS NOT NULL
              AND timeout_at < NOW()
            """)
    int claimTimedOutRunningTask(@Param("taskId") Long taskId,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("targetStatus") String targetStatus,
                                 @Param("finishedAt") LocalDateTime finishedAt,
                                 @Param("lastError") String lastError,
                                 @Param("errorContext") String errorContext);

    @Select("""
            <script>
            SELECT
              COALESCE(platform_code, 'unknown') AS platformCode,
              status AS status,
              FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(due_time) / (#{bucketMinutes} * 60)) * (#{bucketMinutes} * 60)) AS bucketStart,
              COUNT(1) AS taskCount
            FROM dispatch_task
            WHERE task_type = #{taskType}
              AND due_time &gt;= #{rangeStart}
              AND due_time &lt; #{rangeEnd}
              AND status IN
              <foreach collection="statuses" item="status" open="(" separator="," close=")">
                #{status}
              </foreach>
              <if test="platformCode != null and platformCode != ''">
                AND platform_code = #{platformCode}
              </if>
            GROUP BY COALESCE(platform_code, 'unknown'), status, bucketStart
            ORDER BY platformCode ASC, status ASC, bucketStart ASC
            </script>
            """)
    List<DispatchDueTimeBucketRow> aggregateDueTimeDistribution(@Param("taskType") String taskType,
                                                                @Param("rangeStart") LocalDateTime rangeStart,
                                                                @Param("rangeEnd") LocalDateTime rangeEnd,
                                                                @Param("bucketMinutes") int bucketMinutes,
                                                                @Param("statuses") List<String> statuses,
                                                                @Param("platformCode") String platformCode);
}
