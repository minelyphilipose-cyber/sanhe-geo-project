package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.entity.ProjectQuotaSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectQuotaSnapshotMapper extends BaseMapper<ProjectQuotaSnapshot> {

    @Select("""
            SELECT *
            FROM project_quota_snapshot
            WHERE start_request_id = #{startRequestId}
            ORDER BY id DESC
            LIMIT 1
            """)
    ProjectQuotaSnapshot selectLatestByStartRequestId(@Param("startRequestId") Long startRequestId);
}
