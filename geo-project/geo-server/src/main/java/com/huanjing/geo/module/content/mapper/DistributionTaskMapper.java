package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.DistributionTask;
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
              AND status = 'filling'
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int touchSemiAutoHeartbeat(@Param("taskId") Long taskId, @Param("heartbeatAt") LocalDateTime heartbeatAt);

    @Update("""
            UPDATE distribution_tasks
            SET status = 'published',
                published_at = #{publishedAt},
                published_by = #{publishedBy}
            WHERE id = #{taskId}
              AND status = 'filled'
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int markSemiAutoPublished(
            @Param("taskId") Long taskId,
            @Param("publishedAt") LocalDateTime publishedAt,
            @Param("publishedBy") Long publishedBy
    );

    @Select("""
            SELECT id, article_id, project_id, self_media_account_id, status, dispatch_mode,
                   fill_token_issued_at, filled_at, last_heartbeat_at
            FROM distribution_tasks
            WHERE dispatch_mode = 'SEMI_AUTO'
              AND (
                    (status = 'token_issued' AND fill_token_issued_at < #{tokenIssuedBefore})
                 OR (status = 'filling' AND last_heartbeat_at < #{heartbeatBefore})
              )
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<DistributionTask> selectStaleSemiAutoTasks(
            @Param("tokenIssuedBefore") LocalDateTime tokenIssuedBefore,
            @Param("heartbeatBefore") LocalDateTime heartbeatBefore,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE distribution_tasks
            SET status = 'pending',
                fill_token_issued_at = NULL,
                filled_at = NULL,
                last_heartbeat_at = NULL
            WHERE id = #{taskId}
              AND status = #{expectedStatus}
              AND dispatch_mode = 'SEMI_AUTO'
            """)
    int reclaimSemiAutoTask(@Param("taskId") Long taskId, @Param("expectedStatus") String expectedStatus);
}
