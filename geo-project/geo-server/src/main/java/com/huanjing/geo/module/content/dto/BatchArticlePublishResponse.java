package com.huanjing.geo.module.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BatchArticlePublishResponse {
    private Long jobId;
    private String jobName;
    private String jobSource;
    private String publishMode;
    private String status;
    private LocalDateTime scheduledAt;
    private Integer intervalMinutes;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long id;
        private Long articleId;
        private String articleTitle;
        private String projectName;
        private String platformKey;
        private String contentStyle;
        private Long targetSiteId;
        private String targetSiteName;
        private Integer targetForumFid;
        private Long targetBrandId;
        private LocalDateTime plannedAt;
        private LocalDateTime publishedAt;
        private String status;
        private Long distributionTaskId;
        private String errorMessage;
    }
}
