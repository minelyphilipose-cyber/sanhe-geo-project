package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.extension.dto.ExtensionTaskListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DistributionTaskMapper extends BaseMapper<DistributionTask> {

    @Update("""
            UPDATE distribution_tasks
            SET status = 'filling',
                last_heartbeat_at = #{now}
            WHERE id = #{taskId}
              AND status = 'token_issued'
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int markSemiAutoFilling(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE distribution_tasks
            SET status = 'filled',
                filled_at = #{filledAt}
            WHERE id = #{taskId}
              AND status = 'filling'
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int markSemiAutoFilled(@Param("taskId") Long taskId, @Param("filledAt") LocalDateTime filledAt);

    @Update("""
            UPDATE distribution_tasks
            SET last_heartbeat_at = #{heartbeatAt}
            WHERE id = #{taskId}
              AND status IN ('filling', 'filled')
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int touchSemiAutoHeartbeat(@Param("taskId") Long taskId, @Param("heartbeatAt") LocalDateTime heartbeatAt);

    @Update("""
            UPDATE distribution_tasks
            SET status = 'published',
                published_at = #{publishedAt},
                published_by = #{publishedBy},
                finished_at = #{publishedAt}
            WHERE id = #{taskId}
              AND status = 'filled'
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int markSemiAutoPublished(
            @Param("taskId") Long taskId,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("publishedBy") Long publishedBy
    );

    @Update("""
            UPDATE distribution_tasks
            SET status = 'failed',
                failure_kind = 'USER_CANCELLED',
                error_message = #{reason},
                finished_at = #{abandonedAt},
                locked_until = NULL
            WHERE id = #{taskId}
              AND status IN ('token_issued', 'filling', 'filled')
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int abandonSemiAutoTask(
            @Param("taskId") Long taskId,
            @Param("reason") String reason,
            @Param("abandonedAt") LocalDateTime abandonedAt
    );

    @Select("""
            SELECT id, article_id, project_id, self_media_account_id, status, dispatch_mode,
                   fill_token_issued_at, filled_at, last_heartbeat_at
            FROM distribution_tasks
            WHERE dispatch_mode = 'SEMI_AUTO'
              AND (
                    (status = 'token_issued' AND fill_token_issued_at < #{tokenIssuedBefore})
                 OR (status = 'filling' AND last_heartbeat_at < #{heartbeatBefore})
                 OR (status = 'filled' AND filled_at < #{heartbeatBefore})
              )
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<DistributionTask> selectStaleSemiAutoTasks(
            @Param("tokenIssuedBefore") LocalDateTime tokenIssuedBefore,
            @Param("heartbeatBefore") LocalDateTime heartbeatBefore,
            @Param("limit") int limit
    );

    @Select("""
            SELECT id, article_id, project_id, self_media_account_id, status, dispatch_mode,
                   target_kind, platform_article_id, platform_publish_id, external_status,
                   review_status, review_feedback, response_payload, submitted_at, created_at,
                   review_check_count, next_review_check_at, review_locked_until
            FROM distribution_tasks
            WHERE target_kind = 'mp_account'
              AND dispatch_mode = 'AUTO'
              AND status = 'submitted'
              AND review_status IN ('under_review', 'unknown')
              AND (next_review_check_at IS NULL OR next_review_check_at <= #{now})
              AND (review_locked_until IS NULL OR review_locked_until < #{now})
            ORDER BY COALESCE(next_review_check_at, created_at), id
            LIMIT #{limit}
            """)
    List<DistributionTask> selectDueReviewTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE distribution_tasks
            SET review_locked_until = #{lockedUntil}
            WHERE id = #{taskId}
              AND (review_locked_until IS NULL OR review_locked_until < #{now})
              AND status = 'submitted'
              AND review_status IN ('under_review', 'unknown')
            """)
    int claimReviewTask(@Param("taskId") Long taskId,
                        @Param("now") LocalDateTime now,
                        @Param("lockedUntil") LocalDateTime lockedUntil);

    @Select("""
            SELECT t.id AS taskId,
                   COALESCE(sma.platform, t.integration_method) AS platform,
                   t.status AS status,
                   t.published_url AS publishUrl,
                   a.title AS title,
                   t.created_at AS createdAt,
                   t.fill_token_issued_at AS fillTokenIssuedAt,
                   t.operator_id AS operatorId,
                   p.brand_id AS brandId
            FROM distribution_tasks t
            INNER JOIN article_draft a ON a.id = t.article_id
            INNER JOIN project p ON p.id = t.project_id
            LEFT JOIN self_media_account sma ON sma.id = t.self_media_account_id
            WHERE t.dispatch_mode = 'SEMI_AUTO'
              AND t.operator_id = #{operatorId}
              AND t.status IN ('token_issued', 'filling', 'filled')
            ORDER BY t.fill_token_issued_at DESC
            LIMIT #{limit}
            """)
    List<ExtensionTaskListRow> selectExtensionSemiAutoTasks(
            @Param("operatorId") Long operatorId,
            @Param("limit") int limit
    );

    @Select("""
            SELECT id, project_id, self_media_account_id, status, dispatch_mode, operator_id, fill_payload
            FROM distribution_tasks
            WHERE id = #{taskId}
            """)
    DistributionTask selectExtensionFillContext(@Param("taskId") Long taskId);

    @Update("""
            UPDATE distribution_tasks
            SET status = 'token_issued',
                fill_token_issued_at = #{reissuedAt},
                filled_at = NULL,
                last_heartbeat_at = NULL
            WHERE id = #{taskId}
              AND status = #{expectedStatus}
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int reclaimSemiAutoTask(@Param("taskId") Long taskId,
                             @Param("expectedStatus") String expectedStatus,
                             @Param("reissuedAt") LocalDateTime reissuedAt);

    @Update("""
            UPDATE distribution_tasks
            SET status = 'published',
                published_url = #{publishedUrl},
                published_at = #{publishedAt},
                finished_at = #{publishedAt},
                error_message = NULL,
                failure_kind = NULL
            WHERE id = #{taskId}
              AND target_kind = 'authority_media'
              AND status = 'submitted'
            """)
    int markAuthorityMediaPublished(@Param("taskId") Long taskId,
                                    @Param("publishedUrl") String publishedUrl,
                                    @Param("publishedAt") LocalDateTime publishedAt);

    @Update("""
            UPDATE distribution_tasks
            SET status = 'failed',
                failure_kind = #{failureKind},
                error_message = #{errorMessage},
                finished_at = #{failedAt}
            WHERE id = #{taskId}
              AND target_kind = 'authority_media'
              AND status = 'submitted'
            """)
    int markAuthorityMediaFailed(@Param("taskId") Long taskId,
                                 @Param("failureKind") String failureKind,
                                 @Param("errorMessage") String errorMessage,
                                 @Param("failedAt") LocalDateTime failedAt);
}
