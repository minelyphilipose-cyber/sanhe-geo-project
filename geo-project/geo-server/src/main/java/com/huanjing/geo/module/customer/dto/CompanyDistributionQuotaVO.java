package com.huanjing.geo.module.customer.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompanyDistributionQuotaVO {
    private Long companyId;
    private Boolean hasLimitMismatch;
    private List<CompanyDistributionQuotaItemVO> items;
}
