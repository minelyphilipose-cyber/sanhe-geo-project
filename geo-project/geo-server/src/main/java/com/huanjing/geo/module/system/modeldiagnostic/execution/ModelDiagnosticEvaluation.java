package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticCapabilityStatus;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticConclusion;

public record ModelDiagnosticEvaluation(ModelDiagnosticConclusion conclusion,
                                        String reason,
                                        ModelDiagnosticCapabilityStatus authentication,
                                        ModelDiagnosticCapabilityStatus generation,
                                        ModelDiagnosticCapabilityStatus webSearch,
                                        ModelDiagnosticCapabilityStatus sourceParsing,
                                        ModelDiagnosticCapabilityStatus citationParsing,
                                        Integer promptTokens,
                                        Integer completionTokens,
                                        Integer totalTokens,
                                        Integer webSearchCallCount,
                                        int sourceCount,
                                        int validSourceCount,
                                        int citationCount,
                                        int validCitationCount) {
}
