package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_self_media_schedule_plan")
public class ProjectSelfMediaSchedulePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long brandId;
    private String targetMonth;
    private String triggerMode;
    private LocalDateTime plannedExecuteAt;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextAttemptAt;
    private String failureMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
