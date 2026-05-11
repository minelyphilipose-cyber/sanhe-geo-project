package com.huanjing.geo.module.customer.dto;

import lombok.Data;

@Data
public class CompanyKeywordGroupQuotaVO {
    private Long companyId;
    private Long packageBindingId;
    private String packageName;
    private Boolean activeBinding;
    private Integer quotaLimit;
    private Integer quotaLimitA;
    private Integer quotaLimitB;
    private Integer quotaLimitC;
    private Integer usedCount;
    private Integer usedCountA;
    private Integer usedCountB;
    private Integer usedCountC;
    private Integer remainingCount;
    private Integer remainingCountA;
    private Integer remainingCountB;
    private Integer remainingCountC;
    private Double usageRate;
}
