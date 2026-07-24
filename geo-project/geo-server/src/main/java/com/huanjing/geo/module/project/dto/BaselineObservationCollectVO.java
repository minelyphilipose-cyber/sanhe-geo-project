package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class BaselineObservationCollectVO {
    private Long taskId;
    private Long baselineId;
    private Long projectId;
    private String status;
    private Integer questionCount;
    private Integer platformCount;
    private Integer samplePerCell;
    private Integer totalObservationCount;
    private Integer successObservationCount;
    private Integer failedObservationCount;
    private Integer scoreCount;
    private Integer competitorMentionCount;
    private Integer queuePosition;
    private Integer maxConcurrentBaselines;
    private String errorMessage;
}
