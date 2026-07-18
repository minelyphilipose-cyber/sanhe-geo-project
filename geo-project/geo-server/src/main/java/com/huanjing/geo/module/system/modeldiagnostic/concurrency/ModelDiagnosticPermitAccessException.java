package com.huanjing.geo.module.system.modeldiagnostic.concurrency;

public class ModelDiagnosticPermitAccessException extends RuntimeException {

    private final String rejectionCode;

    public ModelDiagnosticPermitAccessException(String rejectionCode,
                                                String message,
                                                Throwable cause) {
        super(message, cause);
        this.rejectionCode = rejectionCode;
    }

    public String rejectionCode() {
        return rejectionCode;
    }
}
