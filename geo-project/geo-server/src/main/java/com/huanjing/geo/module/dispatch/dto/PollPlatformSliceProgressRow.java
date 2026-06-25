package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

@Data
public class PollPlatformSliceProgressRow {
    private String platformCode;
    private Long expectedCount;
    private Long completedCount;
    private Long failedCount;
    private Long resourceWaitCount;
}
