package com.huanjing.geo.module.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BaselinePrintRenderResponse {
    private Long exportId;
    private Long baselineId;
    private Long projectId;
    private Object canonical;
    private RenderProfile renderProfile;

    @Data
    @Builder
    public static class RenderProfile {
        private double deviceScaleFactor;
        private String pageFormat;
    }
}
