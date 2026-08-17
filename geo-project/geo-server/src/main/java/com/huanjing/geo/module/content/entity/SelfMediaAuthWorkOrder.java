package com.huanjing.geo.module.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("self_media_auth_work_order")
public class SelfMediaAuthWorkOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long browserEnvironmentId;
    private String status;
    private String priority;
    private Long ownerUserId;
    private Boolean customerActionRequired;
    private String issueSummary;
    private String latestNote;
    private LocalDateTime nextFollowUpAt;
    private LocalDateTime resolvedAt;
    private String resolutionMode;
    private String resolutionReason;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
