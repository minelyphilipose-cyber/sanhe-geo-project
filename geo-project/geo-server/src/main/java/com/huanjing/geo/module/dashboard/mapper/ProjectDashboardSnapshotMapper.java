package com.huanjing.geo.module.dashboard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huanjing.geo.module.dashboard.entity.ProjectDashboardSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectDashboardSnapshotMapper extends BaseMapper<ProjectDashboardSnapshot> {

    @Insert("""
            <script>
            INSERT INTO project_dashboard_snapshot
                (project_id, snapshot_type, snapshot_key, snapshot_value, snapshot_date, refreshed_at)
            VALUES
            <foreach collection="snapshots" item="item" separator=",">
                (#{item.projectId}, #{item.snapshotType}, #{item.snapshotKey}, #{item.snapshotValue},
                 #{item.snapshotDate}, #{item.refreshedAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("snapshots") List<ProjectDashboardSnapshot> snapshots);
}
