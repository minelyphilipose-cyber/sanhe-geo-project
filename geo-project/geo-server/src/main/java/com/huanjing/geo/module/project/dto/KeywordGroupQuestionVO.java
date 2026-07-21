package com.huanjing.geo.module.project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class KeywordGroupQuestionVO {
    private Long id;
    private Long groupId;
    private String questionCode;
    private String questionText;
    private String sceneCode;
    private String questionTier;
    private String priority;
    private String monitorFrequency;
    private Boolean pollingEnabled;
    private BigDecimal scoreRelevance;
    private BigDecimal scoreIntent;
    private BigDecimal scoreCompetition;
    private BigDecimal scoreConversion;
    private BigDecimal scoreCoverage;
    private BigDecimal totalScore;
    private String articleGenerationNote;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
