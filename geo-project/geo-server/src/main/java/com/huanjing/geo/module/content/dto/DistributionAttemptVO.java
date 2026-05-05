package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DistributionAttemptVO {
    private Long id;
    private Long siteId;
    private String siteName;
    private String domain;
    private String tier;
    private Integer attemptNo;
    private String status;
    private String integrationMethod;
    private String publishedUrl;
    private String errorMessage;
    private String requestPayload;
    private String responsePayload;
    private String platformArticleId;
    private String externalStatus;
    private String reviewStatus;
    private String reviewFeedback;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
