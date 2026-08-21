package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface ProjectSelfMediaScheduleItemMapper extends BaseMapper<ProjectSelfMediaScheduleItem> {

    @Select("""
            SELECT *
            FROM project_self_media_schedule_item
            WHERE batch_id = #{batchId}
            ORDER BY planned_execution_at ASC, id ASC
            """)
    List<ProjectSelfMediaScheduleItem> selectByBatchId(@Param("batchId") Long batchId);

    @Select("""
            SELECT *
            FROM project_self_media_schedule_item
            WHERE id > #{afterId}
              AND ((status = 'planned' AND COALESCE(next_generation_attempt_at, generation_due_at) <= #{deadline})
               OR (status = 'generating' AND planned_execution_at <= #{deadline})
              )
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<ProjectSelfMediaScheduleItem> selectAtRiskItems(@Param("deadline") LocalDateTime deadline,
                                                          @Param("afterId") long afterId,
                                                          @Param("limit") int limit);

    @Update("""
            UPDATE project_self_media_schedule_item
            SET status = 'generating', generation_claim_token = #{claimToken},
                generation_claimed_at = #{now}, updated_by = #{operatorId}, updated_at = #{now}
            WHERE id = #{itemId}
              AND status = 'planned'
              AND COALESCE(next_generation_attempt_at, generation_due_at) <= #{now}
            """)
    int claimDueGeneration(@Param("itemId") Long itemId,
                           @Param("claimToken") String claimToken,
                           @Param("operatorId") Long operatorId,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE project_self_media_schedule_item
            SET status = 'planned', generation_claim_token = NULL, generation_claimed_at = NULL,
                next_generation_attempt_at = #{now}, updated_at = #{now}
            WHERE status = 'generating' AND generation_task_id IS NULL
              AND generation_claimed_at IS NOT NULL AND generation_claimed_at < #{before}
            """)
    int releaseStaleGenerationClaims(@Param("before") LocalDateTime before,
                                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(1) FROM project_self_media_schedule_item
            WHERE status IN ('planned', 'generating', 'generated')
            """)
    long countPendingItems();

    @Select("""
            SELECT COUNT(1) FROM project_self_media_schedule_item
            WHERE status IN ('planned', 'generating') AND planned_execution_at < #{now}
            """)
    long countOverdueItems(@Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(1) FROM project_self_media_schedule_item
            WHERE self_media_account_id = #{accountId} AND id <> #{excludeItemId}
              AND planned_publish_at >= #{dayStart} AND planned_publish_at < #{dayEnd}
              AND status NOT IN ('cancelled', 'skipped', 'generation_failed', 'schedule_failed')
            """)
    long countActivePlanItemsForAccountDay(@Param("accountId") Long accountId,
                                           @Param("excludeItemId") Long excludeItemId,
                                           @Param("dayStart") LocalDateTime dayStart,
                                           @Param("dayEnd") LocalDateTime dayEnd);

    @Select("""
            SELECT COUNT(1) FROM project_self_media_schedule_item
            WHERE self_media_account_id = #{accountId} AND id <> #{excludeItemId}
              AND planned_execution_at = #{executionAt}
              AND status NOT IN ('cancelled', 'skipped', 'generation_failed', 'schedule_failed')
            """)
    long countActivePlanItemsAtExecution(@Param("accountId") Long accountId,
                                         @Param("excludeItemId") Long excludeItemId,
                                         @Param("executionAt") LocalDateTime executionAt);
}
