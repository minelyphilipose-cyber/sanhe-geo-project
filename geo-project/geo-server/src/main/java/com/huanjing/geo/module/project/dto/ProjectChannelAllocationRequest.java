package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectChannelAllocationRequest {
    @NotBlank
    private String channelCode;
    @Min(0)
    private Integer allocatedCount;
}
