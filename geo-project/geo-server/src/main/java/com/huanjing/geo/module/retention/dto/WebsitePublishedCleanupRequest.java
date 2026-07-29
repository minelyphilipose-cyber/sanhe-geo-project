package com.huanjing.geo.module.retention.dto;

import lombok.Data;

@Data
public class WebsitePublishedCleanupRequest {
    private Integer retentionHours;
    private Integer limit;
    private Long cursorArticleId;
    private String reason;
}
