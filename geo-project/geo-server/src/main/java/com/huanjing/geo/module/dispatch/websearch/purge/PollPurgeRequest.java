package com.huanjing.geo.module.dispatch.websearch.purge;

import java.util.Objects;

public record PollPurgeRequest(Long projectId,
                               Long requestedBy,
                               String reason,
                               String scopeJson) {
    public PollPurgeRequest {
        Objects.requireNonNull(requestedBy, "requestedBy");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (scopeJson == null || scopeJson.isBlank()) {
            throw new IllegalArgumentException("scopeJson is required");
        }
    }
}
