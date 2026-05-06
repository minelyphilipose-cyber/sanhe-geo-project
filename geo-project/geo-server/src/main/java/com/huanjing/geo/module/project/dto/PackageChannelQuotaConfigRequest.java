package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PackageChannelQuotaConfigRequest {
    @NotBlank
    private String channelCode;
    @NotBlank
    private String periodType;
    @NotNull
    @Min(0)
    private Integer quotaLimit;
    @NotNull
    private Boolean enabled;
}
