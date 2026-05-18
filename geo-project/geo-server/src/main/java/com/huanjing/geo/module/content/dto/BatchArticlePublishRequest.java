package com.huanjing.geo.module.content.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchArticlePublishRequest {
    @NotEmpty
    private List<Long> articleIds;

    @NotNull
    private String publishMode;

    private String scheduledAt;

    @Min(1)
    @Max(1440)
    private Integer intervalMinutes = 30;

    @Min(1)
    @Max(1)
    private Integer platformConcurrency = 1;

    private Long industrySiteId;

    private Long forumSiteId;
}
