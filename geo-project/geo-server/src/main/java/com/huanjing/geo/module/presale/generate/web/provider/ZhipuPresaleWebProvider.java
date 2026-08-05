package com.huanjing.geo.module.presale.generate.web.provider;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.presale.generate.web.ResolvedCompanionExecutionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ZhipuPresaleWebProvider implements PresaleWebProvider {
    private final CodecBackedProviderSupport support;

    @Override
    public IntegrationType integrationType() {
        return IntegrationType.ZHIPU_CHAT_WEB;
    }

    @Override
    public PresaleWebProviderAttempt execute(ResolvedCompanionExecutionConfig config,
                                             String prompt)
            throws PresaleWebProviderException, InterruptedException {
        return support.execute(config, prompt);
    }
}
