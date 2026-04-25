package com.huanjing.geo.module.export.render;

public class ExportRenderConcurrencyException extends RuntimeException {
    public ExportRenderConcurrencyException(String message) {
        super(message);
    }

    public ExportRenderConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
