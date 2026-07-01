package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminProjectStartRequestVO {
    private Long id;
    private String requestNo;
    private String status;
    private Long projectId;
    private String projectStatus;
    private String projectDisplayStatus;
    private Long companyId;
    private Long partnerId;
    private Long applicantUserId;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long assignedInternalOwnerId;
    private BigDecimal pointsRequiredSnapshot;
    private BigDecimal discountRateSnapshot;
    private String packageSnapshotJson;
    private String partnerAllocatedQuotaJson;
    private String internalDeliverySnapshotJson;
    private String rejectReasonCode;
    private String rejectReasonText;
    private String quotaSnapshotStatus;
    private LocalDateTime quotaLockedAt;
    private LocalDateTime quotaReleasedAt;
    private Long pointsTxnId;
    private String pointsTxnNo;
    private BigDecimal pointsTxnAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
