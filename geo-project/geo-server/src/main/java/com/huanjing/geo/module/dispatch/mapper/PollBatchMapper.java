package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PollBatchMapper extends BaseMapper<PollBatch> {

    @Select("""
            SELECT *
            FROM poll_batches
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollBatch selectByIdForUpdate(@Param("id") Long id);
}
