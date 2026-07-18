package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface PollBatchMapper extends BaseMapper<PollBatch> {

    @Select("""
            SELECT *
            FROM poll_batches
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollBatch selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT id
            FROM project
            WHERE id = #{projectId}
            FOR UPDATE
            """)
    Long lockProjectForManualPoll(@Param("projectId") Long projectId);

    @Select("""
            SELECT COALESCE(MAX(batch_no), 0)
            FROM poll_batches
            WHERE project_id = #{projectId}
              AND batch_date = #{batchDate}
              AND question_tier = #{questionTier}
            """)
    int selectMaxBatchNo(@Param("projectId") Long projectId,
                         @Param("batchDate") LocalDate batchDate,
                         @Param("questionTier") String questionTier);
}
