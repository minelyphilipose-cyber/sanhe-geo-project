package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.entity.ProjectStartRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectStartRequestMapper extends BaseMapper<ProjectStartRequest> {

    @Select("""
            SELECT *
            FROM project_start_request
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            LIMIT 1
            """)
    ProjectStartRequest selectLatestByProjectId(@Param("projectId") Long projectId);

    @Select("""
            SELECT *
            FROM project_start_request
            WHERE request_no = #{requestNo}
            LIMIT 1
            """)
    ProjectStartRequest selectByRequestNo(@Param("requestNo") String requestNo);
}
