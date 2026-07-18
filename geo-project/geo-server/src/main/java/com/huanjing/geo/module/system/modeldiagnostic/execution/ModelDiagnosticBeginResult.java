package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;

import java.util.List;

public record ModelDiagnosticBeginResult(AiModelDiagnosticRun run,
                                         List<WebSearchMessage> messages,
                                         boolean reused) {
    public ModelDiagnosticBeginResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
