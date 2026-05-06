package com.huanjing.geo.module.dashboard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_dashboard_advice")
public class ProjectDashboardAdvice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String summary;
    private String highlights;
    private String improvementDirections;
    private String nextActions;
    private String status;
    private LocalDateTime publishedAt;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
