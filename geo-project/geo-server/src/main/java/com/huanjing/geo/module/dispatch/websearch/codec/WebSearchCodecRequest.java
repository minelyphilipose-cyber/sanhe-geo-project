package com.huanjing.geo.module.dispatch.websearch.codec;

import java.util.List;
import java.util.Objects;

/**
 * Side-effect-free protocol input shared by production polling and future diagnostics.
 */
public record WebSearchCodecRequest(String requestedModelId,
                                    List<WebSearchMessage> messages,
                                    String providerConfigSnapshotJson) {

    public WebSearchCodecRequest {
        Objects.requireNonNull(requestedModelId, "requestedModelId");
        Objects.requireNonNull(messages, "messages");
        if (requestedModelId.isBlank()) {
            throw new IllegalArgumentException("requestedModelId must not be blank");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        if (messages.stream().noneMatch(message -> "user".equals(message.role()))) {
            throw new IllegalArgumentException("messages must contain at least one user message");
        }
        providerConfigSnapshotJson = providerConfigSnapshotJson == null
                || providerConfigSnapshotJson.isBlank() ? "{}" : providerConfigSnapshotJson;
    }
}
