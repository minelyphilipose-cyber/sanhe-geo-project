package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileDashboardShareVO {
    private Long id;
    private Long projectId;
    private String tokenPrefix;
    private String status;
    private LocalDateTime expiresAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
    private LocalDateTime lastAccessAt;
    private Long accessCount;
    private String shareUrl;
    private String token;
}
