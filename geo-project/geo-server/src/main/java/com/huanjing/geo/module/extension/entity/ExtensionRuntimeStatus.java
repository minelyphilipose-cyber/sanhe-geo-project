package com.huanjing.geo.module.extension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("extension_runtime_status")
public class ExtensionRuntimeStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String installId;
    private Long extensionSessionId;
    private Long browserEnvironmentId;
    private Long browserEnvironmentAccountId;
    private Long brandId;
    private String platform;
    private String environmentKey;
    private String providerProfileId;
    private String extensionVersion;
    private String protocolVersion;
    private String currentUrl;
    private String detectedPlatform;
    private String detectedAccountName;
    private String detectedPlatformAccountId;
    private String loginStatus;
    private String runtimeStage;
    private LocalDateTime runtimeStageAt;
    private String runtimeStageMessage;
    private String capabilitiesJson;
    private Long lastTaskId;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
