package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArticleTemplatePreviewRequest {

    @NotNull
    private Long projectId;

    @NotBlank
    @Size(max = 32)
    private String articleType;

    @NotBlank
    @Size(max = 64)
    private String channelGroupCode;

    @Size(max = 64)
    private String channelSubCode;

    @NotBlank
    @Size(max = 1000)
    private String topic;

    @Size(max = 1000)
    private String topicAsQuestion;

    @Size(max = 32)
    @Pattern(regexp = "short|medium|long")
    private String length;

    private Long keywordGroupId;

    @Size(max = 3000)
    private String extraPrompt;

    private Long promptTemplateId;

    @NotNull
    private Long promptTemplateVersionId;

    @Size(max = 64)
    private String modelPlatformCode;

    @Size(max = 128)
    private String modelId;
}
