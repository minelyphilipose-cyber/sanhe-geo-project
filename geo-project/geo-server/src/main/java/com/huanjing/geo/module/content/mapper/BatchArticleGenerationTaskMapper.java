package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.dto.SubjectBrandLastSelectedRow;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BatchArticleGenerationTaskMapper extends BaseMapper<BatchArticleGenerationTask> {

    @Select("""
            <script>
            SELECT subject_brand_id AS subjectBrandId,
                   MAX(id) AS lastSelectedTaskId,
                   MAX(created_at) AS lastSelectedAt
            FROM batch_article_generation_task
            WHERE source_brand_id = #{sourceBrandId}
              AND subject_brand_id IN
              <foreach collection='subjectBrandIds' item='subjectBrandId' open='(' separator=',' close=')'>
                #{subjectBrandId}
              </foreach>
            GROUP BY subject_brand_id
            </script>
            """)
    List<SubjectBrandLastSelectedRow> selectLastSelectedBySourceBrand(@Param("sourceBrandId") Long sourceBrandId,
                                                                      @Param("subjectBrandIds") List<Long> subjectBrandIds);

    @Update("""
            UPDATE batch_article_generation_task
            SET status = 'running',
                started_at = #{now},
                finished_at = NULL,
                error_message = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'pending'
            """)
    int claimPendingForRun(@Param("taskId") Long taskId,
                           @Param("batchId") Long batchId,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'running'
              AND started_at = #{claimedStartedAt}
            """)
    int renewRunningClaim(@Param("taskId") Long taskId,
                          @Param("batchId") Long batchId,
                          @Param("claimedStartedAt") LocalDateTime claimedStartedAt,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET status = 'pending',
                started_at = NULL,
                finished_at = NULL,
                error_message = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'running'
              AND started_at = #{claimedStartedAt}
            """)
    int releaseRunningClaim(@Param("taskId") Long taskId,
                            @Param("batchId") Long batchId,
                            @Param("claimedStartedAt") LocalDateTime claimedStartedAt,
                            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET status = 'pending',
                article_id = NULL,
                retry_count = 0,
                infrastructure_retry_count = 0,
                compliance_retry_count = 0,
                started_at = NULL,
                finished_at = NULL,
                error_message = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'failed'
            """)
    int resetFailedForRetry(@Param("taskId") Long taskId,
                            @Param("batchId") Long batchId,
                            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET model_platform_code = #{platformCode},
                model_id = #{modelId},
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'pending'
            """)
    int updateRetryModel(@Param("taskId") Long taskId,
                         @Param("batchId") Long batchId,
                         @Param("platformCode") String platformCode,
                         @Param("modelId") String modelId,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET model_platform_code = #{platformCode},
                model_id = #{modelId},
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'pending'
              AND model_platform_code IS NULL
              AND model_id IS NULL
            """)
    int assignPendingModel(@Param("taskId") Long taskId,
                           @Param("batchId") Long batchId,
                           @Param("platformCode") String platformCode,
                           @Param("modelId") String modelId,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET status = 'pending',
                model_platform_code = #{platformCode},
                model_id = #{modelId},
                retry_count = COALESCE(infrastructure_retry_count, 0) + 1
                    + GREATEST(COALESCE(compliance_retry_count, 0), #{complianceRetryCount}),
                infrastructure_retry_count = COALESCE(infrastructure_retry_count, 0) + 1,
                compliance_retry_count = GREATEST(
                    COALESCE(compliance_retry_count, 0),
                    #{complianceRetryCount}
                ),
                started_at = NULL,
                finished_at = NULL,
                error_message = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'running'
              AND started_at = #{claimedStartedAt}
              AND COALESCE(infrastructure_retry_count, 0) < #{maxRetryCount}
            """)
    int resetRunningForInfrastructureRetry(@Param("taskId") Long taskId,
                                           @Param("batchId") Long batchId,
                                           @Param("claimedStartedAt") LocalDateTime claimedStartedAt,
                                           @Param("platformCode") String platformCode,
                                           @Param("modelId") String modelId,
                                           @Param("complianceRetryCount") int complianceRetryCount,
                                           @Param("maxRetryCount") int maxRetryCount,
                                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET status = 'pending',
                started_at = NULL,
                finished_at = NULL,
                error_message = #{reason},
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'running'
              AND started_at = #{claimedStartedAt}
            """)
    int resetRunningForCapacityDeferral(@Param("taskId") Long taskId,
                                        @Param("batchId") Long batchId,
                                        @Param("claimedStartedAt") LocalDateTime claimedStartedAt,
                                        @Param("reason") String reason,
                                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE batch_article_generation_task
            SET status = 'pending',
                started_at = NULL,
                finished_at = NULL,
                error_message = NULL,
                updated_at = #{now}
            WHERE id = #{taskId}
              AND batch_id = #{batchId}
              AND status = 'running'
              AND started_at = #{claimedStartedAt}
            """)
    int resetRunningForRecovery(@Param("taskId") Long taskId,
                                @Param("batchId") Long batchId,
                                @Param("claimedStartedAt") LocalDateTime claimedStartedAt,
                                @Param("now") LocalDateTime now);
}
