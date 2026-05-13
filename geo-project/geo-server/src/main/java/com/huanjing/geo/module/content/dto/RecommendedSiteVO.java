package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RecommendedSiteVO {
    private Long siteId;
    private String siteName;
    private String domain;
    private String iconUrl;
    private String tier;
    private String status;
    private String integrationMethod;
    private String currentHealthStatus;
    private BigDecimal failureRate;
    private BigDecimal successRate30d;
    private String matchType;
    private List<String> industryTags;
    private String contentConstraints;
}
