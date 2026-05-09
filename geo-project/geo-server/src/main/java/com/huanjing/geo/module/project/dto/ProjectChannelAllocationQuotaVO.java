package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProjectChannelAllocationQuotaVO {
    private Long companyId;
    private Long excludeProjectId;
    private Long allocationVersion;
    private String note;
    private List<ProjectChannelAllocationVO> items;
}
