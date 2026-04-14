package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DispatchTaskMonitorVO {
    private Long id;
    private String taskNo;
    private Long projectId;
    private String projectName;
    private String platformCode;
    private String currentChannel;
    private String taskType;
    private Integer priorityLevel;
    private String status;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    private LocalDateTime dueTime;
    private Integer retryCount;
    private LocalDateTime finishedAt;
    private String lastError;
    private String errorContext;
    private LocalDateTime createdAt;
}

