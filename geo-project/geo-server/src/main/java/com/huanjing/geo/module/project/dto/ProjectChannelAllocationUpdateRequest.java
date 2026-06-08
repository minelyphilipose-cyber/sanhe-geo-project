package com.huanjing.geo.module.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ProjectChannelAllocationUpdateRequest {
    private Long allocationVersion;
    @Valid
    @NotEmpty
    private List<ProjectChannelAllocationRequest> channelAllocations;
}
