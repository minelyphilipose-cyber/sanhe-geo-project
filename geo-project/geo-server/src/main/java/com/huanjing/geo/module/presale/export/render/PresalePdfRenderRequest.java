package com.huanjing.geo.module.presale.export.render;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
public class PresalePdfRenderRequest {
    Long exportId;
    String renderUrl;
    Path pdfPath;
    Path debugDir;
}
