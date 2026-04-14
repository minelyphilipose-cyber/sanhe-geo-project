package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProjectCreateRequest {
    @NotNull
    private Long companyId;
    private Long brandId;
    @NotBlank
    private String projectName;
    private String projectAliases;
    @NotBlank
    private String packageType;
    private BigDecimal packagePrice;
    private Integer serviceMonths;
    private String ownerType;
    private Long partnerId;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private String districtCode;
    private String districtName;
    private String deliveryMode;
    private LocalDateTime signedAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private String primaryGoal;
    private String remark;
    private List<String> selectedPlatformCodesP0;
    private List<String> selectedPlatformCodesP1;
    private List<String> selectedPlatformCodesP2;
    private String questionPoolChangeReason;
    private List<QuestionPoolItemRequest> questionPoolItems;
}
