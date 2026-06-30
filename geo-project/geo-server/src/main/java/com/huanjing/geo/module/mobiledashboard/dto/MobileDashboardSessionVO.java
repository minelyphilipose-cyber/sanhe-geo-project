package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MobileDashboardSessionVO {
    private String sessionToken;
    private LocalDateTime sessionExpiresAt;
    private Long sessionTtlSeconds;
    private Long shareId;
    private Long projectId;
    private String projectName;
    private String brandName;
    private List<MobileDashboardContentPlatformVO> contentPlatforms = new ArrayList<>();
}
