package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuestionPoolItemRequest {
    @NotBlank
    private String questionText;
    @NotBlank
    private String questionType;
    @NotBlank
    private String priority;
    @NotNull
    private Boolean isCore;
}
