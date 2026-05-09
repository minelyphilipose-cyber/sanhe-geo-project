package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ManualArticleCreateRequest {
    @NotNull
    private Long projectId;
    @NotBlank
    private String articleType;
    @NotBlank
    @Size(max = 120)
    private String title;
    @NotBlank
    @Size(max = 50000)
    private String contentMarkdown;

    @Size(max = 16)
    private String source;

    private Map<String, Object> aiMetadata;
}
