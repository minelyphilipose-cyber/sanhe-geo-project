package com.huanjing.geo.common.llm;

public class LlmInvokeException extends Exception {
    public LlmInvokeException(String message) {
        super(message);
    }

    public LlmInvokeException(String message, Throwable cause) {
        super(message, cause);
    }
}
