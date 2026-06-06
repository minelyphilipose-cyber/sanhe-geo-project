package com.huanjing.geo.module.presale.persist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("presale_industry_bucket_review_task")
public class PresaleIndustryBucketReviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industry;
    private String industryKey;
    private String draftJson;
    private String status;
    private String source;
    private String draftSource;
    private String rejectReason;
    private Integer fallbackHitCount;
    private Long draftedBy;
    private LocalDateTime draftedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long rejectedBy;
    private LocalDateTime rejectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
