package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BaselinePollBatchVO {
    private Long id;
    private Long projectId;
    private String status;
    private List<String> selectedPlatformCodes;
    private List<String> selectedQuestionTiers;
    private Integer platformCount;
    private Integer questionCount;
    private Integer totalCount;
    private Integer completedCount;
    private Integer failedCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
