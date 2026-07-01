package com.huanjing.geo.module.partner.dto;

import lombok.Data;

@Data
public class PartnerChannelQuotaVO {
    private String channelCode;
    private String channelName;
    private String periodType;
    private Boolean enabled;
    private Integer quotaLimit;
    private Long activeAllocatedCount;
    private Integer currentProjectAllocatedCount;
    private Long remainingCount;
    private Long inputMax;
}
