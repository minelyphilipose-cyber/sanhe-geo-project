package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectSelfMediaScheduleBatchMapper extends BaseMapper<ProjectSelfMediaScheduleBatch> {

    @Select("""
            SELECT *
            FROM project_self_media_schedule_batch
            WHERE project_id = #{projectId}
              AND target_month = #{targetMonth}
            LIMIT 1
            """)
    ProjectSelfMediaScheduleBatch selectByProjectAndMonth(@Param("projectId") Long projectId,
                                                          @Param("targetMonth") String targetMonth);

    @Select("""
            SELECT *
            FROM project_self_media_schedule_batch
            WHERE status = 'processing'
            ORDER BY updated_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<ProjectSelfMediaScheduleBatch> selectProcessing(@Param("limit") int limit);
}
