package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PackagePlanUpdateRequest {
    @NotBlank
    private String packageName;
    private String audienceType;
    @NotNull
    private BigDecimal standardPrice;
    private BigDecimal partnerPoints;
    private String partnerVisibleConfigJson;
    private String internalDeliveryConfigJson;
    @NotNull
    @Min(1)
    private Integer serviceMonths;
    @NotNull
    private Integer sortOrder;
    @NotNull
    @Min(1)
    private Integer keywordGroupLimit;
    @NotNull
    @Min(0)
    private Integer keywordGroupLimitA;
    @NotNull
    @Min(0)
    private Integer keywordGroupLimitB;
    @NotNull
    @Min(0)
    private Integer keywordGroupLimitC;
    @NotBlank
    private String monthlyReportDepth;
    @NotBlank
    private String quarterlyReportDepth;
    @NotBlank
    private String consultantIntensity;
    @NotBlank
    private String competitorInsightDepth;
    @NotBlank
    private String mediaDistributionIntensity;
    @NotBlank
    private String commitmentTargetIntensity;
    @NotBlank
    private String targetMetricType;
    @NotNull
    private BigDecimal targetMetricValue;
    @NotNull
    @Min(1)
    private Integer targetWindowDays;
    private String remark;
    private List<PackageChannelQuotaConfigRequest> channelQuotaConfigs;
}
