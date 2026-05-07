package com.huanjing.geo.module.content.credential.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CredentialAuditConfiguration {

    @Bean
    @ConditionalOnMissingBean(CredentialAuditHook.class)
    public CredentialAuditHook noopCredentialAuditHook() {
        return new NoopCredentialAuditHook();
    }
}
