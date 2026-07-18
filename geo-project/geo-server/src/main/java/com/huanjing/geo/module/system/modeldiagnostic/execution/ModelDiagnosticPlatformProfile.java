package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;

import java.util.Objects;

public record ModelDiagnosticPlatformProfile(Long platformConfigId,
                                             String platformCode,
                                             String channelCode,
                                             String platformName,
                                             String usageScene,
                                             IntegrationType integrationType,
                                             String endpointUrl,
                                             String requestedModelId,
                                             long configVersion,
                                             String providerConfigSnapshotJson,
                                             String configSnapshotJson,
                                             String configSnapshotHash,
                                             String primaryCredentialRef,
                                             String encryptedApiKey,
                                             int connectTimeoutMs,
                                             int requestTimeoutMs) {
    public ModelDiagnosticPlatformProfile {
        Objects.requireNonNull(platformConfigId, "platformConfigId");
        Objects.requireNonNull(platformCode, "platformCode");
        Objects.requireNonNull(channelCode, "channelCode");
        Objects.requireNonNull(platformName, "platformName");
        Objects.requireNonNull(usageScene, "usageScene");
        Objects.requireNonNull(integrationType, "integrationType");
        Objects.requireNonNull(endpointUrl, "endpointUrl");
        Objects.requireNonNull(requestedModelId, "requestedModelId");
        Objects.requireNonNull(providerConfigSnapshotJson, "providerConfigSnapshotJson");
        Objects.requireNonNull(configSnapshotJson, "configSnapshotJson");
        Objects.requireNonNull(configSnapshotHash, "configSnapshotHash");
        if (connectTimeoutMs < 1 || requestTimeoutMs < 1) {
            throw new IllegalArgumentException("Diagnostic timeouts must be positive");
        }
    }

    @Override
    public String toString() {
        return "ModelDiagnosticPlatformProfile[platformConfigId=" + platformConfigId
                + ", platformCode=" + platformCode
                + ", integrationType=" + integrationType
                + ", requestedModelId=" + requestedModelId + "]";
    }
}
