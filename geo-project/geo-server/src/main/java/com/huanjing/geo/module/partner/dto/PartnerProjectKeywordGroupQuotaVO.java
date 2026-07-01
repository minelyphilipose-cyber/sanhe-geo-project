package com.huanjing.geo.module.partner.dto;

import lombok.Data;

@Data
public class PartnerProjectKeywordGroupQuotaVO {
    private Long companyId;
    private Long excludeProjectId;
    private Integer coreQuestionQuotaLimit;
    private Integer activeAllocatedCoreQuestionCount;
    private Integer currentProjectAllocatedCoreQuestionCount;
    private Integer remainingCoreQuestionCount;
    private Integer inputMaxCoreQuestionCount;
}
