package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;

public record ModelDiagnosticTransitionResult(AiModelDiagnosticRun run,
                                              boolean transitionedByCaller) {
}
