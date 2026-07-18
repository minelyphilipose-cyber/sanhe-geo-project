package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ModelDiagnosticProviderRequest(ModelDiagnosticPlatformProfile platform,
                                             String systemPrompt,
                                             List<WebSearchMessage> messages,
                                             LocalDateTime deadlineAt) {
    public ModelDiagnosticProviderRequest {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        messages = List.copyOf(messages);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Diagnostic messages must not be empty");
        }
        if (messages.stream().anyMatch(message -> "system".equals(message.role()))) {
            throw new IllegalArgumentException("System messages must use the dedicated systemPrompt field");
        }
        if (!"user".equals(messages.get(messages.size() - 1).role())) {
            throw new IllegalArgumentException("The final diagnostic message must be a user message");
        }
    }
}
