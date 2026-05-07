package com.huanjing.geo.module.customer.dto;

import lombok.Data;

@Data
public class CompanyDistributionQuotaItemVO {
    private String channelCode;
    private String channelName;
    private Boolean enabled;
    private String periodType;
    private String periodKey;
    private Integer quotaLimit;
    private Integer usageQuotaLimit;
    private Boolean limitMismatch;
    private Integer usedCount;
    private Integer remainingCount;
    private Double usageRate;
    private String nextResetAt;
    private String status;
}
