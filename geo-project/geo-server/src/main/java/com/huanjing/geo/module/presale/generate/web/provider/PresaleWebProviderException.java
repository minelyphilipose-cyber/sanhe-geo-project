package com.huanjing.geo.module.presale.generate.web.provider;

public class PresaleWebProviderException extends Exception {
    private final String failureCode;
    private final boolean retryable;
    private final String providerRequestId;
    private final boolean physicalCallOccurred;

    public PresaleWebProviderException(String failureCode,
                                       String message,
                                       boolean retryable,
                                       String providerRequestId,
                                       boolean physicalCallOccurred,
                                       Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
        this.retryable = retryable;
        this.providerRequestId = providerRequestId;
        this.physicalCallOccurred = physicalCallOccurred;
    }

    public String failureCode() { return failureCode; }
    public boolean retryable() { return retryable; }
    public String providerRequestId() { return providerRequestId; }
    public boolean physicalCallOccurred() { return physicalCallOccurred; }
}
