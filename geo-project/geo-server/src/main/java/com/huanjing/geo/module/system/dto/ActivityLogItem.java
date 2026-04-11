package com.huanjing.geo.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityLogItem {
    private Long id;
    private Long userId;
    private String operatorName;
    private String action;
    private String targetType;
    private Long targetId;
    private String detailJson;
    private String ipAddress;
    private LocalDateTime createdAt;
}
