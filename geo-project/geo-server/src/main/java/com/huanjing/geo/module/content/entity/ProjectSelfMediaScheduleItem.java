package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_self_media_schedule_item")
public class ProjectSelfMediaScheduleItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long projectId;
    private Long brandId;
    private Long companyId;
    private Long selfMediaAccountId;
    private String platform;
    private String status;
    private LocalDateTime plannedExecutionAt;
    private LocalDateTime plannedPublishAt;
    private LocalDateTime generationDueAt;
    private String generationIdempotencyKey;
    private Long generationBatchId;
    private Long generationTaskId;
    private String generationClaimToken;
    private LocalDateTime generationClaimedAt;
    private Long articleId;
    private Long scheduleId;
    private String scheduleIdempotencyKey;
    private Integer scheduleAttemptCount;
    private LocalDateTime nextScheduleAttemptAt;
    private Long promptTemplateId;
    private Long promptTemplateVersionId;
    private String promptTemplateName;
    private Integer generationAttemptCount;
    private LocalDateTime nextGenerationAttemptAt;
    private String generationErrorMessage;
    private String scheduleErrorCode;
    private String scheduleErrorMessage;
    private String quotaReservationStatus;
    private Long cancelledBy;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
