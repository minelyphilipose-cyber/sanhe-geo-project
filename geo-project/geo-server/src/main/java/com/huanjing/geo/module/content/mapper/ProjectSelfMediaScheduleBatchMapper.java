package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectSelfMediaScheduleBatchMapper extends BaseMapper<ProjectSelfMediaScheduleBatch> {

    @Select("""
            SELECT *
            FROM project_self_media_schedule_batch
            WHERE project_id = #{projectId}
              AND target_month = #{targetMonth}
            LIMIT 1
            """)
    ProjectSelfMediaScheduleBatch selectByProjectAndMonth(@Param("projectId") Long projectId,
                                                          @Param("targetMonth") String targetMonth);

    @Select("""
            SELECT *
            FROM project_self_media_schedule_batch
            WHERE status IN ('planned', 'processing')
            ORDER BY updated_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ProjectSelfMediaScheduleBatch> selectProcessing(@Param("limit") int limit);

    @Select("""
            SELECT DISTINCT b.*
            FROM project_self_media_schedule_batch b
            JOIN project_self_media_schedule_item i ON i.batch_id = b.id
            LEFT JOIN article_draft a ON a.id = i.article_id
            LEFT JOIN douyin_article_image_batch image_batch ON image_batch.id = a.douyin_image_batch_id
            WHERE b.status NOT IN ('paused', 'skipped')
              AND (
                  (i.status = 'schedule_failed'
                   AND i.schedule_error_code = 'DOUYIN_IMAGE_GENERATION_FAILED'
                   AND i.quota_reservation_status = 'reserved'
                   AND (
                        LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%waiting for permits%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%permit unavailable%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%permit busy%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%rate limit%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%http 429%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%too many requests%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%timeout%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%timed out%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%connection refused%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%connection reset%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%connection closed%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%connectexception%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%socketexception%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%circuit open%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) LIKE '%circuit-open%'
                     OR LOWER(COALESCE(i.schedule_error_message, '')) REGEXP 'http[[:space:]]+5[0-9]{2}'
                     OR i.schedule_error_message LIKE '%图片模型当前请求较多%'
                     OR i.schedule_error_message LIKE '%图片生成超时%'
                     OR i.schedule_error_message LIKE '%任务中断或超时%'
                   ))
                OR (i.status = 'cancelled'
                    AND i.platform = 'douyin'
                    AND i.schedule_error_code = 'DOUYIN_IMAGE_GENERATION_FAILED'
                    AND i.quota_reservation_status = 'refunded'
                    AND i.cancelled_by IS NULL
                    AND i.cancellation_reason = '排期失败超过可重试时限，系统已释放预占额度'
                    AND COALESCE(a.status, '') <> 'deleted'
                    AND image_batch.article_id = a.id
                    AND image_batch.status = 'applied'
                    AND image_batch.applied_version_no = a.current_version_no)
              )
            ORDER BY b.updated_at ASC, b.id ASC
            LIMIT #{limit}
            """)
    List<ProjectSelfMediaScheduleBatch> selectRecoverableDouyinImageFailures(@Param("limit") int limit);
}
