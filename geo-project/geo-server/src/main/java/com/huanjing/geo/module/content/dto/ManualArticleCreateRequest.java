package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ManualArticleCreateRequest {
    @NotNull
    private Long projectId;
    @NotBlank
    private String articleType;
    @Size(max = 64)
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$")
    private String contentStyle;
    @NotBlank
    @Size(max = 1000)
    private String topic;
    @Size(max = 1000)
    private String topicAsQuestion;
    @NotBlank
    @Size(max = 120)
    private String title;
    @NotBlank
    @Size(max = 50000)
    private String contentMarkdown;

    @Size(max = 16)
    private String source;

    private Long coverMaterialId;

    private Long headImageMaterialId;

    private Map<String, Object> aiMetadata;
}
