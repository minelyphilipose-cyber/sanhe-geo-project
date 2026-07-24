package com.huanjing.geo.module.project.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BaselineReportExportResponse {
    private Long exportId;
    private Long baselineId;
    private Long projectId;
    private String status;
    private String idempotencyKey;
    private String errorMsg;
    private String fileKey;
    private Long fileSize;
    private Integer filePages;
    private LocalDateTime triggerAt;
    private Long runningExportId;
    private String runningStatus;
}
