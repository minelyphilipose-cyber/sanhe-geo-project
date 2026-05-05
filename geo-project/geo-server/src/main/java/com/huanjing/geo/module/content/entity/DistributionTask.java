package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Distribution task entity.
 *
 * <p>V93 (Phase 0) extended this table with multi-target columns
 * (target_kind, brand_official_site_id, self_media_account_id, locked_until, etc.)
 * The corresponding Java fields were missing from this entity due to a
 * Phase 0 commit oversight (the modified file was not actually changed at
 * git level due to LF/CRLF normalization). P1.4-b0 supplements them
 * before P1.4-b state machine implementation.
 */
@Data
@TableName("distribution_tasks")
public class DistributionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long projectId;
    private Long siteId;
    // V93: multi-target discriminator + per-channel FK columns
    private String targetKind;
    private Long selfMediaAccountId;
    private Long brandOfficialSiteId;
    private Long targetBrandId;
    private Long industrySiteId;
    private Long authorityMediaId;
    // existing columns continue
    private Integer attemptNo;
    private String status;
    private String integrationMethod;
    private String requestPayload;
    private String responsePayload;
    private String publishedUrl;
    // V93: post-publish identifier returned by external platform
    private String platformArticleId;
    private String externalStatus;
    private String reviewStatus;
    private String reviewFeedback;
    private String errorMessage;
    // V93: failure classification (FailureKind constants)
    private String failureKind;
    // V93: retry & locking for Outbox state machine (IC-4)
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedUntil;
    private Integer retryCount;
    private Long operatorId;
    private String requestId;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
