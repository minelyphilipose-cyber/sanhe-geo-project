package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_self_media_planning_task")
public class ProjectSelfMediaPlanningTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String targetMonth;
    private String idempotencyKey;
    private String requestPayload;
    private String status;
    private String resultPayload;
    private String failureMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
