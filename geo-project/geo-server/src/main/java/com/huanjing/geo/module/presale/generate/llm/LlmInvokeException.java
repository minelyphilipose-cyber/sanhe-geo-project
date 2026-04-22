package com.huanjing.geo.module.presale.generate.llm;

public class LlmInvokeException extends Exception {
    public LlmInvokeException(String message) {
        super(message);
    }

    public LlmInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}

