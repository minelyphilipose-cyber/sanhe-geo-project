package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;

/**
 * Non-secret, immutable companion snapshot used by one report generation run.
 * The credential value is intentionally absent and is resolved from credentialRef for each physical call.
 */
public record ResolvedCompanionExecutionConfig(
        String reportPlatformCode,
        String reportPlatformName,
        Long companionConfigId,
        String companionPlatformCode,
        String companionPlatformName,
        Long companionConfigVersion,
        String channelCode,
        String provider,
        IntegrationType integrationType,
        String endpointUrl,
        String modelId,
        String modelName,
        String credentialRef,
        String providerConfigJson,
        int connectTimeoutMs,
        int requestTimeoutMs,
        int concurrencyLimit,
        int rpmLimit,
        int tpmLimit) {
}
