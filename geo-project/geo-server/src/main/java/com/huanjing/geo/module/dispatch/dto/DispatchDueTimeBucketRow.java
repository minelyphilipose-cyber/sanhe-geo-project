package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchDueTimeBucketRow {
    private String platformCode;
    private String status;
    private LocalDateTime bucketStart;
    private Long taskCount;
}
