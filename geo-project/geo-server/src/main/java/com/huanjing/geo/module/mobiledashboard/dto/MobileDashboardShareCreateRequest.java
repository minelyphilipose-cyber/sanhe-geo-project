package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileDashboardShareCreateRequest {
    private LocalDateTime expiresAt;
}
