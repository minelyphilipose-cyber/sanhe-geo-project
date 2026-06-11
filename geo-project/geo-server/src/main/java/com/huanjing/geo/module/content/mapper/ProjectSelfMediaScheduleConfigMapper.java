package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectSelfMediaScheduleConfigMapper extends BaseMapper<ProjectSelfMediaScheduleConfig> {

    @Select("""
            SELECT *
            FROM project_self_media_schedule_config
            WHERE project_id = #{projectId}
            LIMIT 1
            """)
    ProjectSelfMediaScheduleConfig selectByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT *
            FROM project_self_media_schedule_config
            WHERE auto_schedule_enabled = 1
            ORDER BY project_id ASC
            LIMIT #{limit}
            """)
    List<ProjectSelfMediaScheduleConfig> selectEnabled(@Param("limit") int limit);
}
