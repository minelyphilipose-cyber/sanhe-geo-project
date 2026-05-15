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
    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "wechat|toutiao|douyin_image_text|zhihu|xiaohongshu|linkedin|agent_site_article|industry_site")
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

    private Map<String, Object> aiMetadata;
}
