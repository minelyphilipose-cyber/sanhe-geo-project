package com.huanjing.geo.module.project.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class BaselineSnapshotReviewRequest {
    @Valid
    private List<BaselineSnapshotQuestionReviewRequest> questions;
    @Valid
    private List<BaselineSnapshotCompetitorSourceRequest> competitorSources;
}
