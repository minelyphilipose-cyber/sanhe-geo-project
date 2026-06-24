package com.huanjing.geo.module.mobiledashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileDashboardShareAccessSummaryVO {
    private Long shareId;
    private Long totalAccess;
    private Long successAccess;
    private Long failedAccess;
    private Long distinctIpCount;
    private LocalDateTime lastAccessAt;
    private String latestFailReason;
    private String latestUserAgent;
}
