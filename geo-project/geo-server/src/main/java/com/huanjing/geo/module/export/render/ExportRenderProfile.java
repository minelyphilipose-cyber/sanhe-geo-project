package com.huanjing.geo.module.export.render;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExportRenderProfile {
    String pageFormat;
    double deviceScaleFactor;
    int viewportWidth;
    int viewportHeight;
    int pageLoadTimeoutMs;
    int readyTimeoutMs;
    int pdfTimeoutMs;
    int acquireTimeoutMs;
}
