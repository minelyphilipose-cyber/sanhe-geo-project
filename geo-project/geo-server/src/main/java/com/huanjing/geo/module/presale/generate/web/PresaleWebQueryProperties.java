package com.huanjing.geo.module.presale.generate.web;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "presale.query-web")
public class PresaleWebQueryProperties {
    /** OFF is deliberately the safe deployment default. */
    private PresaleQueryWebMode mode = PresaleQueryWebMode.OFF;
    /** Total business attempts. Two means first attempt plus one retry. */
    private int maxAttempts = 2;
    private int connectTimeoutMs = 10_000;
    private int requestTimeoutMs = 120_000;
    private int maxEvidenceBytes = 64 * 1024;
    private int maxEvidenceItems = 20;
    private int maxTextLength = 2_000;
}
