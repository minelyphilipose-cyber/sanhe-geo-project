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
    private String projectName;
    private Long companyId;
    private String companyName;
    private Long partnerId;
    private String partnerName;
    private Long brandId;
    private String brandName;
    private Long applicantUserId;
    private String applicantUserName;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private Long assignedInternalOwnerId;
    private String assignedInternalOwnerName;
    private Long defaultInternalOwnerId;
    private String defaultInternalOwnerName;
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
