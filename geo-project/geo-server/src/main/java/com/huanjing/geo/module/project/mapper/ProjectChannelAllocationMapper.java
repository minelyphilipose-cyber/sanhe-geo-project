package com.huanjing.geo.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.project.dto.ProjectChannelAllocationProjectRow;
import com.huanjing.geo.module.project.entity.ProjectChannelAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectChannelAllocationMapper extends BaseMapper<ProjectChannelAllocation> {

    @Select("""
            SELECT COALESCE(SUM(a.allocated_count), 0)
            FROM project_channel_allocation a
            JOIN project p ON p.id = a.project_id
            WHERE a.company_id = #{companyId}
              AND a.channel_code = #{channelCode}
              AND p.deleted_at IS NULL
              AND p.status IN ('submitted', 'approved_pending_setup', 'setup_ready', 'active', 'paused')
              AND (#{excludeProjectId} IS NULL OR p.id <> #{excludeProjectId})
            """)
    long sumActiveAllocatedByCompanyAndChannel(@Param("companyId") Long companyId,
                                               @Param("channelCode") String channelCode,
                                               @Param("excludeProjectId") Long excludeProjectId);

    @Select("""
            SELECT p.id AS projectId,
                   p.project_name AS projectName,
                   a.allocated_count AS allocatedCount
            FROM project_channel_allocation a
            JOIN project p ON p.id = a.project_id
            WHERE a.company_id = #{companyId}
              AND a.channel_code = #{channelCode}
              AND p.deleted_at IS NULL
              AND p.status IN ('submitted', 'approved_pending_setup', 'setup_ready', 'active', 'paused')
              AND a.allocated_count > 0
              AND (#{excludeProjectId} IS NULL OR p.id <> #{excludeProjectId})
            ORDER BY p.id ASC
            FOR UPDATE
            """)
    List<ProjectChannelAllocationProjectRow> activeProjectRowsForUpdate(@Param("companyId") Long companyId,
                                                                        @Param("channelCode") String channelCode,
                                                                        @Param("excludeProjectId") Long excludeProjectId);

    @Select("""
            SELECT COALESCE(MAX(revision), 0)
            FROM project_channel_allocation
            WHERE company_id = #{companyId}
            """)
    long maxRevisionByCompany(@Param("companyId") Long companyId);

    @Select("""
            SELECT p.id AS projectId,
                   p.project_name AS projectName,
                   a.allocated_count AS allocatedCount
            FROM project_channel_allocation a
            JOIN project p ON p.id = a.project_id
            WHERE a.company_id = #{companyId}
              AND a.channel_code = #{channelCode}
              AND p.deleted_at IS NULL
              AND p.status IN ('submitted', 'approved_pending_setup', 'setup_ready', 'active', 'paused')
              AND a.allocated_count > 0
            ORDER BY p.id ASC
            """)
    List<ProjectChannelAllocationProjectRow> activeProjectRows(@Param("companyId") Long companyId,
                                                               @Param("channelCode") String channelCode);

}
