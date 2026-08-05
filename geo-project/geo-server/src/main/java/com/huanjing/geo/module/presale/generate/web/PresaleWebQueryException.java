package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.presale.generate.llm.LlmInvokeException;

public class PresaleWebQueryException extends LlmInvokeException {
    private final String failureCode;
    private final PresaleSearchEvidence partialEvidence;
    private final String evidenceJson;

    public PresaleWebQueryException(String failureCode,
                                    String message,
                                    PresaleSearchEvidence partialEvidence,
                                    String evidenceJson,
                                    Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
        this.partialEvidence = partialEvidence;
        this.evidenceJson = evidenceJson;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public PresaleSearchEvidence getPartialEvidence() {
        return partialEvidence;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}
