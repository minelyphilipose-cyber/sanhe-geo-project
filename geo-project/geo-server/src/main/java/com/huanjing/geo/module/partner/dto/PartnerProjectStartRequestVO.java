package com.huanjing.geo.module.partner.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartnerProjectStartRequestVO {
    private Long requestId;
    private String requestNo;
    private String status;
    private String projectDisplayStatus;
    private BigDecimal pointsRequiredSnapshot;
    private BigDecimal discountRateSnapshot;
    private List<PartnerChannelQuotaVO> partnerAllocatedQuota;
    private LocalDateTime submittedAt;
}
