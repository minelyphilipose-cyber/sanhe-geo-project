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
            SELECT i.* FROM project_self_media_schedule_item i
            JOIN company_channel_quota_ledger l
              ON l.biz_type = 'project_self_media_plan' AND l.biz_id = CAST(i.id AS CHAR)
            WHERE i.brand_id = #{brandId} AND i.platform = #{platform}
              AND i.company_id = #{companyId} AND l.company_id = i.company_id
              AND l.channel_code = CONCAT('self_media:', #{platform})
              AND l.period_type = #{periodType} AND l.period_key = #{periodKey}
              AND l.status = 'reserved' AND l.delta_count = 1
              AND i.quota_reservation_status = 'reserved' AND i.status = 'planned'
              AND i.schedule_id IS NULL AND i.article_id IS NULL
              AND i.generation_task_id IS NULL AND i.generation_batch_id IS NULL
              AND i.generation_claim_token IS NULL AND i.generation_claimed_at IS NULL
              AND i.planned_publish_at >= #{periodStart} AND i.planned_publish_at < #{periodEnd}
              AND i.generation_due_at >= #{replaceAfter}
            ORDER BY i.planned_publish_at ASC, i.id ASC LIMIT 1
            """)
    ProjectSelfMediaScheduleItem selectSafeReplaceableReservedPlan(@Param("brandId") Long brandId,
            @Param("platform") String platform, @Param("companyId") Long companyId,
            @Param("periodType") String periodType, @Param("periodKey") String periodKey,
            @Param("periodStart") LocalDateTime periodStart, @Param("periodEnd") LocalDateTime periodEnd,
            @Param("replaceAfter") LocalDateTime replaceAfter);

    @Update("""
            UPDATE project_self_media_schedule_item
            SET status = 'cancelled', quota_reservation_status = 'transferred',
                cancelled_by = #{operatorId}, cancelled_at = #{now}, updated_by = #{operatorId}, updated_at = #{now},
                cancellation_reason = '已由手动发布替换，预占额度转入手动发布任务'
            WHERE id = #{itemId} AND brand_id = #{brandId} AND platform = #{platform}
              AND status = 'planned' AND quota_reservation_status = 'reserved'
              AND schedule_id IS NULL AND article_id IS NULL
              AND generation_task_id IS NULL AND generation_batch_id IS NULL
              AND generation_claim_token IS NULL AND generation_claimed_at IS NULL
              AND planned_publish_at = #{expectedPublishAt} AND generation_due_at = #{expectedGenerationDueAt}
              AND generation_due_at >= #{replaceAfter}
            """)
    int cancelReservedPlanForManualPublish(@Param("itemId") Long itemId, @Param("brandId") Long brandId,
            @Param("platform") String platform, @Param("expectedPublishAt") LocalDateTime expectedPublishAt,
            @Param("expectedGenerationDueAt") LocalDateTime expectedGenerationDueAt,
            @Param("replaceAfter") LocalDateTime replaceAfter, @Param("now") LocalDateTime now,
            @Param("operatorId") Long operatorId);

    @Update("""
            UPDATE project_self_media_schedule_item i
            JOIN project_self_media_schedule_batch b ON b.id = i.batch_id
            SET i.status = 'generated',
                i.schedule_error_code = NULL,
                i.schedule_error_message = NULL,
                i.schedule_attempt_count = 0,
                i.next_schedule_attempt_at = #{now},
                i.updated_by = COALESCE(#{operatorId}, i.updated_by),
                i.updated_at = #{now},
                b.status = 'planned',
                b.failure_message = NULL,
                b.updated_by = COALESCE(#{operatorId}, b.updated_by),
                b.updated_at = #{now}
            WHERE i.article_id = #{articleId}
              AND i.platform = 'douyin'
              AND i.status = 'schedule_failed'
              AND i.schedule_error_code = 'DOUYIN_IMAGE_GENERATION_FAILED'
              AND i.quota_reservation_status = 'reserved'
              AND b.status IN ('planned', 'processing', 'failed', 'partial_failed')
            """)
    int restoreReservedDouyinScheduleAfterImagesApplied(@Param("articleId") Long articleId,
                                                         @Param("operatorId") Long operatorId,
                                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE project_self_media_schedule_item
            SET schedule_error_code = 'DOUYIN_IMAGE_READY_REQUIRES_RESCHEDULE',
                schedule_error_message = '图片已生成，原排期额度已退还，请在排期管理中点击“重新处理本批次”重新排期',
                updated_by = COALESCE(#{operatorId}, updated_by),
                updated_at = #{now}
            WHERE article_id = #{articleId}
              AND platform = 'douyin'
              AND status = 'schedule_failed'
              AND schedule_error_code = 'DOUYIN_IMAGE_GENERATION_FAILED'
              AND quota_reservation_status = 'refunded'
            """)
    int markRefundedDouyinScheduleForManualReschedule(@Param("articleId") Long articleId,
                                                       @Param("operatorId") Long operatorId,
                                                       @Param("now") LocalDateTime now);

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
