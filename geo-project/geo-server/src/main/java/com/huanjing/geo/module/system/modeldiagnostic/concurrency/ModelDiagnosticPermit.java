package com.huanjing.geo.module.system.modeldiagnostic.concurrency;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelDiagnosticPermit implements AutoCloseable {

    private final String globalKey;
    private final String operatorKey;
    private final String ownerToken;
    private final ModelDiagnosticPermitService service;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ModelDiagnosticPermit(String globalKey,
                          String operatorKey,
                          String ownerToken,
                          ModelDiagnosticPermitService service) {
        this.globalKey = globalKey;
        this.operatorKey = operatorKey;
        this.ownerToken = ownerToken;
        this.service = service;
    }

    String globalKey() {
        return globalKey;
    }

    String operatorKey() {
        return operatorKey;
    }

    String ownerToken() {
        return ownerToken;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            service.release(this);
        }
    }
}
