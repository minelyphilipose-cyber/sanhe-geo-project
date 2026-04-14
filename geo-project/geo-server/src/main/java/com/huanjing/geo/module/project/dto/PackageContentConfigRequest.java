package com.huanjing.geo.module.project.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PackageContentConfigRequest {
    @NotBlank
    private String articleType;
    @NotNull
    @Min(1)
    private Integer articlesPerBatch;
    @NotNull
    @Min(1)
    private Integer questionsPerArticle;
    @NotNull
    private Boolean isActive;
}
