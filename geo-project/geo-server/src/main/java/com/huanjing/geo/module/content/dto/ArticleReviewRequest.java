package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArticleReviewRequest {
    @NotBlank
    private String action;
    private String comment;
    private Boolean riskOverride;
}

