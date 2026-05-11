package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KeywordGroupQuestionUpdateRequest {
    @NotBlank(message = "questionText is required")
    @Size(max = 500, message = "questionText length must be <= 500")
    private String questionText;

    @Size(max = 64, message = "sceneCode length must be <= 64")
    private String sceneCode;

    @Size(max = 64, message = "priority length must be <= 64")
    private String priority;

    @DecimalMin(value = "1", message = "scoreRelevance must be >= 1")
    @DecimalMax(value = "5", message = "scoreRelevance must be <= 5")
    private BigDecimal scoreRelevance;

    @DecimalMin(value = "1", message = "scoreIntent must be >= 1")
    @DecimalMax(value = "5", message = "scoreIntent must be <= 5")
    private BigDecimal scoreIntent;

    @DecimalMin(value = "1", message = "scoreCompetition must be >= 1")
    @DecimalMax(value = "5", message = "scoreCompetition must be <= 5")
    private BigDecimal scoreCompetition;

    @DecimalMin(value = "1", message = "scoreConversion must be >= 1")
    @DecimalMax(value = "5", message = "scoreConversion must be <= 5")
    private BigDecimal scoreConversion;

    @DecimalMin(value = "1", message = "scoreCoverage must be >= 1")
    @DecimalMax(value = "5", message = "scoreCoverage must be <= 5")
    private BigDecimal scoreCoverage;

    @Size(max = 1000, message = "articleGenerationNote length must be <= 1000")
    private String articleGenerationNote;
}
