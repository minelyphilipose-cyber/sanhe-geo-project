package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface BatchArticleGenerationBatchMapper extends BaseMapper<BatchArticleGenerationBatch> {

    @Select("SELECT * FROM batch_article_generation_batch WHERE idempotency_key = #{idempotencyKey} LIMIT 1")
    BatchArticleGenerationBatch selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE batch_article_generation_batch
            SET status = 'running',
                started_at = COALESCE(started_at, #{now}),
                finished_at = NULL,
                error_message = NULL,
                updated_at = #{now}
            WHERE id = #{batchId}
            """)
    int markRunningClearingFinished(@Param("batchId") Long batchId,
                                    @Param("now") LocalDateTime now);
}
