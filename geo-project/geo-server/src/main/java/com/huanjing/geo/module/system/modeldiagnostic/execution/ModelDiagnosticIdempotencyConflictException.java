package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.common.exception.BizException;

public class ModelDiagnosticIdempotencyConflictException extends BizException {
    public ModelDiagnosticIdempotencyConflictException() {
        super(409, "clientRequestId was already used with different diagnostic parameters",
                409, null);
    }
}
