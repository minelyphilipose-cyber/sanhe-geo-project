package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.entity.BaselineCollectionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BaselineCollectionTaskMapper extends BaseMapper<BaselineCollectionTask> {
    @Update("""
            UPDATE baseline_collection_task
            SET success_observation_count = success_observation_count + #{successDelta},
                failed_observation_count = failed_observation_count + #{failedDelta},
                score_count = score_count + #{scoreDelta},
                competitor_mention_count = competitor_mention_count + #{competitorMentionDelta},
                updated_at = NOW()
            WHERE id = #{taskId}
              AND status <> 'CANCELED'
            """)
    int incrementProgress(@Param("taskId") Long taskId,
                          @Param("successDelta") int successDelta,
                          @Param("failedDelta") int failedDelta,
                          @Param("scoreDelta") int scoreDelta,
                          @Param("competitorMentionDelta") int competitorMentionDelta);

    @Select("""
            SELECT COUNT(1)
            FROM baseline_collection_task
            WHERE status = 'RUNNING'
            """)
    int countRunning();

    @Select("""
            SELECT COUNT(1) + 1
            FROM baseline_collection_task
            WHERE status = 'PENDING'
              AND id < #{taskId}
            """)
    int queuePosition(@Param("taskId") Long taskId);

    @Select("""
            SELECT *
            FROM baseline_collection_task
            WHERE status = 'PENDING'
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<BaselineCollectionTask> selectPending(@Param("limit") int limit);
}
