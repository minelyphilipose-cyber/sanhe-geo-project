package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class ProjectKeywordGroupQuotaVO {
    private Long companyId;
    private Long excludeProjectId;
    private Integer quotaLimit;
    private Integer quotaLimitA;
    private Integer quotaLimitB;
    private Integer quotaLimitC;
    private Integer activeAllocatedCount;
    private Integer activeAllocatedCountA;
    private Integer activeAllocatedCountB;
    private Integer activeAllocatedCountC;
    private Integer currentProjectAllocatedCount;
    private Integer currentProjectAllocatedCountA;
    private Integer currentProjectAllocatedCountB;
    private Integer currentProjectAllocatedCountC;
    private Integer remainingCount;
    private Integer remainingCountA;
    private Integer remainingCountB;
    private Integer remainingCountC;
    private Integer inputMaxA;
    private Integer inputMaxB;
    private Integer inputMaxC;
}
