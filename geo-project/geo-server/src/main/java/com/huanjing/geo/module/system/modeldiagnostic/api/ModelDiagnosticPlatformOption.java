package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;

import java.util.List;

public record ModelDiagnosticPlatformOption(Long platformConfigId,
                                            String channelCode,
                                            String platformCode,
                                            String platformName,
                                            String modelId,
                                            ModelDiagnosticModelTier modelTier,
                                            String usageScene,
                                            String integrationType,
                                            Boolean enabled,
                                            Boolean enabledForQuestionPoll,
                                            boolean credentialAvailable,
                                            List<String> supportedModes,
                                            List<String> responseModes,
                                            boolean selectable,
                                            String unavailableReason) {
    public ModelDiagnosticPlatformOption {
        supportedModes = List.copyOf(supportedModes);
        responseModes = List.copyOf(responseModes);
    }
}
