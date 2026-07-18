package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.PollProviderCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PollProviderCallMapper extends BaseMapper<PollProviderCall> {

    @Select("""
            SELECT COALESCE(MAX(sequence_no), 0)
            FROM poll_provider_calls
            WHERE attempt_id = #{attemptId}
            """)
    int selectMaxSequenceNo(@Param("attemptId") Long attemptId);
}
