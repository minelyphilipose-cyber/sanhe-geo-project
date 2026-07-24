package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class BaselineSnapshotCompetitorSourceRequest {
    private Long competitorId;
    private String competitorName;
    private String aliasesJson;
    private String sourceType;
    private String sourceUrl;
    private String sourceNote;
    private String reviewStatus;
}
