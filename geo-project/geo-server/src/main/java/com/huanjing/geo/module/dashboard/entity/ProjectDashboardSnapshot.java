package com.huanjing.geo.module.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project_dashboard_snapshot")
public class ProjectDashboardSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String snapshotType;
    private String snapshotKey;
    private String snapshotValue;
    private LocalDate snapshotDate;
    private LocalDateTime refreshedAt;
}
