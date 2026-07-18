package com.huanjing.geo.module.dispatch.websearch.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record WebSearchRequest(Long attemptId,
                               Long pollResultId,
                               String originalQuestion,
                               String systemPrompt,
                               WebSearchPlatformProfile profile,
                               LocalDateTime attemptDeadlineAt) {
    public WebSearchRequest {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(pollResultId, "pollResultId");
        Objects.requireNonNull(originalQuestion, "originalQuestion");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(attemptDeadlineAt, "attemptDeadlineAt");
        if (originalQuestion.isBlank()) {
            throw new IllegalArgumentException("originalQuestion must not be blank");
        }
    }
}
