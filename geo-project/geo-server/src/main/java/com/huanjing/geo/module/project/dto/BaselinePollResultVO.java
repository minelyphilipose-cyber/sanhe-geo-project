package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselinePollResultVO {
    private Long id;
    private Long batchId;
    private Long keywordResultId;
    private String questionTier;
    private String questionText;
    private String platformCode;
    private String platformName;
    private String status;
    private Integer requestCount;
    private Long responseTimeMs;
    private String responseText;
    private String errorMessage;
    private LocalDateTime createdAt;
}
