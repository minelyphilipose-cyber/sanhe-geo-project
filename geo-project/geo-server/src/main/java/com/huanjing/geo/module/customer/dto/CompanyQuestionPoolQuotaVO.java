package com.huanjing.geo.module.customer.dto;

import lombok.Data;

@Data
public class CompanyQuestionPoolQuotaVO {
    private Long companyId;
    private Long packageBindingId;
    private String packageName;
    private Boolean activeBinding;
    private Integer quotaLimit;
    private Integer usedCount;
    private Integer remainingCount;
    private Double usageRate;
}
