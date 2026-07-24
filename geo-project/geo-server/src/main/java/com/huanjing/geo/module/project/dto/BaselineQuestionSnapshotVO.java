package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineQuestionSnapshotVO {
    private Long id;
    private String questionKey;
    private Long sourceKeywordResultId;
    private String questionText;
    private String valueTier;
    private String sourceQuestionTier;
    private String sourcePriority;
    private String intentType;
    private String sceneCode;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
