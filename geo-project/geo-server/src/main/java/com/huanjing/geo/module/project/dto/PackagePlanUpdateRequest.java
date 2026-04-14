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
    @NotNull
    private BigDecimal standardPrice;
    @NotNull
    @Min(1)
    private Integer serviceMonths;
    @NotNull
    private Integer sortOrder;
    @NotNull
    @Min(1)
    private Integer questionPoolSize;
    @NotNull
    @Min(1)
    private Integer coreQuestionCount;
    @NotNull
    @Min(0)
    private Integer platformP0Count;
    @NotNull
    @Min(0)
    private Integer platformP1Count;
    @NotNull
    @Min(0)
    private Integer platformP2Count;
    @NotNull
    @Min(1)
    private Integer perQuestionPlatformCalls;
    @NotNull
    @Min(1)
    private Integer perQuestionCallsP0;
    @NotNull
    @Min(1)
    private Integer perQuestionCallsP1;
    @NotNull
    @Min(1)
    private Integer perQuestionCallsP2;
    @NotNull
    private Integer biweeklyFrequency;
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
    private List<PackageContentConfigRequest> contentConfigs;
}
