package com.huanjing.geo.common.llm;

public interface LlmInvoker {

    LlmInvokeResult invoke(String prompt, LlmModelConfig modelConfig) throws LlmInvokeException;
}
