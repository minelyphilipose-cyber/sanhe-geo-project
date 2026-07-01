package com.huanjing.geo.module.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("project_start_request")
public class ProjectStartRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long companyId;
    private Long partnerId;
    private Long applicantUserId;
    private String status;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long activeSubmittedProjectId;
    private String requestNo;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReasonCode;
    private String rejectReasonText;
    private Long assignedInternalOwnerId;
    private BigDecimal pointsRequiredSnapshot;
    private BigDecimal discountRateSnapshot;
    private String packageSnapshotJson;
    private String partnerAllocatedQuotaJson;
    private String internalDeliverySnapshotJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
