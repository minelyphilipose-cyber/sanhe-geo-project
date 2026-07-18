package com.huanjing.geo.module.dispatch.websearch.codec;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Provider-neutral text message consumed by web-search protocol codecs.
 */
public record WebSearchMessage(String role, String content) {

    private static final Set<String> SUPPORTED_ROLES = Set.of("system", "user", "assistant");

    public WebSearchMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        role = role.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Unsupported web-search message role: " + role);
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("Web-search message content must not be blank");
        }
    }
}
