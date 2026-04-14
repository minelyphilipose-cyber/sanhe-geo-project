package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchAlertVO {
    private Long id;
    private String alertCode;
    private Long taskId;
    private Long projectId;
    private String projectName;
    private String severity;
    private String status;
    private String title;
    private String content;
    private Integer retryCount;
    private String contextJson;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private LocalDateTime createdAt;
}

