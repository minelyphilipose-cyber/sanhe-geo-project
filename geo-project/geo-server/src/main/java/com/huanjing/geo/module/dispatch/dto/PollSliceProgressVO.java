package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PollSliceProgressVO {
    private LocalDate batchDate;
    private String questionTier;
    private List<String> platformCodes;
    private long expectedCount;
    private long completedCount;
    private long failedCount;
    private long resourceWaitCount;
    private double actualProgress;
    private double expectedProgress;
    private double lag;
    private int windowMinutes;
    private LocalDateTime sliceStart;
    private LocalDateTime observedAt;
    private List<PollPlatformSliceProgressRow> rows;
}
