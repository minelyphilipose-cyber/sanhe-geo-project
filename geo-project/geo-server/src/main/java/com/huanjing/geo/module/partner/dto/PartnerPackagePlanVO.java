package com.huanjing.geo.module.partner.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PartnerPackagePlanVO {
    private Long id;
    private String packageType;
    private String packageName;
    private BigDecimal standardPrice;
    private Integer serviceMonths;
    private Integer coreQuestionLimit;
    private String monthlyReportDepth;
    private String quarterlyReportDepth;
    private String consultantIntensity;
    private String competitorInsightDepth;
    private String mediaDistributionIntensity;
    private String commitmentTargetIntensity;
    private String targetMetricType;
    private BigDecimal targetMetricValue;
    private Integer targetWindowDays;
    private Boolean enabled;
    private Integer sortOrder;
    private List<PartnerChannelQuotaVO> channelQuotaConfigs;
}
