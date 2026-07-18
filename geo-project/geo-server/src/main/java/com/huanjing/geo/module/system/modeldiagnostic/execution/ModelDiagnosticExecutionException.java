package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;

public class ModelDiagnosticExecutionException extends RuntimeException {
    private final ErrorCategory category;
    private final Integer httpStatus;
    private final String sanitizedRequest;
    private final String sanitizedResponse;

    public ModelDiagnosticExecutionException(ErrorCategory category,
                                             Integer httpStatus,
                                             String message,
                                             String sanitizedRequest,
                                             String sanitizedResponse,
                                             Throwable cause) {
        super(message, cause);
        this.category = category;
        this.httpStatus = httpStatus;
        this.sanitizedRequest = sanitizedRequest;
        this.sanitizedResponse = sanitizedResponse;
    }

    public ErrorCategory category() {
        return category;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String sanitizedRequest() {
        return sanitizedRequest;
    }

    public String sanitizedResponse() {
        return sanitizedResponse;
    }
}
