package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class BaselineSnapshotQuestionReviewRequest {
    private Long questionSnapshotId;
    private Long keywordResultId;
    private String intentType;
    private String valueTier;
}
