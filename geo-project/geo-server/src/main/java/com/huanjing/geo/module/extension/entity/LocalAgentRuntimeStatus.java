package com.huanjing.geo.module.extension.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("local_agent_runtime_status")
public class LocalAgentRuntimeStatus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String machineId;
    private String activeProfile;
    private Long sessionId;
    private Long operatorId;
    private String helperVersion;
    private String protocolVersion;
    private String helperName;
    private Boolean adspowerApiOk;
    private String adspowerApiBase;
    private Integer runningTaskCount;
    private Integer capacity;
    private String supportedPlatformsJson;
    private String capabilitiesJson;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime lastSeenAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
