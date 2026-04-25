package com.huanjing.geo.module.presale.export.render;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PresalePdfRenderResult {
    long elapsedMs;
    long fileSize;
    String metricsJson;

    public PresalePdfRenderResult withMetricsJson(String newMetricsJson) {
        return PresalePdfRenderResult.builder()
                .elapsedMs(elapsedMs)
                .fileSize(fileSize)
                .metricsJson(newMetricsJson)
                .build();
    }
}
