package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ModelDiagnosticRunRequest(
        @NotBlank String sessionId,
        @NotBlank String clientRequestId,
        @NotNull @Positive Long platformConfigId,
        @JsonProperty("mode") @NotNull ModelDiagnosticMode diagnosticMode,
        @NotNull ModelDiagnosticTestMode testMode,
        @Size(max = 64) String probeCode,
        @Size(max = 8_000) String systemPrompt,
        @Size(max = 8_000) String userMessage,
        ModelDiagnosticModelTier modelTier) {

    public ModelDiagnosticRunRequest(String sessionId,
                                     String clientRequestId,
                                     Long platformConfigId,
                                     ModelDiagnosticMode diagnosticMode,
                                     ModelDiagnosticTestMode testMode,
                                     String probeCode,
                                     String systemPrompt,
                                     String userMessage) {
        this(sessionId, clientRequestId, platformConfigId, diagnosticMode, testMode,
                probeCode, systemPrompt, userMessage, ModelDiagnosticModelTier.PRIMARY);
    }
}
