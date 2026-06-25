package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.llm.capacity.LlmCapacityFailure;

public class DispatchResourceBusyException extends RuntimeException {
    private final LlmCapacityFailure capacityFailure;

    public DispatchResourceBusyException(String message, Throwable cause) {
        super(message, cause);
        this.capacityFailure = null;
    }

    public DispatchResourceBusyException(String message, Throwable cause, LlmCapacityFailure capacityFailure) {
        super(message, cause);
        this.capacityFailure = capacityFailure;
    }

    public LlmCapacityFailure getCapacityFailure() {
        return capacityFailure;
    }
}
