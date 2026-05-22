package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PollBatchShardItemMapper extends BaseMapper<PollBatchShardItem> {

    @Select("""
            SELECT *
            FROM poll_batch_shard_items
            WHERE shard_id = #{shardId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<PollBatchShardItem> selectByShardId(@Param("shardId") Long shardId);
}
