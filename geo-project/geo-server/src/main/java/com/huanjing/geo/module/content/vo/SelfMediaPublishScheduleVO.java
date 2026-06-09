package com.huanjing.geo.module.content.vo;

import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SelfMediaPublishScheduleVO {
    private Long id;
    private Long requestId;
    private String requestIdempotencyKey;
    private Long articleId;
    private String articleTitle;
    private Long brandId;
    private String brandName;
    private Long selfMediaAccountId;
    private String selfMediaAccountName;
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
    private LocalDateTime lockedUntil;
    private String failureCode;
    private String failureMessage;
    private String diagnosticsJson;
    private List<SelfMediaPublishScheduleAlertVO> activeAlerts = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime cancelRequestedAt;
    private LocalDateTime publishedConfirmedAt;

    public static SelfMediaPublishScheduleVO from(SelfMediaPublishSchedule row) {
        if (row == null) {
            return null;
        }
        SelfMediaPublishScheduleVO vo = new SelfMediaPublishScheduleVO();
        vo.setId(row.getId());
        vo.setRequestId(row.getRequestId());
        vo.setRequestIdempotencyKey(row.getRequestIdempotencyKey());
        vo.setArticleId(row.getArticleId());
        vo.setBrandId(row.getBrandId());
        vo.setSelfMediaAccountId(row.getSelfMediaAccountId());
        vo.setBrowserEnvironmentId(row.getBrowserEnvironmentId());
        vo.setBrowserEnvironmentAccountId(row.getBrowserEnvironmentAccountId());
        vo.setPlatform(row.getPlatform());
        vo.setScheduleStrategy(row.getScheduleStrategy());
        vo.setPlannedPublishAt(row.getPlannedPublishAt());
        vo.setPlatformScheduledAt(row.getPlatformScheduledAt());
        vo.setScheduleDriftSeconds(row.getScheduleDriftSeconds());
        vo.setScheduleDriftReason(row.getScheduleDriftReason());
        vo.setStatus(row.getStatus());
        vo.setQueueKind(row.getQueueKind());
        vo.setQueuePriority(row.getQueuePriority());
        vo.setPlatformScheduleId(row.getPlatformScheduleId());
        vo.setPlatformPublishId(row.getPlatformPublishId());
        vo.setPlatformPublishedUrl(row.getPlatformPublishedUrl());
        vo.setPublishCheckTitle(row.getPublishCheckTitle());
        vo.setPublishCheckCoverUrl(row.getPublishCheckCoverUrl());
        vo.setPublishCheckLocationName(row.getPublishCheckLocationName());
        vo.setPublishCheckFingerprint(row.getPublishCheckFingerprint());
        vo.setBaseIdempotencyKey(row.getBaseIdempotencyKey());
        vo.setGenerationNo(row.getGenerationNo());
        vo.setAttemptCount(row.getAttemptCount());
        vo.setMaxAttempts(row.getMaxAttempts());
        vo.setLastAttemptAt(row.getLastAttemptAt());
        vo.setNextAttemptAt(row.getNextAttemptAt());
        vo.setLockedUntil(row.getLockedUntil());
        vo.setFailureCode(row.getFailureCode());
        vo.setFailureMessage(row.getFailureMessage());
        vo.setDiagnosticsJson(row.getDiagnosticsJson());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setScheduledAt(row.getScheduledAt());
        vo.setCancelledAt(row.getCancelledAt());
        vo.setCancelRequestedAt(row.getCancelRequestedAt());
        vo.setPublishedConfirmedAt(row.getPublishedConfirmedAt());
        return vo;
    }
}
