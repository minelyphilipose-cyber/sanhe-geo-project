package com.huanjing.geo.module.presale.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReportVersionOptionVO {
    private Long versionId;
    private Integer versionNo;
    private String generationStatus;
    private String generationStatusText;
    private LocalDateTime createdAt;
    private Boolean hasPromptTrace;
    private Boolean disabled;
    private String disabledReason;
}
