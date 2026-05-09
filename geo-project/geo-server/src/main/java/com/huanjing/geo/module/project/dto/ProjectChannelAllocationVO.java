package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class ProjectChannelAllocationVO {
    private String channelCode;
    private String channelName;
    private String periodType;
    private boolean enabled;
    private int quotaLimit;
    private long activeAllocatedCount;
    private int currentProjectAllocatedCount;
    private long remainingCount;
    private long inputMax;
}
