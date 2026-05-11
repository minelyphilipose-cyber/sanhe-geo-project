package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KeywordLlmQuestionGenerateRequest {
    private Long companyId;

    private Long projectId;

    @NotBlank(message = "seedText is required")
    @Size(max = 10, message = "seedText length must be <= 10")
    private String seedText;

    @Size(max = 32, message = "currentToken length must be <= 32")
    private String currentToken;

    private Integer count;

    private Integer currentLlmCount;

    @Min(value = 5, message = "targetCount must be >= 5")
    @Max(value = 50, message = "targetCount must be <= 50")
    private Integer targetCount;
}
