package com.huanjing.geo.module.export.render;

public interface ExportRenderKernel {
    ExportRenderResult render(ExportRenderRequest request) throws Exception;
}
