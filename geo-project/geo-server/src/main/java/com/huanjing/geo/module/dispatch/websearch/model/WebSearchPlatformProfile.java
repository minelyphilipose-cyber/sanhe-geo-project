package com.huanjing.geo.module.dispatch.websearch.model;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;

import java.util.Objects;

public record WebSearchPlatformProfile(Long platformConfigId,
                                       String platformCode,
                                       String channelCode,
                                       String provider,
                                       IntegrationType integrationType,
                                       String endpointUrl,
                                       String requestedModelId,
                                       String primaryCredentialRef,
                                       String endpointId,
                                       long configVersion,
                                       String providerConfigSnapshotJson,
                                       String providerConfigHash,
                                       int connectTimeoutMs,
                                       int requestTimeoutMs) {
    public WebSearchPlatformProfile {
        Objects.requireNonNull(platformConfigId, "platformConfigId");
        Objects.requireNonNull(platformCode, "platformCode");
        Objects.requireNonNull(channelCode, "channelCode");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(integrationType, "integrationType");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        Objects.requireNonNull(requestedModelId, "requestedModelId");
        Objects.requireNonNull(primaryCredentialRef, "primaryCredentialRef");
        if (!integrationType.isWebSearch()) {
            throw new IllegalArgumentException("Web-search profile requires a web-search integration type");
        }
        if (!primaryCredentialRef.matches("^env://[A-Za-z_][A-Za-z0-9_]*$")
                && !primaryCredentialRef.matches("^db://ai-platform-config/[1-9][0-9]*$")) {
            throw new IllegalArgumentException(
                    "Web-search credentials must use a supported environment or encrypted database reference");
        }
        if (connectTimeoutMs < 1 || requestTimeoutMs < 1) {
            throw new IllegalArgumentException("Web-search timeouts must be positive");
        }
    }
}
