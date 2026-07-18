package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PollResultMapper extends BaseMapper<PollResult> {

    @Select("""
            SELECT *
            FROM poll_results
            WHERE id = #{id}
            FOR UPDATE
            """)
    PollResult selectByIdForUpdate(@Param("id") Long id);
}
