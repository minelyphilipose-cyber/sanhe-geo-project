package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;

import java.util.List;

public record ModelDiagnosticPreparedExecution(AiModelDiagnosticRun run,
                                               List<WebSearchMessage> messages,
                                               boolean executable,
                                               boolean transitionedByCaller) {
    public ModelDiagnosticPreparedExecution {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public ModelDiagnosticPreparedExecution(AiModelDiagnosticRun run,
                                            List<WebSearchMessage> messages,
                                            boolean executable) {
        this(run, messages, executable, false);
    }
}
