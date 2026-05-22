package com.huanjing.geo.module.content.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchArticleGenerateRequest {

    @NotNull
    private Long projectId;

    @Size(max = 32)
    @Pattern(regexp = "keyword_group|manual")
    private String topicSource;

    @NotEmpty
    @Size(max = 30)
    @Valid
    private List<TopicConfig> topics;

    @Data
    public static class TopicConfig {
        private Long keywordGroupId;

        @Size(max = 255)
        private String keywordGroupName;

        @NotBlank
        @Size(max = 1000)
        private String topic;

        @Size(max = 1000)
        private String topicAsQuestion;

        @Size(max = 32)
        @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$")
        private String questionSceneCode;

        private Boolean readinessWarningConfirmed;

        @Size(max = 16)
        private List<@Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$") String> readinessWarningCodes;

        @NotEmpty
        @Size(max = 16)
        @Valid
        private List<PlatformCount> platforms;
    }

    @Data
    public static class PlatformCount {
        @Size(max = 64)
        private String channelGroupCode;

        @Size(max = 64)
        private String channelSubCode;

        @Size(max = 16)
        @Pattern(regexp = "auto|custom")
        private String allocationMode;

        @Size(max = 64)
        private String articleTypeCode;

        @Size(max = 32)
        private String agentSiteModule;

        private Long templateId;

        private Long templateVersionId;

        @Size(max = 64)
        @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$")
        private String contentStyle;

        @Min(0)
        @Max(30)
        private Integer count;

        @Size(max = 3000)
        private String extraPrompt;

        @Valid
        private List<TemplateCount> templateCounts;

        @Valid
        private List<TemplateCount> previewTemplateCounts;
    }

    @Data
    public static class TemplateCount {
        @NotNull
        private Long templateId;

        @NotNull
        private Long templateVersionId;

        @Min(0)
        @Max(30)
        private Integer count;

        @Size(max = 3000)
        private String extraPrompt;
    }
}
