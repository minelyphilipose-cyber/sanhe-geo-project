package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class QuestionStrategyUpdateRequest {
    @NotBlank
    private String contentStrategy;
    private List<String> strategyKeywords;
    @NotBlank
    private String strategySuggestedType;
}
