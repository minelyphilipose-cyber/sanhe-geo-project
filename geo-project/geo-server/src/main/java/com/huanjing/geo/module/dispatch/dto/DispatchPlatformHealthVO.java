package com.huanjing.geo.module.dispatch.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispatchPlatformHealthVO {
    private Long id;
    private String platformCode;
    private String platformName;
    private String priorityLevel;
    private Boolean enabled;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private Boolean degraded;
    private String degradedReason;
    private String currentHealthStatus;
    private LocalDateTime lastFailureAt;
    private Long exceptionCount;
}

