package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_self_media_schedule_batch")
public class ProjectSelfMediaScheduleBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long brandId;
    private Long companyId;
    private String targetMonth;
    private String triggerMode;
    private String status;
    private String scheduleStrategy;
    private Integer articleCount;
    private Integer accountCount;
    private Integer plannedCount;
    private Integer createdCount;
    private Integer rejectedCount;
    private String generationBatchIds;
    private String requestPayload;
    private String resultSnapshot;
    private String failureMessage;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
