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

        @NotEmpty
        @Size(max = 9)
        @Valid
        private List<PlatformCount> platforms;
    }

    @Data
    public static class PlatformCount {
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "wechat|toutiao|douyin_image_text|zhihu|linkedin|industry_site|authority_media|forum|xiaohongshu")
        private String contentStyle;

        @Min(0)
        @Max(30)
        private Integer count;

        @Size(max = 3000)
        private String extraPrompt;
    }
}
