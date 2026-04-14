package com.huanjing.geo.module.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("dispatch_task")
public class DispatchTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long projectId;
    private String platformCode;
    private String currentChannel;
    private String taskType;
    private Integer priorityLevel;
    private String status;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    private LocalDateTime dueTime;
    private String payloadJson;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime firstStartedAt;
    private LocalDateTime lastStartedAt;
    private String lastError;
    private String errorContext;
    private LocalDateTime nextRetryAt;
    private LocalDateTime timeoutAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
