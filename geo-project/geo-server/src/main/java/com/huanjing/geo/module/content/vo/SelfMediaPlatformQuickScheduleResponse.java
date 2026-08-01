package com.huanjing.geo.module.content.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SelfMediaPlatformQuickScheduleResponse {
    private String action;
    private String code;
    private String message;
    private Long articleId;
    private Long brandId;
    private String platform;
    private String platformLabel;
    private Long selfMediaAccountId;
    private String topicRegionText;
    private String topicIndustryText;
    private String topicQuery;
    private List<Long> imageMaterialIds;
    private Integer expectedImageCount;
    private Long replaceScheduleId;
    private LocalDateTime plannedPublishAt;
    private LocalDateTime nextAttemptAt;
    private Integer brandSafetyIntervalMinutes;
    private SelfMediaPublishScheduleCreateResponse createResponse;
}
