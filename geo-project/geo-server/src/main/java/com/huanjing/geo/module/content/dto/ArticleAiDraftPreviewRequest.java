package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArticleAiDraftPreviewRequest {

    @NotNull
    private Long projectId;

    @NotBlank
    @Size(max = 32)
    private String articleType;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "wechat|toutiao|douyin_image_text|zhihu|xiaohongshu|linkedin")
    private String contentStyle;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "professional|friendly|sharp|storytelling")
    private String tone;

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "short|medium|long")
    private String length;

    @NotBlank
    @Size(max = 1000)
    private String topic;

    @Size(max = 3000)
    private String extraPrompt;

    @Size(max = 3000)
    private String referenceMaterials;

    @Size(max = 64)
    private String modelPlatformCode;

    @Size(max = 128)
    private String modelId;
}
