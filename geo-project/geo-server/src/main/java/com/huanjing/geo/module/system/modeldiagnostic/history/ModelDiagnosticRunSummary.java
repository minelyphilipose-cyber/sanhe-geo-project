package com.huanjing.geo.module.system.modeldiagnostic.history;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelDiagnosticRunSummary {
    private Long id;
    private String sessionId;
    private Integer turnNo;
    private Long operatorId;
    private Long platformConfigId;
    private String platformCode;
    private String channelCode;
    private String platformName;
    private String requestedModelId;
    private String responseModelId;
    private String diagnosticMode;
    private String testMode;
    private String status;
    private String conclusion;
    private String errorCategory;
    private String errorCode;
    private Long durationMs;
    private Integer sourceCount;
    private Integer validSourceCount;
    private Integer citationCount;
    private Integer validCitationCount;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
