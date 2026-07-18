package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.modeldiagnostic.ModelDiagnosticPermissions;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import com.huanjing.geo.module.system.modeldiagnostic.execution.ModelDiagnosticExecutionCommand;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModelDiagnosticRequestFactory {

    private static final int MAX_TOTAL_INPUT_CHARS = 30_000;

    private final CurrentUserService currentUserService;
    private final ModelDiagnosticProbeCatalog probeCatalog;

    public ModelDiagnosticExecutionCommand create(ModelDiagnosticRunRequest request) {
        currentUserService.ensurePermission(ModelDiagnosticPermissions.DIAGNOSE);
        SysUser operator = currentUserService.requireCurrentUser();
        String systemPrompt;
        String resolvedUserMessage;
        String clientUserMessage = null;
        String probeVersion = null;
        String templateVersion = null;
        ModelDiagnosticInputMode inputMode = null;

        if (request.testMode() == ModelDiagnosticTestMode.FREE_CHAT) {
            requireAbsent(request.probeCode(), "FREE_CHAT forbids probeCode");
            systemPrompt = normalize(request.systemPrompt());
            resolvedUserMessage = requireText(request.userMessage(), "userMessage is required");
        } else {
            requireAbsent(request.systemPrompt(), request.testMode() + " forbids systemPrompt");
            ModelDiagnosticProbeCatalog.ProbeDefinition probe = probeCatalog.require(
                    requireText(request.probeCode(), "probeCode is required"),
                    request.diagnosticMode(), request.testMode());
            probeVersion = probe.version();
            templateVersion = probe.templateVersion();
            inputMode = probe.inputMode();
            systemPrompt = probe.systemPrompt();
            if (inputMode == ModelDiagnosticInputMode.USER_REQUIRED) {
                clientUserMessage = requireText(request.userMessage(), "userMessage is required");
                resolvedUserMessage = clientUserMessage;
            } else {
                requireAbsent(request.userMessage(), request.testMode() + " forbids userMessage");
                resolvedUserMessage = probe.fixedUserMessage();
            }
        }
        if (length(systemPrompt) + length(resolvedUserMessage) > MAX_TOTAL_INPUT_CHARS) {
            throw badRequest("Request content exceeds 30000 characters");
        }
        try {
            return new ModelDiagnosticExecutionCommand(
                    operator.getId(), request.sessionId(), request.clientRequestId(),
                    request.platformConfigId(), request.diagnosticMode(), request.testMode(),
                    request.probeCode(), probeVersion, templateVersion, inputMode,
                    clientUserMessage, systemPrompt, resolvedUserMessage, request.modelTier());
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw badRequest(message);
        return value.trim();
    }

    private void requireAbsent(String value, String message) {
        if (value != null && !value.isBlank()) throw badRequest(message);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private BizException badRequest(String message) {
        return new BizException(400, message, 400, null);
    }
}
