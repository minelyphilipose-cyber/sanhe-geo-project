package com.huanjing.geo.module.retention.dto;

import lombok.Data;

@Data
public class ContentUrlRewriteRequest {
    private Boolean dryRun = true;
    private Integer limit = 100;
    private Long articleId;
    private Long versionId;
}
