package com.huanjing.geo.module.presale.export.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
public class PresaleExportResponse {
    private Long exportId;
    private Long reportId;
    private Long versionId;
    private String status;
    private String idempotencyKey;
    private String errorMsg;
    private Integer retryCount;
    private String fileKey;
    private Long fileSize;
    private Integer filePages;
    private LocalDateTime expireAt;
    private Long runningExportId;
    private String runningStatus;
}
