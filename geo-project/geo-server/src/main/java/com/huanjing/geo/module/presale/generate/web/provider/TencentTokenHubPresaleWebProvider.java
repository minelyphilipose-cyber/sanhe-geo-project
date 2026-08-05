package com.huanjing.geo.module.presale.generate.web.provider;

import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.presale.generate.web.ResolvedCompanionExecutionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TencentTokenHubPresaleWebProvider implements PresaleWebProvider {
    private final CodecBackedProviderSupport support;
    public IntegrationType integrationType() { return IntegrationType.TENCENT_TOKENHUB_RESPONSES_WEB; }
    public PresaleWebProviderAttempt execute(ResolvedCompanionExecutionConfig c, String p) throws PresaleWebProviderException, InterruptedException { return support.execute(c, p); }
}
