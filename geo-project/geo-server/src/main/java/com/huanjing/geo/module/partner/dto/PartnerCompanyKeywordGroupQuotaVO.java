package com.huanjing.geo.module.partner.dto;

import lombok.Data;

@Data
public class PartnerCompanyKeywordGroupQuotaVO {
    private Long companyId;
    private Long packageBindingId;
    private String packageName;
    private Boolean activeBinding;
    private Integer coreQuestionQuotaLimit;
    private Integer usedCoreQuestionCount;
    private Integer remainingCoreQuestionCount;
    private Double usageRate;
}
