package com.huanjing.geo.module.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dispatch.entity.ProjectPollRotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectPollRotationMapper extends BaseMapper<ProjectPollRotation> {

    @Select("""
            SELECT *
            FROM project_poll_rotation
            WHERE project_id = #{projectId}
              AND priority_level = #{priorityLevel}
            LIMIT 1
            FOR UPDATE
            """)
    ProjectPollRotation selectForUpdate(@Param("projectId") Long projectId,
                                        @Param("priorityLevel") String priorityLevel);
}
