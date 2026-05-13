package com.huanjing.geo.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiPlatformConfigUpdateRequest {
    @NotBlank
    private String platformCode;
    @NotBlank
    private String platformName;
    @NotBlank
    private String priorityLevel;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private String apiKey;
    private String primaryKeyRef;
    private String backupKeyRef;
    private String backupProviderName;
    private String backupApiUrl;
    private String backupModelId;
    @NotBlank
    private String apiUrl;
    @NotBlank
    private String modelId;
    private String lowModelId;
    @NotBlank
    private String modelName;
    private Integer concurrencyLimit;
    @NotNull
    private Boolean enabled;
    private Boolean enabledForPresale;
    private Boolean presaleEvaluateEnabled;
    private Boolean enabledForArticle;
    private Integer maxRetry;
    private Integer timeoutMs;
    private Integer rateLimitQps;
    @NotNull
    private Boolean degraded;
    private String degradedReason;
    private String remark;
}
