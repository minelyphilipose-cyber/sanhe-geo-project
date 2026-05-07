package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArticleAiDraftRequest {

    @NotNull
    private Long projectId;

    @NotBlank
    @Size(max = 32)
    private String articleType;

    @NotBlank
    @Size(max = 8000)
    private String prompt;

    @Size(max = 64)
    private String modelPlatformCode;

    @Size(max = 128)
    private String modelId;
}
