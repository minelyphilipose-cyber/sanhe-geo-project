package com.huanjing.geo.module.presale.generate.llm;

import lombok.Data;

@Data
public class PresaleLlmPlatformConfigRow {
    private String platformCode;
    private String apiUrl;
    private String modelId;
    private String apiKey;
    private String primaryKeyRef;
    private Integer maxRetry;
    private Integer timeoutMs;
    private Integer rateLimitQps;
    private Integer inWhitelist;
}

