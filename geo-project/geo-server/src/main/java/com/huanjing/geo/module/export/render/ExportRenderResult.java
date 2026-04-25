package com.huanjing.geo.module.export.render;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExportRenderResult {
    long elapsedMs;
    long fileSize;
    String metricsJson;

    public ExportRenderResult withMetricsJson(String newMetricsJson) {
        return ExportRenderResult.builder()
                .elapsedMs(elapsedMs)
                .fileSize(fileSize)
                .metricsJson(newMetricsJson)
                .build();
    }
}
