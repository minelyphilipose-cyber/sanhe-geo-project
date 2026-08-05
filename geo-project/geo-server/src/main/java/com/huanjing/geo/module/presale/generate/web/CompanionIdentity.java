package com.huanjing.geo.module.presale.generate.web;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;

public record CompanionIdentity(Long configId,
                                Long configVersion,
                                IntegrationType integrationType,
                                String modelId) {
    public static CompanionIdentity from(ResolvedCompanionExecutionConfig config) {
        return new CompanionIdentity(config.companionConfigId(), config.companionConfigVersion(),
                config.integrationType(), config.modelId());
    }
}
