package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.SelfMediaScheduleCapability;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SelfMediaScheduleCapabilityMapper extends BaseMapper<SelfMediaScheduleCapability> {

    @Select("""
            SELECT *
            FROM self_media_schedule_capability
            WHERE platform = #{platform}
            LIMIT 1
            """)
    SelfMediaScheduleCapability selectByPlatform(@Param("platform") String platform);
}
