package com.huanjing.geo.module.partner.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartnerCompanyPackageBindingVO {
    private Long id;
    private Long companyId;
    private Long packagePlanId;
    private String packageType;
    private String packageName;
    private BigDecimal standardPrice;
    private Integer serviceMonths;
    private Integer coreQuestionLimit;
    private String status;
    private Integer activeFlag;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PartnerChannelQuotaVO> visibleChannelQuotas;
}
