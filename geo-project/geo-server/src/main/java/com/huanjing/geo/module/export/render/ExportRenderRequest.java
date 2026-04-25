package com.huanjing.geo.module.export.render;

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
public class ExportRenderRequest {
    Long exportId;
    String renderUrl;
    Path outputPath;
    Path debugDir;
    ExportRenderProfile profile;
}
