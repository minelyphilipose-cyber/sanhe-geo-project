package com.huanjing.geo.module.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_dashboard_share")
public class ProjectDashboardShare {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String shareCode;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
}
