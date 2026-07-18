package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;

import java.util.Objects;
import java.util.UUID;

public record ModelDiagnosticExecutionCommand(Long operatorId,
                                              String sessionId,
                                              String clientRequestId,
                                              Long platformConfigId,
                                              ModelDiagnosticMode diagnosticMode,
                                              ModelDiagnosticTestMode testMode,
                                              String probeCode,
                                              String probeVersion,
                                              String templateVersion,
                                              ModelDiagnosticInputMode inputMode,
                                              String clientUserMessage,
                                              String systemPrompt,
                                              String resolvedUserMessage,
                                              ModelDiagnosticModelTier modelTier) {
    public ModelDiagnosticExecutionCommand {
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(platformConfigId, "platformConfigId");
        Objects.requireNonNull(diagnosticMode, "diagnosticMode");
        Objects.requireNonNull(testMode, "testMode");
        modelTier = modelTier == null ? ModelDiagnosticModelTier.PRIMARY : modelTier;
        requireUuid(sessionId, "sessionId");
        requireUuid(clientRequestId, "clientRequestId");
        if (operatorId < 1 || platformConfigId < 1) {
            throw new IllegalArgumentException("operatorId and platformConfigId must be positive");
        }
        if (resolvedUserMessage == null || resolvedUserMessage.isBlank()) {
            throw new IllegalArgumentException("resolvedUserMessage must not be blank");
        }
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        clientUserMessage = hasText(clientUserMessage) ? clientUserMessage.trim() : null;
        resolvedUserMessage = resolvedUserMessage.trim();
        validateModeFields(testMode, probeCode, probeVersion, templateVersion,
                inputMode, clientUserMessage);
    }

    public ModelDiagnosticExecutionCommand(Long operatorId,
                                           String sessionId,
                                           String clientRequestId,
                                           Long platformConfigId,
                                           ModelDiagnosticMode diagnosticMode,
                                           ModelDiagnosticTestMode testMode,
                                           String probeCode,
                                           String probeVersion,
                                           String templateVersion,
                                           ModelDiagnosticInputMode inputMode,
                                           String clientUserMessage,
                                           String systemPrompt,
                                           String resolvedUserMessage) {
        this(operatorId, sessionId, clientRequestId, platformConfigId, diagnosticMode,
                testMode, probeCode, probeVersion, templateVersion, inputMode,
                clientUserMessage, systemPrompt, resolvedUserMessage,
                ModelDiagnosticModelTier.PRIMARY);
    }

    private static void validateModeFields(ModelDiagnosticTestMode testMode,
                                           String probeCode,
                                           String probeVersion,
                                           String templateVersion,
                                           ModelDiagnosticInputMode inputMode,
                                           String clientUserMessage) {
        if (testMode == ModelDiagnosticTestMode.FREE_CHAT) {
            if (hasText(probeCode) || hasText(probeVersion) || hasText(templateVersion)
                    || inputMode != null || hasText(clientUserMessage)) {
                throw new IllegalArgumentException(
                        "FREE_CHAT forbids probe, template and production input fields");
            }
            return;
        }
        if (!hasText(probeCode) || !hasText(probeVersion)) {
            throw new IllegalArgumentException(testMode + " requires resolved probeCode and probeVersion");
        }
        if (testMode == ModelDiagnosticTestMode.STANDARD_PROBE && hasText(templateVersion)) {
            throw new IllegalArgumentException("STANDARD_PROBE forbids templateVersion");
        }
        if (testMode == ModelDiagnosticTestMode.STANDARD_PROBE
                && (inputMode != null || hasText(clientUserMessage))) {
            throw new IllegalArgumentException("STANDARD_PROBE forbids production input fields");
        }
        if (testMode == ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE) {
            if (!hasText(templateVersion) || inputMode == null) {
                throw new IllegalArgumentException(
                        "PRODUCTION_POLL_TEMPLATE requires templateVersion and inputMode");
            }
            if (inputMode == ModelDiagnosticInputMode.FIXED && hasText(clientUserMessage)) {
                throw new IllegalArgumentException("FIXED production template forbids clientUserMessage");
            }
            if (inputMode == ModelDiagnosticInputMode.USER_REQUIRED
                    && !hasText(clientUserMessage)) {
                throw new IllegalArgumentException(
                        "USER_REQUIRED production template requires clientUserMessage");
            }
        }
    }

    private static void requireUuid(String value, String field) {
        try {
            if (value == null || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(field + " must be a canonical UUID");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
