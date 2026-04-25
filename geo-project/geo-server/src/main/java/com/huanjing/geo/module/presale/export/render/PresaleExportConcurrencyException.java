package com.huanjing.geo.module.presale.export.render;

public class PresaleExportConcurrencyException extends RuntimeException {
    public PresaleExportConcurrencyException(String message) {
        super(message);
    }

    public PresaleExportConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
