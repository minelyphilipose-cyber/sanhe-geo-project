package com.huanjing.geo.module.content.dto;

import lombok.Data;

@Data
public class ArticleBodyPurgeRequest {
    private Long projectId;
    private Integer retentionDays;
    private Integer archiveGraceHours;
    private Long cursorVersionId;
    private Integer limit;
    private String reason;
}
