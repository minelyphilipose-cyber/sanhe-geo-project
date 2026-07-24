package com.huanjing.geo.module.project.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class BaselineSnapshotCreateRequest {
    private Long sourcePollBatchId;
    @Valid
    private List<BaselineSnapshotIntentOverrideRequest> intentOverrides;
    @Valid
    private List<BaselineSnapshotCompetitorSourceRequest> competitorSources;
}
