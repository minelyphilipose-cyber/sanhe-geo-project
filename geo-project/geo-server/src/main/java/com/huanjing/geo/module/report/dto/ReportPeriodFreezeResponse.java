package com.huanjing.geo.module.report.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReportPeriodFreezeResponse {

    private Long freezeId;
    private Long projectId;
    private String reportType;
    private String periodKey;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer versionNo;
    private String status;
    private String reason;
    private Integer sourceRowCount = 0;
    private String sourceChecksum;
    private String snapshotObjectKey;
    private String objectChecksum;
    private Long objectSizeBytes;
}
