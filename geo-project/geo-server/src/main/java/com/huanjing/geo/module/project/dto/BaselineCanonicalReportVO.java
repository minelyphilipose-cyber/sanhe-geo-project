package com.huanjing.geo.module.project.dto;

import lombok.Data;

@Data
public class BaselineCanonicalReportVO {
    private Long baselineId;
    private String canonicalSchemaVersion;
    private String scoreAlgorithmVersion;
    private String highlightAlgorithmVersion;
    private String competitorNormalizationVersion;
    private String canonicalAggregateVersion;
    private String canonicalJson;
}
