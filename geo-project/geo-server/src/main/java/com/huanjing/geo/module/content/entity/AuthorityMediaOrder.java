package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Meititejia purchase order mapped from one distribution task.
 *
 * <p>One distribution_task maps to exactly one order row. Retries for the same
 * logical submission reuse this row and its externalNo. If the remote side
 * rejects or deletes the draft and the user edits/resubmits, create a new
 * distribution_task and a new order row.</p>
 */
@Data
@TableName("authority_media_order")
public class AuthorityMediaOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long distributionTaskId;
    private Long articleId;
    private Long projectId;
    private Long resourceId;
    private String resourceType;
    private String externalNo;
    private String submitStatus;
    private Integer remoteStatus;
    private String remoteStatusText;
    private String publishedUrl;
    private String rejectReason;
    private LocalDateTime remotePublishedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime nextCheckAt;
    private String requestPayload;
    private String responsePayload;
    private String extraPayload;
    private Integer lockVersion;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
