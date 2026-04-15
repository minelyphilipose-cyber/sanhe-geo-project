package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

@Data
public class DispatchDashboardVO {
    private Long activeProjectCount;
    private Long dueTaskCount;
    private Long runningTaskCount;
    private Long completedTaskCount;
    private Long failedTaskCount;
    private Long deadLetterPendingCount;
    private Long platformExceptionCount;
    private Long avgTaskDurationMs;
    private String rangeLabel;
}
