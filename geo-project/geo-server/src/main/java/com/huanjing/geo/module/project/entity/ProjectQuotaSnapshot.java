package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_quota_snapshot")
public class ProjectQuotaSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long startRequestId;
    private String status;
    private String partnerAllocatedQuotaJson;
    private String internalDeliverySnapshotJson;
    private LocalDateTime lockedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
