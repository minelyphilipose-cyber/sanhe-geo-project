package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
    private List<String> targetRegions;
    private String targetAudience;
    private String customStatement;
    private String contentTone;
    private List<String> preferredAngles;
    private List<String> extraForbiddenPhrases;
    private String contentNote;
    private String remark;
    @NotEmpty
    @Size(max = 10)
    private List<Long> keywordGroupIds;
    private Long allocationVersion;
    private List<ProjectChannelAllocationRequest> channelAllocations;
}
