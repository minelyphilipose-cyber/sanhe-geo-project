package com.huanjing.geo.module.presale.export.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresalePrintRenderResponse {
    private Long exportId;
    private Long reportId;
    private Long versionId;
    private Object snapshot;
    private RenderProfile renderProfile;

    @Data
    @Builder
    public static class RenderProfile {
        private double deviceScaleFactor;
        private String pageFormat;
        private int expectedPages;
    }
}