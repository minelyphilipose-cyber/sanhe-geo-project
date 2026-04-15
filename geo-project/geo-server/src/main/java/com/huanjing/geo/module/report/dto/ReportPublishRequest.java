package com.huanjing.geo.module.report.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportPublishRequest {
    private String sharePassword;
    private LocalDateTime shareExpiresAt;
}
