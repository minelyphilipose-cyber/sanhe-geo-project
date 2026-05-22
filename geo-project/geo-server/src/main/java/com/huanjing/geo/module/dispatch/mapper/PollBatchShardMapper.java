package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PollBatchShardMapper extends BaseMapper<PollBatchShard> {

    @Select("""
            SELECT *
            FROM poll_batch_shards
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollBatchShard selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            SELECT COUNT(1)
            FROM poll_batch_shards
            WHERE batch_id = #{batchId}
              AND status IN ('completed', 'failed')
            """)
    long countTerminalByBatchId(@Param("batchId") Long batchId);

    @Select("""
            SELECT *
            FROM poll_batch_shards
            WHERE batch_id = #{batchId}
            ORDER BY platform_id ASC, shard_no ASC
            """)
    List<PollBatchShard> selectByBatchId(@Param("batchId") Long batchId);
}
