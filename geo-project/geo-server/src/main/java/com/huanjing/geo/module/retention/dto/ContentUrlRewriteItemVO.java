package com.huanjing.geo.module.retention.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ContentUrlRewriteItemVO {
    private Long versionId;
    private Long articleId;
    private Integer versionNo;
    private String tableName;
    private String columnName;
    private Integer matchedUrlCount = 0;
    private Integer rewriteUrlCount = 0;
    private Integer orphanUrlCount = 0;
    private Boolean changed = false;
    private Boolean contentAlreadyArchived = false;
    private Boolean requiresRearchive = false;
    private String result;
    private String errorMessage;
    private List<ReplacementSample> replacements = new ArrayList<>();
    private List<String> orphanUrls = new ArrayList<>();

    @Data
    public static class ReplacementSample {
        private String objectKey;
        private String oldUrl;
        private String newUrl;
    }
}
