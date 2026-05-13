package com.huanjing.geo.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_platform_config")
public class AiPlatformConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    private String platformName;
    private String priorityLevel;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private String apiKey;
    private String primaryKeyRef;
    private String backupKeyRef;
    private String backupProviderName;
    private String backupApiUrl;
    private String backupModelId;
    private String apiUrl;
    private String modelId;
    private String lowModelId;
    private String modelName;
    private Integer concurrencyLimit;
    private Boolean enabled;
    private Boolean enabledForPresale;
    private Boolean presaleEvaluateEnabled;
    private Boolean enabledForArticle;
    private Integer maxRetry;
    private Integer timeoutMs;
    private Integer rateLimitQps;
    private Boolean degraded;
    private String degradedReason;
    private String currentHealthStatus;
    private LocalDateTime lastFailureAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
