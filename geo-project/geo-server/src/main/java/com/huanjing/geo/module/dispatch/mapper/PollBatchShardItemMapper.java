package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
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

    @Update("""
            UPDATE poll_batch_shard_items
            SET result_snapshot_json = #{snapshotJson},
                result_snapshot_at = #{snapshotAt},
                last_error = NULL
            WHERE id = #{itemId}
              AND status = 'pending'
            """)
    int stageResultSnapshot(@Param("itemId") Long itemId,
                            @Param("snapshotJson") String snapshotJson,
                            @Param("snapshotAt") LocalDateTime snapshotAt);

    @Update("""
            UPDATE poll_batch_shard_items
            SET poll_result_id = #{pollResultId},
                status = #{status},
                last_error = #{lastError},
                result_snapshot_json = NULL,
                result_snapshot_at = NULL
            WHERE id = #{itemId}
              AND status = 'pending'
            """)
    int markResultProjected(@Param("itemId") Long itemId,
                            @Param("pollResultId") Long pollResultId,
                            @Param("status") String status,
                            @Param("lastError") String lastError);
}
