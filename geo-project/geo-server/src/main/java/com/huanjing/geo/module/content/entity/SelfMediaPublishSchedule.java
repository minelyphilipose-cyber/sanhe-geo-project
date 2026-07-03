package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_publish_schedule")
public class SelfMediaPublishSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private String requestIdempotencyKey;
    private Long articleId;
    private Long distributionTaskId;
    private Long brandId;
    private Long selfMediaAccountId;
    private Long browserEnvironmentId;
    private Long browserEnvironmentAccountId;
    private String platform;

    private String scheduleStrategy;
    private LocalDateTime plannedPublishAt;
    private LocalDateTime platformScheduledAt;
    private Integer scheduleDriftSeconds;
    private String scheduleDriftReason;

    private String status;
    private String queueKind;
    private Integer queuePriority;

    private String platformScheduleId;
    private String platformPublishId;
    private String platformPublishedUrl;
    private String publishCheckTitle;
    private String publishCheckCoverUrl;
    private String publishCheckLocationName;
    private String publishCheckFingerprint;

    private String baseIdempotencyKey;
    private Integer generationNo;

    private Integer attemptCount;
    private Integer maxAttempts;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextAttemptAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime lockedUntil;

    private String failureCode;
    private String failureMessage;
    private String diagnosticsJson;
    private String runtimeStage;
    private LocalDateTime runtimeStageAt;
    private String runtimeStageMessage;
    private String runtimeWorkerId;
    private String runtimeExtensionInstallId;

    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime cancelRequestedAt;
    private LocalDateTime publishedConfirmedAt;
}
