package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaPlanningTask;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ProjectSelfMediaPlanningTaskMapper extends BaseMapper<ProjectSelfMediaPlanningTask> {
    @Select("SELECT * FROM project_self_media_planning_task WHERE project_id=#{projectId} AND idempotency_key=#{key} LIMIT 1")
    ProjectSelfMediaPlanningTask selectByKey(@Param("projectId") Long projectId, @Param("key") String key);

    @Select("SELECT * FROM project_self_media_planning_task WHERE project_id=#{projectId} AND target_month=#{month} ORDER BY id DESC LIMIT 1")
    ProjectSelfMediaPlanningTask selectLatest(@Param("projectId") Long projectId, @Param("month") String month);

    // The row lock and all quota/plan writes commit together; a restart leaves the request pending.
    @Select("SELECT * FROM project_self_media_planning_task WHERE status='pending' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
    ProjectSelfMediaPlanningTask selectPendingForUpdate();

    @Select("SELECT * FROM project_self_media_planning_task WHERE id=#{id} FOR UPDATE")
    ProjectSelfMediaPlanningTask selectForUpdate(@Param("id") Long id);
}
