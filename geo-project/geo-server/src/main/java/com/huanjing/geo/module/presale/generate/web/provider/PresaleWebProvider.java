package com.huanjing.geo.module.presale.generate.web.provider;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.presale.generate.web.ResolvedCompanionExecutionConfig;

public interface PresaleWebProvider {
    IntegrationType integrationType();

    /** Independent multi-call providers may override this when they do not use a WebSearchCodec. */
    default boolean requiresCodec() { return true; }

    PresaleWebProviderAttempt execute(ResolvedCompanionExecutionConfig config,
                                      String userPrompt) throws PresaleWebProviderException, InterruptedException;
}
